package com.monstro.v18

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.*
import androidx.media3.transformer.Composition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// Milliseconds throughout; ranges must always contain at least one millisecond.
data class TrimRange(val start: Long, val end: Long) {
    init { require(start >= 0 && end > start) }
    val duration: Long get() = end - start
    fun split(offset: Long): Pair<TrimRange, TrimRange>? {
        if (offset <= 0 || offset >= duration) return null
        return TrimRange(start, start + offset) to TrimRange(start + offset, end)
    }
}

data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    val duration: Long,
    val trim: TrimRange = TrimRange(0, duration),
    val preset: String = "raw",
    val chaos: ChaosSettings = ChaosSettings()
)

@UnstableApi
fun videoEffects(preset: String): List<Effect> = when (preset) {
    "neon" -> listOf(Contrast(0.25f), RgbAdjustment.Builder().setRedScale(1.1f).setBlueScale(1.2f).build())
    "trap" -> listOf(Contrast(0.15f), RgbAdjustment.Builder().setRedScale(1.15f).setGreenScale(0.9f).build())
    "dark" -> listOf(Brightness(-0.15f), Contrast(0.2f))
    "cinema" -> listOf(Contrast(0.05f), HslAdjustment.Builder().adjustSaturation(10f).build())
    else -> emptyList()
}

// Shared by preview, export and the on-device integration test.
@UnstableApi
fun buildClipEffects(clip: VideoClip, safeMode: Boolean): List<Effect> = listOf(
    FrameDropEffect.createDefaultFrameDropEffect(if (safeMode) 30f else 60f),
    Presentation.createForHeight(if (safeMode) 480 else 720)
) + videoEffects(clip.preset) + if (clip.chaos.isIdentity) emptyList() else listOf(ChaosEffect(clip.chaos))

@UnstableApi
fun VideoClip.mediaItem(): MediaItem = MediaItem.Builder().setUri(uri)
    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(trim.start).setEndPositionMs(trim.end).build())
    .build()

@UnstableApi
class EditorModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("editor", 0)
    var clips by mutableStateOf<List<VideoClip>>(emptyList()); private set
    var selected by mutableStateOf(0); private set
    var mute by mutableStateOf(false); private set
    var safeMode by mutableStateOf(true); private set
    var importing by mutableStateOf(false); private set
    var exporting by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var progress by mutableStateOf<Int?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var output by mutableStateOf<File?>(null); private set
    val busy get() = importing || exporting || saving
    val current get() = clips.getOrNull(selected)
    val player = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                message = "Não foi possível abrir este vídeo: ${error.errorCodeName}. Tente outro arquivo."
            }
        })
    }
    private var transformer: Transformer? = null
    private var renderingFile: File? = null
    private var polling: Job? = null

    init {
        runCatching {
            val data = JSONArray(prefs.getString("clips", "[]"))
            clips = (0 until data.length()).map { i ->
                val c = data.getJSONObject(i)
                VideoClip(c.getString("id"), c.getString("uri"), c.getString("name"),
                    c.getLong("duration"), TrimRange(c.getLong("start"), c.getLong("end")), c.getString("preset"),
                    ChaosSettings.restore(c.optJSONArray("fx")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                        c.optDouble("zoom", 1.0).toFloat()))
            }
            mute = prefs.getBoolean("mute", false)
            safeMode = prefs.getBoolean("safeMode", true)
            output = prefs.getString("output", null)?.let { File(it) }?.takeIf { it.isFile && it.length() > 0 }
            preview()
        }.onFailure { clips = emptyList(); message = "Não foi possível restaurar o projeto. Importe os vídeos novamente." }
    }

    private fun persist() {
        val data = JSONArray()
        clips.forEach { c -> data.put(JSONObject().put("id", c.id).put("uri", c.uri)
            .put("name", c.name).put("duration", c.duration).put("start", c.trim.start)
            .put("end", c.trim.end).put("preset", c.preset)
            .put("fx", JSONArray(c.chaos.enabled.toList())).put("zoom", c.chaos.zoom.toDouble())) }
        prefs.edit().putString("clips", data.toString()).putBoolean("mute", mute).putBoolean("safeMode", safeMode).apply()
    }

    private fun preview(position: Long = 0, resume: Boolean = false) {
        player.stop()
        player.clearMediaItems()
        current?.let {
            player.setVideoEffects(buildClipEffects(it, safeMode))
            player.volume = if (mute) 0f else 1f
            player.setMediaItem(it.mediaItem(), position.coerceIn(0, it.trim.duration - 1))
            player.prepare()
            player.playWhenReady = resume
        }
    }

    fun select(index: Int) {
        if (busy || index !in clips.indices) return
        selected = index
        preview()
    }

    fun importVideos(uris: List<Uri>) {
        if (busy || uris.isEmpty()) return
        importing = true
        viewModelScope.launch {
            var failed = 0
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        val retriever = MediaMetadataRetriever()
                        val duration = try {
                            retriever.setDataSource(context, uri)
                            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes")
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                                ?.takeIf { it > 0 } ?: error("Duração inválida")
                        } finally { retriever.release() }
                        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Vídeo"
                        VideoClip(uri = uri.toString(), name = name, duration = duration)
                    }.getOrElse { failed++; null }
                }
            }
            val firstNewIndex = clips.size
            clips = clips + added
            if (added.isNotEmpty()) { selected = firstNewIndex; persist(); preview() }
            importing = false
            if (failed > 0) message = "$failed arquivo(s) não puderam ser importados. Use vídeos locais acessíveis."
        }
    }

    fun toggleSafeMode() {
        if (busy) return
        val position = player.currentPosition
        val playing = player.playWhenReady
        safeMode = !safeMode
        persist(); preview(position, playing)
    }

    fun toggleFx(fx: ChaosFx) {
        val clip = current ?: return
        edit(chaos = clip.chaos.toggle(fx))
    }

    fun edit(trim: TrimRange? = null, preset: String? = null, chaos: ChaosSettings? = null) {
        if (busy) return
        val old = current ?: return
        if (trim != null && trim.end > old.duration) return
        val position = if (trim == null) player.currentPosition else 0L
        val playing = player.playWhenReady
        clips = clips.toMutableList().also { it[selected] = old.copy(trim = trim ?: old.trim, preset = preset ?: old.preset, chaos = chaos ?: old.chaos) }
        persist(); preview(position, playing)
    }

    fun move(delta: Int) {
        val target = selected + delta
        if (busy || target !in clips.indices) return
        clips = clips.toMutableList().also { val c = it.removeAt(selected); it.add(target, c) }
        selected = target
        persist()
    }

    fun remove() {
        if (busy || current == null) return
        clips = clips.toMutableList().also { it.removeAt(selected) }
        selected = selected.coerceAtMost((clips.size - 1).coerceAtLeast(0))
        persist(); preview()
    }

    fun split() {
        if (busy) return
        val clip = current ?: return
        val ranges = clip.trim.split(player.currentPosition) ?: run {
            message = "Pause no ponto de corte, entre o início e o fim do clipe."; return
        }
        clips = clips.toMutableList().also {
            it[selected] = clip.copy(trim = ranges.first)
            it.add(selected + 1, clip.copy(id = UUID.randomUUID().toString(), trim = ranges.second))
        }
        persist(); preview()
    }

    fun toggleMute() {
        if (busy) return
        mute = !mute
        player.volume = if (mute) 0f else 1f
        persist()
    }

    fun clearMessage() { message = null }

    fun export() {
        if (busy || clips.isEmpty()) return
        player.pause()
        exporting = true
        progress = null
        val file = File(context.filesDir, "monstro-${System.currentTimeMillis()}.mp4")
        renderingFile = file
        try {
            val items = clips.map { clip ->
                EditedMediaItem.Builder(clip.mediaItem()).setRemoveAudio(mute)
                    .setEffects(Effects(emptyList(), buildClipEffects(clip, safeMode) +
                        Presentation.createForWidthAndHeight(if (safeMode) 854 else 1280, if (safeMode) 480 else 720, Presentation.LAYOUT_SCALE_TO_FIT)))
                    .build()
            }
            val composition = Composition.Builder(EditedMediaItemSequence(items))
                .experimentalSetForceAudioTrack(!mute)
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .build()
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder()
                    .setBitrate(if (safeMode) 2_500_000 else 5_000_000).build())
                .setEnableFallback(true).build()
            val job = Transformer.Builder(context)
                .setEncoderFactory(encoderFactory)
                .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        polling?.cancel()
                        transformer = null
                        renderingFile = null
                        exporting = false
                        if (!file.isFile || file.length() == 0L) {
                            file.delete(); message = "A exportação terminou sem gerar um arquivo válido."; return
                        }
                        val previous = output
                        output = file
                        prefs.edit().putString("output", file.absolutePath).apply()
                        previous?.takeIf { it != file }?.delete()
                        message = "MP4 pronto! Toque em Salvar MP4 para escolher onde guardar."
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        failExport("Falha na exportação: ${exportException.errorCodeName}. Tente clipes menores ou outro vídeo.")
                    }
                }).build()
            transformer = job
            job.start(composition, file.absolutePath)
            polling = viewModelScope.launch {
                val holder = ProgressHolder()
                while (exporting) {
                    progress = if (job.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null
                    delay(300)
                }
            }
        } catch (e: Exception) {
            failExport("Não foi possível exportar: ${e.localizedMessage ?: "erro de processamento"}")
        }
    }

    private fun failExport(reason: String) {
        transformer?.cancel(); transformer = null
        polling?.cancel()
        renderingFile?.delete(); renderingFile = null
        exporting = false; progress = null; message = reason
    }

    fun cancelExport() { if (exporting) failExport("Exportação cancelada. Seu projeto foi mantido.") }

    fun saveOutput(uri: Uri?) {
        if (uri == null || busy) return
        val file = output ?: return
        saving = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Destino indisponível")
                    stream.use { destination -> file.inputStream().use { it.copyTo(destination) } }
                }
            }
            saving = false
            message = if (result.isSuccess) "Vídeo salvo no local escolhido!" else "Não foi possível salvar. O MP4 continua disponível; tente outro local."
        }
    }

    override fun onCleared() {
        polling?.cancel(); transformer?.cancel(); renderingFile?.delete(); player.release()
        super.onCleared()
    }
}
