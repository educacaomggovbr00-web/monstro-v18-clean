package com.monstro.v18.monstro

/** Stable IDs also used in saved projects. Never depend on enum ordinal. */
enum class ChaosFx(val id: String, val label: String, val description: String) {
    MOTION_BLUR("motionblur", "Motion Blur", "Rastro entre quadros"),
    AUTO_ZOOM("smartzoom", "Auto Retention", "Zoom automático suave"),
    GLITCH("glitch", "Digital Glitch", "Deslocamento de faixas"),
    RGB_SPLIT("rgb", "RGB Split", "Separação de cores"),
    SHAKE("shake", "Impact Shake", "Tremor de câmera"),
    STROBE("strobe", "Psycho Strobe", "Pulsos de luz · 2 por segundo"),
    HUE("hue", "Hue Shift", "Rotação das cores"),
    VIGNETTE("vignette", "Focus Edge", "Bordas escurecidas")
}

data class ChaosSettings(val enabled: Set<String> = emptySet(), val zoom: Float = 1f) {
    init { require(zoom.isFinite() && zoom in 1f..3f) }
    fun has(fx: ChaosFx) = fx.id in enabled
    fun toggle(fx: ChaosFx) = copy(enabled = if (has(fx)) enabled - fx.id else enabled + fx.id)
    val isIdentity get() = enabled.isEmpty() && zoom == 1f
    companion object {
        fun restore(ids: Collection<String>, zoom: Float): ChaosSettings = ChaosSettings(
            ids.filter { id -> ChaosFx.values().any { it.id == id } }.toSet(),
            if (zoom.isFinite()) zoom.coerceIn(1f, 3f) else 1f
        )
    }
}
