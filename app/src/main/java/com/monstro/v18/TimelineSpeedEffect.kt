package com.monstro.v18
import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.*
import androidx.media3.effect.*

/** SpeedChangeEffect in 1.2 divides the entire composition timestamp. Normalize
 * each segment first so changing speed never inserts gaps between clips. */
@UnstableApi
class TimelineSpeedEffect(private val speed:Float,private val outputStartMs:Long,private val inputStartMs:Long):GlEffect {
 override fun toGlShaderProgram(context:Context,useHdr:Boolean):GlShaderProgram=object:BaseGlShaderProgram(useHdr,1){
    private var logged=false
    private val gl=GlProgram(context,"shaders/chaos.vert","shaders/copy.frag").apply {setBufferAttribute("aPosition",GlUtil.getNormalizedCoordinateBounds(),4)}
    override fun configure(inputWidth:Int,inputHeight:Int)=Size(inputWidth,inputHeight)
    override fun queueInputFrame(provider:GlObjectsProvider,inputTexture:GlTextureInfo,presentationTimeUs:Long){if(!logged){android.util.Log.i("MonstroSpeed","inputStart=$inputStartMs first=$presentationTimeUs outputStart=$outputStartMs speed=$speed");logged=true};super.queueInputFrame(provider,inputTexture,outputStartMs*1000+((presentationTimeUs-inputStartMs*1000)/speed).toLong())}
    override fun drawFrame(inputTexId:Int,presentationTimeUs:Long){gl.use();gl.setSamplerTexIdUniform("uInput",inputTexId,0);gl.bindAttributesAndUniforms();GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GlUtil.checkGlError()}
    override fun flush(){logged=false;super.flush()}
    override fun release(){try{gl.delete()}finally{super.release()}}
 }
}
