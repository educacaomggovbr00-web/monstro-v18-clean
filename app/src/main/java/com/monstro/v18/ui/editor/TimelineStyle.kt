package com.monstro.v18.ui.editor

import com.monstro.v18.ui.theme.ClearCutAccents
import androidx.compose.ui.graphics.Color
import com.monstro.v18.model.TrackType

internal fun trackAccentColor(trackType: TrackType): Color = when (trackType) {
    TrackType.VIDEO -> ClearCutAccents.Blue
    TrackType.AUDIO -> ClearCutAccents.Green
    TrackType.OVERLAY -> ClearCutAccents.Peach
    TrackType.TEXT -> ClearCutAccents.Mauve
    TrackType.ADJUSTMENT -> ClearCutAccents.Yellow
}
