package com.monstro.v18
import org.junit.Assert.*
import org.junit.Test

class StudioTest {
    @Test fun frameLimitPreservesSlowMotionTail(){val fast=FrameRateGate();assertEquals(60,(0L until 120L).count {fast.accept(it*1000000/60)});val slow=FrameRateGate();assertTrue((0L until 30L).all {slow.accept(it*1000000/15)});assertFalse(slow.accept(0))}

    @Test fun libraryHasDistinctRecipesAndSearch(){assertEquals(1000,FxCatalog.all.size);assertEquals(1000,FxCatalog.all.map {it.signature}.toSet().size);assertEquals(1000,FxCatalog.all.map {it.id}.toSet().size);assertEquals(20,FxCatalog.all.map {it.engine}.toSet().size);assertTrue(FxCatalog.search("impact","Shake").isNotEmpty());assertTrue(FxCatalog.search("not-an-effect").isEmpty())}
    @Test fun speedMapRoundTripsAndChangesDuration(){val normal=SpeedMap(10000,emptyList());assertEquals(10000,normal.outputDuration);val fast=SpeedMap(10000,listOf(KeyPoint(0,2f)));assertEquals(5000,fast.outputDuration);val curve=SpeedMap(6000,listOf(KeyPoint(0,.25f),KeyPoint(3000,4f),KeyPoint(6000,.5f)));for(t in 0L..6000L step 37){assertTrue(kotlin.math.abs(t-curve.toSource(curve.toOutput(t)))<5)};assertTrue(curve.slices.size<=120)}
    @Test fun keyframesClampAndReplace(){val keys=listOf(KeyPoint(100,1f),KeyPoint(300,3f));assertEquals(1f,animated(keys,0,0f));assertEquals(2f,animated(keys,200,0f));assertEquals(3f,animated(keys,999,0f));assertEquals(2,putKey(keys,110,5f).size)}
    @Test fun realWordTimesKeepSilences(){val cue=SrtCue(0,2000,"ola mundo",listOf(WordTime(100,500),WordTime(1200,1800)));assertEquals(-1,cue.wordAt(0));assertEquals(0,cue.wordAt(200));assertEquals(-1,cue.wordAt(900));assertEquals(1,cue.wordAt(1400));assertEquals(1200,cue.wordStart(1))}
    @Test fun exportRatiosAndBitratesAreStable(){
        val vertical=ExportFormat("9:16",false,"blur","recommended","H264")
        assertEquals(1080,vertical.width);assertEquals(1920,vertical.height);assertEquals(10_000_000,vertical.bitrate)
        val square=ExportFormat("1:1",true,"solid","low","H264")
        assertEquals(540,square.width);assertEquals(540,square.height);assertEquals(1_800_000,square.bitrate)
        val portrait=ExportFormat("4:5",false,"pattern","high","HEVC")
        assertEquals(1080,portrait.width);assertEquals(1350,portrait.height);assertEquals(16_000_000,portrait.bitrate)
    }

    @Test fun studioCodecPreservesNewEditingState(){
        val motion=ClipMotion(
            speed=listOf(KeyPoint(0,1f),KeyPoint(1000,2f)),
            zoom=listOf(KeyPoint(0,1.2f)),
            rotation=listOf(KeyPoint(300,12f)),
            x=listOf(KeyPoint(400,.2f)),
            y=listOf(KeyPoint(500,-.1f))
        )
        val audio=AudioLayer(uri="file:///voice.wav",name="voz",duration=4000,start=250,trimStart=10,trimEnd=3500,volume=.7f,pitch=1.4f,fadeIn=300,fadeOut=500)
        val adjust=ClipAdjust(.1f,.2f,20f,5f,-10f,.3f)
        val image=ImageLayer(path="/tmp/test.png",name="logo",start=100,end=2100,x=.4f,y=.6f,scale=.5f,rotation=12f,opacity=.8f,xKeys=listOf(KeyPoint(0,.3f),KeyPoint(1000,.7f)))
        val marker=TimelineMarker(time=777,label="Beat")
        val source=StudioProject(audio=listOf(audio),motions=mapOf("clip" to motion),adjustments=mapOf("clip" to adjust),markers=listOf(marker),images=listOf(image))
        val restored=StudioCodec.decode(StudioCodec.encode(source))
        assertEquals(audio,restored.audio.single())
        assertEquals(motion,restored.motions["clip"])
        assertEquals(adjust,restored.adjustments["clip"])
        assertEquals(marker,restored.markers.single())
        assertEquals(image,restored.images.single())
    }
}
