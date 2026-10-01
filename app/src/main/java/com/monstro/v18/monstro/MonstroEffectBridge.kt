package com.monstro.v18.monstro

import androidx.media3.common.Effect as VideoEffect
import androidx.media3.common.util.UnstableApi
import com.monstro.v18.model.Effect
import com.monstro.v18.model.EffectType

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
object MonstroEffectBridge {
    fun build(effect:Effect):VideoEffect {
        fun value(key:String,default:Float,min:Float,max:Float)=
            effect.params[key]?.takeIf{it.isFinite()}?.coerceIn(min,max) ?: default
        return when(effect.type) {
            EffectType.MONSTRO_STUDIO -> {
                val engine=value("engine",2f,0f,19f).toInt()
                val recipe=value("recipe",0f,0f,4f).toInt()
                val envelope=value("envelope",6f,0f,9f).toInt()
                StudioEffect(FxLayer(presetId="fx-$engine-$recipe-$envelope",
                    start=value("start",0f,0f,Float.MAX_VALUE).toLong(),
                    end=value("end",Float.MAX_VALUE,0f,Float.MAX_VALUE).toLong(),
                    intensity=value("intensity",1f,0f,2f),speed=value("speed",1f,.1f,4f),direction=value("direction",0f,-180f,180f)),
                    emptyList(),FrameClock())
            }
            EffectType.MONSTRO_CHAOS -> {
                val enabled=ChaosFx.entries.filter {value(it.id,0f,0f,1f)>=.5f}.map{it.id}.toSet()
                ChaosEffect(ChaosSettings(enabled,value("zoom",1f,1f,3f)))
            }
            EffectType.MONSTRO_COLOR -> ColorPreset(listOf("neon","trap","dark","cinema")[value("preset",0f,0f,3f).toInt()])
            else -> error("Not a Monstro effect")
        }
    }
    fun preset(p:FxPreset)=Effect(type=EffectType.MONSTRO_STUDIO,params=mapOf(
        "engine" to p.engine.toFloat(),"recipe" to p.recipe.toFloat(),"envelope" to p.envelope.toFloat(),"intensity" to 1f,"speed" to 1f,"direction" to 0f))
}
