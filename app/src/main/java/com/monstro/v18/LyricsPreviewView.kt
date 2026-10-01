package com.monstro.v18

import android.content.Context
import android.graphics.Canvas
import android.view.View
import androidx.media3.common.util.UnstableApi

@UnstableApi
class LyricsPreviewView(context: Context) : View(context) {
    var model: EditorModel? = null
    private val painter = LyricsPainter()
    init { setLayerType(LAYER_TYPE_SOFTWARE,null); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val editor = model ?: return
        val track = editor.lyrics ?: return
        painter.draw(canvas,width,height,track,editor.timelineOffset+editor.player.currentPosition,editor.simpleLyrics,editor.purpleLyrics)
        if (isShown) postInvalidateDelayed(if(editor.player.isPlaying && !editor.simpleLyrics) 33 else 100)
    }
}
