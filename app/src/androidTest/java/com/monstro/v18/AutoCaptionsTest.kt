package com.monstro.v18
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.media.MediaMetadataRetriever

@RunWith(AndroidJUnit4::class)
class AutoCaptionsTest {
 @Test(timeout=240000) fun recognizesFileAudioWithWordTimes()=runBlocking {
    val i=InstrumentationRegistry.getInstrumentation();val context=i.targetContext;val speech=AutoCaptions(context)
    speech.install {};assertTrue(speech.ready)
    val file=File(context.cacheDir,"speech-fixture.mp4");i.context.assets.open("voice.mp4").use {s->file.outputStream().use {s.copyTo(it)}}
    val r=MediaMetadataRetriever();val duration=try{r.setDataSource(file.absolutePath);r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()}finally{r.release()}
    val result=speech.transcribe(listOf(VideoClip(uri=file.toURI().toString(),name="Speech",duration=duration)),StudioProject()){}
    assertTrue(result.cues.isNotEmpty());assertTrue(result.cues.all {it.wordTimes.size==it.words.size && it.wordTimes.all {w->w.end>w.start}})
    assertTrue(result.cues.flatMap {it.words}.size>=3)
 }
}
