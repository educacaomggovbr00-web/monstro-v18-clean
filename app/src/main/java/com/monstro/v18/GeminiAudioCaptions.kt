package com.monstro.v18

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * High-quality cloud captioning through Firebase AI Logic.
 *
 * Firebase AI Logic currently exposes general Gemini models, not the dedicated
 * gemini-3.5-transcribe endpoint. Gemini 3.8 Flash handles the audio here and
 * the local Vosk engine remains available as an offline fallback.
 */
class GeminiAudioCaptions(private val context: Context) {
    val configured: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    suspend fun transcribe(
        clips: List<VideoClip>,
        project: StudioProject,
        progress: (Int) -> Unit
    ): SrtTrack {
        require(configured) {
            "Firebase ainda não está conectado a este APK. Adicione app/google-services.json e ative AI Logic."
        }

        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                maxOutputTokens = 8192
            }
        )

        val cues = mutableListOf<SrtCue>()
        var timelineOffset = 0L

        clips.forEachIndexed { index, clip ->
            currentCoroutineContext().ensureActive()
            val base = index * 100 / clips.size
            val slice = max(1, 100 / clips.size)
            progress((base + slice * 5 / 100).coerceAtMost(99))

            val audioFile = extractAudio(clip)
            try {
                require(audioFile.length() in 1 until 19_000_000) {
                    "O áudio deste clipe é grande demais para o modo Gemini direto. Use um clipe menor ou o modo Offline."
                }

                progress((base + slice * 15 / 100).coerceAtMost(99))
                val prompt = content {
                    inlineData(audioFile.readBytes(), "audio/mp4")
                    text(
                        """
                        Transcreva todo o áudio falado exatamente no idioma original.
                        Retorne SOMENTE JSON válido neste formato:
                        {
                          "words": [
                            {"text":"palavra","start_ms":0,"end_ms":320}
                          ]
                        }

                        Regras:
                        - Um item por palavra falada, na ordem.
                        - start_ms e end_ms são relativos ao início deste arquivo de áudio.
                        - Preserve repetições reais.
                        - Não invente fala quando houver silêncio ou música.
                        - Pontuação pode ficar anexada à palavra anterior.
                        - Use números inteiros em milissegundos.
                        - O áudio tem aproximadamente ${clip.trim.duration} ms.
                        """.trimIndent()
                    )
                }

                progress((base + slice * 30 / 100).coerceAtMost(99))
                val response = model.generateContent(prompt).text
                    ?: error("Gemini não retornou uma transcrição.")
                progress((base + slice * 80 / 100).coerceAtMost(99))

                val rawWords = parseWords(response, clip.trim.duration)
                require(rawWords.isNotEmpty()) {
                    "Gemini não encontrou fala reconhecível neste clipe."
                }

                val speed = SpeedMap(
                    clip.trim.duration,
                    project.motions[clip.id]?.speed ?: emptyList()
                )
                rawWords.chunked(6).forEach { group ->
                    val timed = group.map { word ->
                        WordTime(
                            timelineOffset + speed.toOutput(word.start),
                            timelineOffset + speed.toOutput(word.end)
                        )
                    }
                    val start = timed.first().start
                    val end = max(start + 1, timed.last().end)
                    cues += SrtCue(
                        startMs = start,
                        endMs = end,
                        text = group.joinToString(" ") { it.text },
                        wordTimes = timed
                    )
                }
                timelineOffset += speed.outputDuration
                progress((base + slice).coerceAtMost(99))
            } finally {
                audioFile.delete()
            }
        }

        require(cues.isNotEmpty()) {
            "Não encontrei fala reconhecível. Tente outro áudio ou use o modo Offline."
        }
        progress(100)
        return SrtTrack(cues.sortedBy { it.startMs })
    }

    private data class AiWord(val text: String, val start: Long, val end: Long)

    private fun parseWords(raw: String, duration: Long): List<AiWord> {
        val clean = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        val root = JSONTokener(clean).nextValue()
        val array = when (root) {
            is JSONObject -> root.optJSONArray("words") ?: JSONArray()
            is JSONArray -> root
            else -> JSONArray()
        }

        val result = mutableListOf<AiWord>()
        var previousEnd = 0L
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val token = item.optString("text").trim()
            if (token.isBlank()) continue

            var start = item.optLong("start_ms", previousEnd)
                .coerceIn(0L, duration)
            var end = item.optLong("end_ms", start + 1)
                .coerceIn(0L, duration)

            start = max(start, previousEnd.coerceAtMost(duration))
            if (end <= start) end = (start + 1).coerceAtMost(duration)
            if (end <= start) continue

            val pieces = token.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (pieces.size <= 1) {
                result += AiWord(token, start, end)
            } else {
                val span = max(1L, end - start)
                pieces.forEachIndexed { index, piece ->
                    val partStart = start + span * index / pieces.size
                    val partEnd = start + span * (index + 1) / pieces.size
                    result += AiWord(piece, partStart, max(partStart + 1, partEnd))
                }
            }
            previousEnd = end
        }
        return result
    }

    private suspend fun extractAudio(clip: VideoClip): File {
        val output = File.createTempFile("monstro-gemini-", ".m4a", context.cacheDir)
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false

        try {
            extractor.setDataSource(context, Uri.parse(clip.uri), null)
            val sourceTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("Este clipe não possui uma faixa de áudio.")

            extractor.selectTrack(sourceTrack)
            val format = extractor.getTrackFormat(sourceTrack)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val targetTrack = muxer.addTrack(format)
            muxer.start()
            started = true

            val startUs = clip.trim.start * 1000L
            val endUs = clip.trim.end * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            while (extractor.sampleTime in 0 until startUs) extractor.advance()

            val maxInput = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256 * 1024)
            } else 1024 * 1024
            val buffer = ByteBuffer.allocate(maxInput)
            val info = MediaCodec.BufferInfo()
            var wrote = false

            while (true) {
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0 || sampleTime >= endUs) break
                currentCoroutineContext().ensureActive()

                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break

                info.offset = 0
                info.size = size
                info.presentationTimeUs = (sampleTime - startUs).coerceAtLeast(0)
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(targetTrack, buffer, info)
                wrote = true
                extractor.advance()
            }
            require(wrote) { "Não foi possível extrair o áudio deste trecho." }
        } finally {
            extractor.release()
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
        return output
    }
}
