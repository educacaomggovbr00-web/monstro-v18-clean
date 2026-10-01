package com.monstro.v18.monstro

import android.net.Uri
import com.monstro.v18.model.Clip
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class MonstroAiBridgeTest {
    private fun clips()=listOf(
        Clip(id="one",sourceUri=Uri.parse("content://test/one"),sourceDurationMs=2_000,timelineStartMs=1_000),
        Clip(id="two",sourceUri=Uri.parse("content://test/two"),sourceDurationMs=3_000,timelineStartMs=10_000),
    )

    @Test fun speechCaptionsKeepClipLocalTimesAcrossTimelineGaps() {
        val speech=SrtTrack(listOf(
            SrtCue(100,600,"primeiro",listOf(WordTime(100,600))),
            SrtCue(2_200,2_900,"segundo",listOf(WordTime(2_200,2_900))),
        ))
        val result=MonstroAiBridge.captionsFromPacked(speech,clips())
        assertEquals("primeiro",result.getValue("one").single().text)
        val second=result.getValue("two").single()
        assertEquals(200L,second.startTimeMs)
        assertEquals(900L,second.endTimeMs)
        assertEquals(200L,second.words.single().startTimeMs)
        val positioned=clips().map{it.copy(captions=result.getValue(it.id))}
        assertEquals(listOf(100L,2_200L),MonstroAiBridge.oldPackedCaptions(positioned).cues.map{it.startMs})
    }

    @Test fun plannedEffectsAndMarkersFollowTheNativeClipPosition() {
        val plan=AiEditPlan("test","9:16",emptyList(),emptyList(),emptyList(),
            listOf(AiFxSuggestion(2_500,2_900,"Glitch",1f,0)),listOf(AiMarker(2_800,"beat")))
        val result=MonstroAiBridge.applyPlan(clips(),plan)
        assertTrue(result.first().effects.isEmpty())
        val effect=result.last().effects.single()
        assertEquals(500f,effect.params.getValue("start"))
        assertEquals(900f,effect.params.getValue("end"))
        assertEquals(10_800L,MonstroAiBridge.timelineMarkers(plan,clips()).single().timeMs)
        assertEquals(10_000L,result.last().timelineStartMs)
    }
}
