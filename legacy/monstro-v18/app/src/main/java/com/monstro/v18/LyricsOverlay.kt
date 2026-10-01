package com.monstro.v18

import android.graphics.*
import android.opengl.GLES20
import android.opengl.GLUtils
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextureOverlay
import kotlin.math.*

/** Shared drawing code for the UI preview and the MP4 overlay. */
class LyricsPainter {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD) }
    fun draw(canvas: Canvas, width: Int, height: Int, track: SrtTrack, timeMs: Long, simple: Boolean, purple: Boolean) {
        val cue = track.at(timeMs) ?: return
        val unit = min(width.toFloat(), height * 16f/9f)
        val index = cue.wordAt(timeMs)
        if(index<0)return
        val word = cue.words[index].uppercase(java.util.Locale.ROOT)
        val neon = if (purple) Color.rgb(194,80,255) else Color.rgb(255,35,85)
        paint.color = Color.LTGRAY; paint.textSize = unit * .042f
        paint.textAlign = Paint.Align.LEFT
        paint.setShadowLayer(unit*.007f, 0f, unit*.003f, Color.BLACK)
        var layout: StaticLayout
        // Fit the complete phrase instead of truncating it.
        do {
            layout = StaticLayout.Builder.obtain(cue.text,0,cue.text.length,paint,(width*.88f).toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
            if (layout.height <= height*.25f || paint.textSize <= unit*.012f) break
            paint.textSize *= .85f
        } while (true)
        canvas.save(); canvas.translate(width*.06f, min(height*.80f,height-layout.height-height*.03f))
        layout.draw(canvas); canvas.restore()
        val age = (timeMs-cue.wordStart(index)).coerceAtLeast(0)
        val phase = (age/180f).coerceIn(0f,1f)
        val scale = if (simple) 1f else .70f + .30f*(1f-(1f-phase).pow(3)) + .12f*sin(phase*PI.toFloat())
        paint.textSize = unit*.15f
        val measured = paint.measureText(word)
        if (measured > width*.84f) paint.textSize *= width*.84f/measured
        paint.textAlign = Paint.Align.CENTER; paint.color = neon
        if (simple) paint.setShadowLayer(unit*.006f,0f,unit*.003f,Color.BLACK)
        else paint.setShadowLayer(unit*.026f,0f,0f,neon)
        val x = width/2f; val y = height*.74f
        canvas.save(); canvas.scale(scale,scale,x,y)
        canvas.drawText(word,x,y,paint)
        if (!simple) { paint.clearShadowLayer(); canvas.drawText(word,x,y,paint) }
        canvas.restore(); paint.clearShadowLayer()
    }
}

/** Reuses one bitmap and texture: no bitmap allocations for every encoded frame. */
@UnstableApi
class LyricsOverlay(private val track: SrtTrack, private val simple: Boolean, private val purple: Boolean) : TextureOverlay() {
    private var bitmap: Bitmap? = null
    private var texture = 0
    private var tick = Long.MIN_VALUE
    private var videoSize = Size(1,1)
    private val painter = LyricsPainter()
    override fun configure(videoSize: Size) {
        release(); this.videoSize = videoSize
        android.util.Log.i("MonstroLyrics", "configure ${videoSize.width}x${videoSize.height}")
        val ratio = min(1f, (if (simple) 360f else 720f)/videoSize.width)
        bitmap = Bitmap.createBitmap(max(1,(videoSize.width*ratio).toInt()),max(1,(videoSize.height*ratio).toInt()),Bitmap.Config.ARGB_8888)
        texture = GlUtil.createTexture(bitmap!!.width,bitmap!!.height,false)
        tick = Long.MIN_VALUE
    }
    override fun getTextureId(presentationTimeUs: Long): Int {
        val now = presentationTimeUs/1000
        if(tick == Long.MIN_VALUE || now % 500L < 34L) android.util.Log.i("MonstroLyrics", "time=$now cue=${track.at(now)?.startMs}")
        val next = now / (if (simple) 66 else 33)
        if (tick != next) {
            val b = bitmap!!; b.eraseColor(Color.TRANSPARENT)
            val c = Canvas(b)
            // Canvas is top-down; GL textures are bottom-up.
            c.translate(0f,b.height.toFloat()); c.scale(1f,-1f)
            painter.draw(c,b.width,b.height,track,now,simple,purple)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,b)
            GlUtil.checkGlError(); tick = next
        }
        return texture
    }
    override fun getTextureSize(presentationTimeUs: Long) = Size(bitmap!!.width,bitmap!!.height)
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = OverlaySettings.Builder()
        .setScale(videoSize.width.toFloat()/bitmap!!.width,videoSize.height.toFloat()/bitmap!!.height).build()
    override fun release() {
        if (texture != 0) { GlUtil.deleteTexture(texture); texture = 0 }
        bitmap?.recycle(); bitmap = null
    }
}

// Media3 1.2.1's single-input graph only honors Presentation as a composition
// effect. Overlays belong to each item's effect chain; the processor supplies
// cumulative output timestamps across the sequence via FrameInfo offsets.
@UnstableApi
fun lyricsEffects(track: SrtTrack?, simple: Boolean, purple: Boolean): List<androidx.media3.common.Effect> =
    if (track == null) emptyList() else listOf(androidx.media3.effect.OverlayEffect(
        com.google.common.collect.ImmutableList.of<TextureOverlay>(LyricsOverlay(track,simple,purple))))
