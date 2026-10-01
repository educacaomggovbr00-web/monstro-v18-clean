package com.monstro.v18.monstro

import com.monstro.v18.model.EffectType
import org.junit.Assert.*
import org.junit.Test

class MonstroFxTest {
    @Test fun seekingDoesNotRestartStudioAnimationClock() {
        val clock=FrameClock()
        assertEquals(5_000L,clock.at(5_000_000L))
        clock.reset()
        assertEquals(8_000L,clock.at(8_000_000L))
        assertEquals(2_000L,clock.at(2_000_000L))
    }
    @Test fun allThousandOriginalRecipesRemainAddressable() {
        val presets=(0..19).flatMap{e->(0..4).flatMap{r->(0..9).map{t->FxCatalog.get("fx-$e-$r-$t")!!}}}
        assertEquals(1000,presets.map{it.signature}.toSet().size)
        presets.forEach{p->val effect=MonstroEffectBridge.preset(p);assertEquals(EffectType.MONSTRO_STUDIO,effect.type);assertEquals(p.engine.toFloat(),effect.params.getValue("engine"))}
    }
}
