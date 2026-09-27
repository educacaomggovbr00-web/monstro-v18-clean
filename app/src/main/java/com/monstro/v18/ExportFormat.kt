package com.monstro.v18

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.util.*
import androidx.media3.effect.*

data class ExportFormat(val vertical: Boolean = true, val light: Boolean = true) {
    val width get() = if (vertical) { if (light) 540 else 1080 } else { if (light) 960 else 1920 }
    val height get() = if (vertical) { if (light) 960 else 1920 } else { if (light) 540 else 1080 }
    val bitrate get() = if (light) 2_500_000 else 10_000_000
}

@UnstableApi
class AspectBackgroundEffect(private val format: ExportFormat) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = object : BaseGlShaderProgram(useHdr,1) {
        private val program = GlProgram(context,"shaders/chaos.vert","shaders/aspect.frag").apply {
            setBufferAttribute("aPosition",GlUtil.getNormalizedCoordinateBounds(),4)
        }
        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            program.setFloatUniform("uSourceAspect",inputWidth.toFloat()/inputHeight)
            program.setFloatUniform("uTargetAspect",format.width.toFloat()/format.height)
            return Size(format.width,format.height)
        }
        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            program.use(); program.setSamplerTexIdUniform("uInput",inputTexId,0)
            program.bindAttributesAndUniforms(); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4); GlUtil.checkGlError()
        }
        override fun release() { try { program.delete() } finally { super.release() } }
    }
}
