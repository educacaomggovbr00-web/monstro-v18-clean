package com.monstro.v18.monstro

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** The same shader is used by ExoPlayer and Transformer, entirely offline. */
@UnstableApi
class ChaosEffect(private val settings: ChaosSettings) : GlEffect {
    override fun isNoOp(inputWidth: Int, inputHeight: Int) = settings.isIdentity
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        ChaosShaderProgram(context, settings, useHdr)
}

@UnstableApi
private class ChaosShaderProgram(context: Context, private val settings: ChaosSettings, private val useHdr: Boolean) :
    BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram
    private var history = 0
    private var readFramebuffer = 0
    private var width = 0
    private var height = 0
    private var previousTimeUs = Long.MIN_VALUE

    init {
        try {
            program = GlProgram(context, "shaders/chaos.vert", "shaders/chaos.frag")
            program.setBufferAttribute("aPosition", GlUtil.getNormalizedCoordinateBounds(), 4)
            program.setFloatUniform("uZoom", settings.zoom)
            mapOf("uAutoZoom" to ChaosFx.AUTO_ZOOM, "uGlitch" to ChaosFx.GLITCH,
                "uRgb" to ChaosFx.RGB_SPLIT, "uShake" to ChaosFx.SHAKE,
                "uStrobe" to ChaosFx.STROBE, "uHue" to ChaosFx.HUE,
                "uVignette" to ChaosFx.VIGNETTE).forEach { (name, fx) ->
                program.setFloatUniform(name, if (settings.has(fx)) 1f else 0f)
            }
        } catch (e: Exception) { throw VideoFrameProcessingException(e) }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        try {
            if (history != 0) GlUtil.deleteTexture(history)
            width = inputWidth; height = inputHeight
            history = if (settings.has(ChaosFx.MOTION_BLUR)) GlUtil.createTexture(width, height, useHdr) else GlUtil.createTexture(1, 1, useHdr)
            previousTimeUs = Long.MIN_VALUE
            return Size(width, height)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uInput", inputTexId, 0)
            program.setSamplerTexIdUniform("uHistory", history, 1)
            // Bounded seconds retain floating-point precision for long videos.
            program.setFloatUniform("uTime", ((presentationTimeUs % 600_000_000L) / 1_000_000.0).toFloat())
            val delta = if (previousTimeUs == Long.MIN_VALUE) 0L else presentationTimeUs - previousTimeUs
            val blend = if (settings.has(ChaosFx.MOTION_BLUR) && delta in 1..250_000) 0.30f else 0f
            program.setFloatUniform("uBlurWeight", blend)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
            // Store the previous SOURCE frame, not the output: no recursive color/flash accumulation.
            // Copy input via a temporary read framebuffer, then restore the caller's output framebuffer.
            if (settings.has(ChaosFx.MOTION_BLUR)) {
                val bound = IntArray(1)
                GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, bound, 0)
                if (readFramebuffer == 0) readFramebuffer = GlUtil.createFboForTexture(inputTexId)
                else {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, readFramebuffer)
                    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, inputTexId, 0)
                }
                try {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, readFramebuffer)
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                    GlUtil.bindTexture(GLES20.GL_TEXTURE_2D, history, GLES20.GL_LINEAR)
                    GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
                    GlUtil.checkGlError()
                } finally {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bound[0])
                }
            }
            previousTimeUs = presentationTimeUs
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    override fun flush() { previousTimeUs = Long.MIN_VALUE; super.flush() }
    override fun signalEndOfCurrentInputStream() { previousTimeUs = Long.MIN_VALUE; super.signalEndOfCurrentInputStream() }
    override fun release() {
        try {
            program.delete()
            if (readFramebuffer != 0) { GlUtil.deleteFbo(readFramebuffer); readFramebuffer = 0 }
            if (history != 0) { GlUtil.deleteTexture(history); history = 0 }
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
        finally { super.release() }
    }
}
