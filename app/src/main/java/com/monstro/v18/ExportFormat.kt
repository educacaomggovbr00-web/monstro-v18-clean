package com.monstro.v18

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.*
import androidx.media3.effect.*

data class ExportFormat(
    val ratio: String = "9:16",
    val light: Boolean = true,
    val background: String = "blur",
    val bitrateMode: String = "recommended",
    val codec: String = "H264"
) {
    constructor(vertical:Boolean, light:Boolean):this(if(vertical)"9:16" else "16:9",light)

    val vertical get() = ratio=="9:16" || ratio=="4:5"
    private val longEdge get() = if(light) 960 else 1920
    private val shortEdge get() = if(light) 540 else 1080
    val width get() = when(ratio){
        "16:9" -> longEdge
        "1:1" -> shortEdge
        "4:5" -> shortEdge
        else -> shortEdge
    }
    val height get() = when(ratio){
        "16:9" -> shortEdge
        "1:1" -> shortEdge
        "4:5" -> if(light)675 else 1350
        else -> longEdge
    }
    val aspect get() = width.toFloat()/height
    val resolutionLabel get() = if(light)"540p" else "1080p"
    val bitrate get() = if(light) when(bitrateMode){
        "low"->1_800_000
        "high"->4_500_000
        else->2_800_000
    } else when(bitrateMode){
        "low"->6_000_000
        "high"->16_000_000
        else->10_000_000
    }
    val videoMime get() = if(codec=="HEVC") MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264
}

@UnstableApi
class AspectBackgroundEffect(private val format: ExportFormat) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = object : BaseGlShaderProgram(useHdr,1) {
        private val program = GlProgram(context,"shaders/chaos.vert","shaders/aspect.frag").apply {
            setBufferAttribute("aPosition",GlUtil.getNormalizedCoordinateBounds(),4)
        }
        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            program.setFloatUniform("uSourceAspect",inputWidth.toFloat()/inputHeight)
            program.setFloatUniform("uTargetAspect",format.aspect)
            program.setFloatUniform("uBackgroundMode",when(format.background){"solid"->1f;"pattern"->2f;else->0f})
            return Size(format.width,format.height)
        }
        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            program.use(); program.setSamplerTexIdUniform("uInput",inputTexId,0)
            program.bindAttributesAndUniforms(); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4); GlUtil.checkGlError()
        }
        override fun release() { try { program.delete() } finally { super.release() } }
    }
}
