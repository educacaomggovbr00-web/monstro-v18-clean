package com.monstro.v18.engine

import android.net.FakeUri
import com.monstro.v18.model.Clip
import com.monstro.v18.model.Effect
import com.monstro.v18.model.EffectType
import com.monstro.v18.model.ExportConfig
import com.monstro.v18.model.Track
import com.monstro.v18.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportResumePolicyTest {
    @Test
    fun acceptsOneGapFreeEmbeddedAvClip() {
        val decision = ExportResumePolicy.evaluate(
            tracks = listOf(track(clip = clip())),
            config = ExportConfig(),
        )

        assertTrue(decision.eligible)
        assertEquals(ExportResumePolicy.Reason.ELIGIBLE, decision.reason)
    }

    @Test
    fun rejectsGapsAndAdditionalSequences() {
        val gap = ExportResumePolicy.evaluate(
            tracks = listOf(track(clip = clip(timelineStartMs = 100L))),
            config = ExportConfig(),
        )
        val secondSequence = ExportResumePolicy.evaluate(
            tracks = listOf(
                track(clip = clip()),
                Track(type = TrackType.AUDIO, index = 1, clips = listOf(clip(id = "audio"))),
            ),
            config = ExportConfig(),
        )

        assertEquals(ExportResumePolicy.Reason.TIMELINE_NOT_CONTINUOUS, gap.reason)
        assertEquals(ExportResumePolicy.Reason.NOT_SINGLE_AV_SEQUENCE, secondSequence.reason)
    }

    @Test
    fun rejectsNonMp4SpecialAndSpeedChangingExports() {
        val webm = ExportResumePolicy.evaluate(
            tracks = listOf(track(clip())),
            config = ExportConfig(transparentBackground = true),
            outputExtension = "webm",
        )
        val speed = ExportResumePolicy.evaluate(
            tracks = listOf(track(clip(speed = 2f))),
            config = ExportConfig(),
        )
        val speedEffect = ExportResumePolicy.evaluate(
            tracks = listOf(
                track(
                    clip = clip(
                        effects = listOf(Effect(type = EffectType.SPEED, enabled = true)),
                    )
                )
            ),
            config = ExportConfig(),
        )

        assertEquals(ExportResumePolicy.Reason.NOT_MP4, webm.reason)
        assertEquals(ExportResumePolicy.Reason.SPEED_CHANGE, speed.reason)
        assertEquals(ExportResumePolicy.Reason.SPEED_CHANGE, speedEffect.reason)
    }

    private fun track(clip: Clip) = Track(type = TrackType.VIDEO, index = 0, clips = listOf(clip))

    private fun clip(
        id: String = "video",
        timelineStartMs: Long = 0L,
        speed: Float = 1f,
        effects: List<Effect> = emptyList(),
    ) = Clip(
        id = id,
        sourceUri = FakeUri,
        sourceDurationMs = 10_000L,
        timelineStartMs = timelineStartMs,
        trimEndMs = 10_000L,
        speed = speed,
        effects = effects,
    )
}
