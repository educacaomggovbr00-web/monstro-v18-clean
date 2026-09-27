package com.monstro.v18

import android.app.Application
import android.media.MediaMetadataRetriever
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@UnstableApi @RunWith(AndroidJUnit4::class)
class StudioExportTest {
 @Test fun speedAudioFxAndTextExportTogether(){
    val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
    val input=File(context.cacheDir,"studio-input.mp4");instrumentation.context.assets.open("sample.mp4").use {s->input.outputStream().use {s.copyTo(it)}}
    val clips=JSONArray();for(i in 0..1)clips.put(JSONObject().put("id","test-$i").put("uri",input.toURI().toString()).put("name","Clip $i").put("duration",2000).put("start",i*1000).put("end",(i+1)*1000).put("preset","raw"))
    val project=StudioProject(texts=listOf(TextLayer(text="STUDIO",start=0,end=2500)),fx=listOf(FxLayer(presetId="fx-5-2-3",start=0,end=2500)),audio=listOf(AudioLayer(uri=input.toURI().toString(),name="Music",duration=2000,start=300,volume=.3f)),motions=mapOf("test-0" to ClipMotion(speed=listOf(KeyPoint(0,2f))),"test-1" to ClipMotion(speed=listOf(KeyPoint(0,.5f)))))
    val prefs=context.getSharedPreferences("editor",0);val previous=prefs.getString("clips","[]");val oldStudio=prefs.getString("studio","{}")
    prefs.edit().putString("clips",clips.toString()).putString("studio",StudioCodec.encode(project)).putBoolean("compatibilityPreview",false).putBoolean("safeMode",true).apply()
    val done=CountDownLatch(1);val failure=AtomicReference<Throwable?>();val output=File(context.cacheDir,"studio-export.mp4").also {it.delete()};var model:EditorModel?=null;var transformer:Transformer?=null
    try {
      instrumentation.runOnMainSync {try{val m=EditorModel(context.applicationContext as Application);model=m;assertEquals(2500L,m.totalDuration)
        transformer=Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).addListener(object:Transformer.Listener {
          override fun onCompleted(c:Composition,r:ExportResult){done.countDown()}
          override fun onError(c:Composition,r:ExportResult,e:ExportException){failure.set(e);done.countDown()}
        }).build();transformer!!.start(m.studioComposition(),output.absolutePath)
      }catch(e:Throwable){failure.set(e);done.countDown()}}
      assertTrue("Studio export timeout",done.await(160,TimeUnit.SECONDS));failure.get()?.let {throw AssertionError("Studio export failed",it)}
      val r=MediaMetadataRetriever();try{r.setDataSource(output.absolutePath);val duration=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong();assertTrue("Speed duration expected 2500ms, got $duration",duration in 2350..2700);assertEquals("yes",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));for(t in listOf(250000L,900000L,2100000L)){val b=r.getFrameAtTime(t,MediaMetadataRetriever.OPTION_CLOSEST)!!;val colors=(1..8).map {b.getPixel(b.width*it/10,b.height/2)};assertTrue(colors.toSet().size>2);b.recycle()}}finally{r.release()}
      val extractor=android.media.MediaExtractor()
      try {
        extractor.setDataSource(output.absolutePath)
        val ends=mutableListOf<Long>()
        for(track in 0 until extractor.trackCount){extractor.selectTrack(track);extractor.seekTo(0,android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC);var last=0L;while(extractor.sampleTime>=0){last=extractor.sampleTime;if(!extractor.advance())break};extractor.unselectTrack(track);ends+=last}
        assertEquals(2,ends.size)
        assertTrue("Audio/video must both reach the slowed final clip: $ends",ends.all {it in 2350000L..2700000L})
        assertTrue("Audio/video drift: $ends",kotlin.math.abs(ends[0]-ends[1])<150000L)
      } finally {extractor.release()}
    }finally{instrumentation.runOnMainSync {transformer?.cancel();model?.player?.release()};prefs.edit().putString("clips",previous).putString("studio",oldStudio).apply();output.delete()}
 }
}
