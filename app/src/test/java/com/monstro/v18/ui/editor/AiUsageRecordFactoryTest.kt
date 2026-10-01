package com.monstro.v18.ui.editor

import android.net.FakeUri
import com.monstro.v18.engine.AiUsageLedger
import com.monstro.v18.model.Clip
import org.junit.Assert.assertEquals
import org.junit.Test

class AiUsageRecordFactoryTest {

    @Test
    fun forClip_usesTimelineCoordinatesAndFallbackModelName() {
        val clip = Clip(
            id = "clip-1",
            sourceUri = FakeUri,
            sourceDurationMs = 10_000L,
            timelineStartMs = 5_000L,
            trimStartMs = 1_000L,
            trimEndMs = 9_000L
        )

        val entry = AiUsageRecordFactory.forClip(
            clip = clip,
            effectKind = AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL,
            modelName = "  ",
            recordedAtEpochMs = 42L
        )

        assertEquals("clip-1", entry.clipId)
        assertEquals(AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL, entry.effectKind)
        assertEquals("STYLE_TRANSFER_LOCAL", entry.modelName)
        assertEquals(5_000L, entry.rangeStartMs)
        assertEquals(13_000L, entry.rangeEndMs)
        assertEquals(42L, entry.recordedAtEpochMs)
    }
}
