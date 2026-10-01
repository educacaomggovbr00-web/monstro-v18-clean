package com.monstro.v18.monstro

import com.monstro.v18.model.EffectType
import org.junit.Assert.*
import org.junit.Test

class MonstroFxTest {
    @Test fun allThousandOriginalRecipesRemainAddressable() {
        val presets=(0..19).flatMap{e->(0..4).flatMap{r->(0..9).map{t->FxCatalog.get("fx-$e-$r-$t")!!}}}
        assertEquals(1000,presets.map{it.signature}.toSet().size)
        presets.forEach{p->val effect=MonstroEffectBridge.preset(p);assertEquals(EffectType.MONSTRO_STUDIO,effect.type);assertEquals(p.engine.toFloat(),effect.params.getValue("engine"))}
    }
}
