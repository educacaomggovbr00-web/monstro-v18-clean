package com.monstro.v18

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.util.*
import androidx.media3.effect.*

/** Export timestamps are normalized per item. Preview supplies its authoritative playhead. */
class FrameClock(private val start:Long=0,private val speed:Float=1f,private val preview:(()->Long)?=null) {
    private var first:Long?=null
    fun at(us:Long):Long { preview?.let { return it() }; if(first==null) first=us; return start+((us-first!!)/1000.0/speed).toLong() }
    fun reset(){first=null}
}
@UnstableApi
class StudioEffect(private val layer:FxLayer?,private val zoom:List<KeyPoint>,private val clock:FrameClock,private val clipStart:Long=0):GlEffect {
    override fun toGlShaderProgram(context:Context,useHdr:Boolean):GlShaderProgram = object:BaseGlShaderProgram(useHdr,1) {
        val gl=GlProgram(context,"shaders/chaos.vert","shaders/studio.frag").apply { setBufferAttribute("aPosition",GlUtil.getNormalizedCoordinateBounds(),4) }
        override fun configure(inputWidth:Int,inputHeight:Int)=Size(inputWidth,inputHeight)
        override fun drawFrame(inputTexId:Int,presentationTimeUs:Long) {
            val time=clock.at(presentationTimeUs); val preset=layer?.let { FxCatalog.get(it.presetId) }
            val active=layer!=null && time>=layer.start && time<layer.end
            gl.use();gl.setSamplerTexIdUniform("uInput",inputTexId,0)
            gl.setFloatUniform("uZoom",animated(zoom,time-clipStart,1f).coerceIn(1f,4f))
            gl.setFloatUniform("uEngine",preset?.engine?.toFloat() ?: 0f)
            gl.setFloatUniform("uRecipe",preset?.recipe?.toFloat() ?: 0f)
            gl.setFloatUniform("uEnvelope",preset?.envelope?.toFloat() ?: 0f)
            gl.setFloatUniform("uTime",((time-(layer?.start ?: 0))/1000f)*(layer?.speed ?: 1f))
            gl.setFloatUniform("uIntensity",if(active) animated(layer!!.keys,time-layer.start,layer.intensity) else 0f)
            gl.setFloatUniform("uDirection",(layer?.direction ?: 0f)*Math.PI.toFloat()/180f)
            gl.bindAttributesAndUniforms();GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GlUtil.checkGlError()
        }
        override fun flush(){clock.reset();super.flush()}
        override fun release(){try{gl.delete()}finally{super.release()}}
    }
}
