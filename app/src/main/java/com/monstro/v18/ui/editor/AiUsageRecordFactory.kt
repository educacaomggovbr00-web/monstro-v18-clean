package com.monstro.v18.ui.editor

import com.monstro.v18.engine.AiUsageLedger
import com.monstro.v18.model.Clip

internal object AiUsageRecordFactory {
    fun forClip(
        clip: Clip,
        effectKind: AiUsageLedger.EffectKind,
        modelName: String,
        recordedAtEpochMs: Long = System.currentTimeMillis()
    ): AiUsageLedger.Entry {
        val startMs = clip.timelineStartMs.coerceAtLeast(0L)
        return AiUsageLedger.Entry(
            clipId = clip.id,
            effectKind = effectKind,
            modelName = modelName.trim().ifBlank { effectKind.name },
            rangeStartMs = startMs,
            rangeEndMs = maxOf(startMs, clip.timelineEndMs),
            recordedAtEpochMs = recordedAtEpochMs.coerceAtLeast(0L)
        )
    }
}
