package com.monstro.v18
import org.junit.Assert.*
import org.junit.Test

class StudioTest {
    @Test fun frameLimitPreservesSlowMotionTail(){val fast=FrameRateGate();assertEquals(60,(0L until 120L).count {fast.accept(it*1000000/60)});val slow=FrameRateGate();assertTrue((0L until 30L).all {slow.accept(it*1000000/15)});assertFalse(slow.accept(0))}

    @Test fun libraryHasDistinctRecipesAndSearch(){assertEquals(1000,FxCatalog.all.size);assertEquals(1000,FxCatalog.all.map {it.signature}.toSet().size);assertEquals(1000,FxCatalog.all.map {it.id}.toSet().size);assertEquals(20,FxCatalog.all.map {it.engine}.toSet().size);assertTrue(FxCatalog.search("impact","Shake").isNotEmpty());assertTrue(FxCatalog.search("not-an-effect").isEmpty())}
    @Test fun speedMapRoundTripsAndChangesDuration(){val normal=SpeedMap(10000,emptyList());assertEquals(10000,normal.outputDuration);val fast=SpeedMap(10000,listOf(KeyPoint(0,2f)));assertEquals(5000,fast.outputDuration);val curve=SpeedMap(6000,listOf(KeyPoint(0,.25f),KeyPoint(3000,4f),KeyPoint(6000,.5f)));for(t in 0L..6000L step 37){assertTrue(kotlin.math.abs(t-curve.toSource(curve.toOutput(t)))<5)};assertTrue(curve.slices.size<=120)}
    @Test fun keyframesClampAndReplace(){val keys=listOf(KeyPoint(100,1f),KeyPoint(300,3f));assertEquals(1f,animated(keys,0,0f));assertEquals(2f,animated(keys,200,0f));assertEquals(3f,animated(keys,999,0f));assertEquals(2,putKey(keys,110,5f).size)}
    @Test fun realWordTimesKeepSilences(){val cue=SrtCue(0,2000,"ola mundo",listOf(WordTime(100,500),WordTime(1200,1800)));assertEquals(-1,cue.wordAt(0));assertEquals(0,cue.wordAt(200));assertEquals(-1,cue.wordAt(900));assertEquals(1,cue.wordAt(1400));assertEquals(1200,cue.wordStart(1))}
}
