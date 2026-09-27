package com.monstro.v18

import android.media.MediaMetadataRetriever
import android.graphics.Color
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.*
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
class LyricsExportTest {
    @Test fun exportLyricsBlurAnd1080pWithAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val input = File(context.cacheDir,"lyrics-source.mp4")
        instrumentation.context.assets.open("sample.mp4").use { s -> input.outputStream().use { s.copyTo(it) } }
        val track = SrtParser.parse("1\n00:00:00,000 --> 00:00:00,700\nTRAP PALAVRA\n\n2\n00:00:01,100 --> 00:00:01,600\nFINAL NEON").track
        try {
            for (light in listOf(true,false)) {
                val format = ExportFormat(true,light)
                val output = File(context.cacheDir,"lyrics-$light.mp4").also { it.delete() }
                val done = CountDownLatch(1)
                val failure = AtomicReference<Throwable?>()
                var transformer: Transformer? = null
                instrumentation.runOnMainSync {
                    try {
                        // Two cuts: overlay timing must follow the complete output timeline.
                        val clips = listOf(TrimRange(200,1000),TrimRange(1000,1800)).mapIndexed { i,trim ->
                            val clip = VideoClip(uri=input.toURI().toString(),name="lyrics",duration=2000,trim=trim,preset=if(i==0) "neon" else "cinema")
                            EditedMediaItem.Builder(clip.mediaItem()).setEffects(Effects(emptyList(),videoEffects(clip.preset)+AspectBackgroundEffect(format))).build()
                        }
                        val composition = Composition.Builder(EditedMediaItemSequence(clips))
                            .setEffects(Effects(emptyList(),listOf(OverlayEffect(listOf(LyricsOverlay(track,!light,false))))))
                            .experimentalSetForceAudioTrack(true)
                            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
                        transformer = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(c: Composition,r: ExportResult) { done.countDown() }
                                override fun onError(c: Composition,r: ExportResult,e: ExportException) { failure.set(e); done.countDown() }
                            }).build()
                        transformer!!.start(composition,output.absolutePath)
                    } catch(e: Throwable) { failure.set(e); done.countDown() }
                }
                try {
                    assertTrue("Lyrics export timed out",done.await(150,TimeUnit.SECONDS))
                    failure.get()?.let { throw AssertionError("Lyrics export failed",it) }
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(output.absolutePath)
                        assertEquals(format.width.toString(),retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                        assertEquals(format.height.toString(),retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                        assertEquals("yes",retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                        fun redPixels(time: Long): Int {
                            val frame = retriever.getFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST)!!
                            var red = 0
                            for(y in (frame.height*.66).toInt() until (frame.height*.80).toInt() step 2)
                                for(x in frame.width/10 until frame.width*9/10 step 2) {
                                    val color = frame.getPixel(x,y)
                                    if(Color.red(color)>180 && Color.red(color)>Color.green(color)*1.8 && Color.red(color)>Color.blue(color)*1.4) red++
                                }
                            frame.recycle(); return red
                        }
                        val early = redPixels(450000); val gap = redPixels(900000); val late = redPixels(1350000)
                        assertTrue("First phrase missing: $early / $gap",early > gap+30)
                        assertTrue("Second phrase must use project time: $late / $gap",late > gap+30)
                        val frame = retriever.getFrameAtTime(900000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                        val background = (1..8).map { frame.getPixel(frame.width*it/10,frame.height/8) }
                        assertTrue("Blur background must fill letterbox",background.any { Color.red(it)+Color.green(it)+Color.blue(it)>30 })
                        assertTrue("Background must retain image variation",background.toSet().size>2)
                        frame.recycle()
                    } finally { retriever.release() }
                } finally { instrumentation.runOnMainSync { transformer?.cancel() }; output.delete() }
            }
        } finally { input.delete() }
    }
}
