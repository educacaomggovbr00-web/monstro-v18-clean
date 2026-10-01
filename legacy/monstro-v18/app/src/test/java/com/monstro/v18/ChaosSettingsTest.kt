package com.monstro.v18

import org.junit.Assert.*
import org.junit.Test

class ChaosSettingsTest {
    @Test fun oldProjectsStartWithoutNewEffectsOrZoom() {
        val restored = ChaosSettings.restore(emptyList(), 1f)
        assertTrue(restored.isIdentity)
    }
    @Test fun allEightEffectsCanBeCombinedAndIndependentlyDisabled() {
        var s = ChaosSettings()
        ChaosFx.values().forEach { s = s.toggle(it) }
        assertEquals(8, s.enabled.size)
        s = s.toggle(ChaosFx.RGB_SPLIT)
        assertEquals(7, s.enabled.size)
        assertFalse(s.has(ChaosFx.RGB_SPLIT))
        assertTrue(s.has(ChaosFx.MOTION_BLUR))
    }
    @Test fun migrationSanitizesUnknownEffectsAndInvalidZoom() {
        assertEquals(setOf("rgb"), ChaosSettings.restore(listOf("rgb", "rgb", "unknown"), 7f).enabled)
        assertEquals(3f, ChaosSettings.restore(emptyList(), 7f).zoom)
        assertEquals(1f, ChaosSettings.restore(emptyList(), Float.NaN).zoom)
    }
    @Test fun splittingPreservesEffectsOnBothHalves() {
        val clip = VideoClip(uri = "content://test", name = "clip", duration = 10000,
            trim = TrimRange(2000, 8000), chaos = ChaosSettings(setOf("glitch", "hue"), 1.7f))
        val (a, b) = clip.trim.split(2000)!!
        val first = clip.copy(trim = a)
        val second = clip.copy(id = "second", trim = b)
        assertEquals(first.chaos, second.chaos)
        assertEquals(6000L, first.trim.duration + second.trim.duration)
    }
}
