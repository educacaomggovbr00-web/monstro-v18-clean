package com.monstro.v18

import android.media.MediaMetadataRetriever
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
@RunWith(AndroidJUnit4::class)
class ChaosExportTest {
    @Test fun exportTrimmedSequenceWithAllEffectsAndAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val input = File(context.cacheDir, "sample.mp4")
        instrumentation.context.assets.open("sample.mp4").use { source -> input.outputStream().use { source.copyTo(it) } }
        val output = File(context.cacheDir, "fx-test.mp4").also { it.delete() }
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        var transformer: Transformer? = null
        instrumentation.runOnMainSync {
            try {
                val first = VideoClip(uri = input.toURI().toString(), name = "first", duration = 2000,
                    trim = TrimRange(200, 1000), preset = "cinema",
                    chaos = ChaosSettings(ChaosFx.values().map { it.id }.toSet(), 1.4f))
                val second = first.copy(id = "second", trim = TrimRange(1000, 1800), chaos = ChaosSettings(), preset = "raw")
                val sequence = listOf(first, second).map { clip ->
                    EditedMediaItem.Builder(clip.mediaItem()).setEffects(Effects(emptyList(), buildClipEffects(clip, true) +
                        Presentation.createForWidthAndHeight(854, 480, Presentation.LAYOUT_SCALE_TO_FIT))).build()
                }
                transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) { done.countDown() }
                        override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                            failure.set(exception); done.countDown()
                        }
                    }).build()
                transformer!!.start(Composition.Builder(EditedMediaItemSequence(sequence)).experimentalSetForceAudioTrack(true).build(), output.absolutePath)
            } catch (e: Throwable) { failure.set(e); done.countDown() }
        }
        try {
            assertTrue("Export timed out", done.await(120, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Real shader export failed", it) }
            assertTrue(output.length() > 1000)
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(output.absolutePath)
                assertEquals("yes", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Trimmed sequence duration: $duration", duration in 1450..1800)
                val frame = metadata.getFrameAtTime(400000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                assertNotNull(frame)
                val bitmap = frame!!
                val colors = (0 until 10).map { bitmap.getPixel(bitmap.width * (it + 1) / 12, bitmap.height / 2) }.toSet()
                assertTrue("Export must not be a black or solid frame", colors.size > 2)
                bitmap.recycle()
            } finally { metadata.release() }
        } finally {
            instrumentation.runOnMainSync { transformer?.cancel() }
            output.delete(); input.delete()
        }
    }
}
