package com.monstro.v18

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

@UnstableApi
class MirrorEffect:GlEffect {
    override fun toGlShaderProgram(context:Context,useHdr:Boolean):GlShaderProgram =
        object:BaseGlShaderProgram(useHdr,1){
            private val program=GlProgram(context,"shaders/chaos.vert","shaders/mirror.frag").apply {
                setBufferAttribute("aPosition",GlUtil.getNormalizedCoordinateBounds(),4)
            }
            override fun configure(inputWidth:Int,inputHeight:Int)=Size(inputWidth,inputHeight)
            override fun drawFrame(inputTexId:Int,presentationTimeUs:Long){
                program.use();program.setSamplerTexIdUniform("uInput",inputTexId,0)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GlUtil.checkGlError()
            }
            override fun release(){try{program.delete()}finally{super.release()}}
        }
}
