package com.monstro.v18

import android.content.Context
import android.graphics.*
import android.opengl.GLES20
import android.opengl.GLUtils
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.MotionEvent
import android.view.View
import androidx.media3.common.util.*
import androidx.media3.effect.*
import kotlin.math.*

class StudioPainter {
    private val paint=TextPaint(Paint.ANTI_ALIAS_FLAG)
    fun draw(canvas:Canvas,w:Int,h:Int,project:StudioProject,track:SrtTrack?,time:Long,simple:Boolean) {
        project.texts.filter { time in it.start until it.end }.forEach { drawText(canvas,w,h,it.text,it.style,time-it.start,simple) }
        val cue=track?.at(time) ?: return
        val i=track.cues.indexOf(cue);val style=project.captionStyles[i] ?: project.captionStyle
        drawCaption(canvas,w,h,cue,style,time,simple)
    }
    private fun drawCaption(c:Canvas,w:Int,h:Int,cue:SrtCue,s:TextStyle,time:Long,simple:Boolean) {
        val word=cue.wordAt(time)
        val styled=SpannableString(cue.text)
        val match=Regex("\\S+").findAll(cue.text).toList().getOrNull(word)
        if(match!=null){
            val start=match.range.first;val end=match.range.last+1
            styled.setSpan(ForegroundColorSpan(s.color),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            styled.setSpan(RelativeSizeSpan(if(simple)1.06f else 1.14f),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            styled.setSpan(StyleSpan(Typeface.BOLD),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val unit=min(w.toFloat(),h*16f/9f)
        paint.reset();paint.isAntiAlias=true;paint.typeface=Typeface.create(s.font,Typeface.BOLD)
        paint.textSize=unit*animated(s.scaleKeys,time-cue.startMs,s.size)*.58f
        paint.color=Color.LTGRAY;paint.alpha=(255*s.opacity).toInt().coerceIn(0,255)
        val x=animated(s.xKeys,time-cue.startMs,s.x)*w;val y=animated(s.yKeys,time-cue.startMs,s.y)*h
        val age=(time-cue.startMs).coerceAtLeast(0);val phase=(age/180f).coerceIn(0f,1f)
        if(s.animation=="Fade")paint.alpha=(paint.alpha*phase).toInt()
        if(s.shadow>0)paint.setShadowLayer(s.shadow*unit,0f,unit*.004f,Color.BLACK)
        else if(!simple && s.glow>0)paint.setShadowLayer(s.glow*unit,0f,0f,s.color)
        val box=(w*.90f).toInt().coerceAtLeast(1)
        var layout=StaticLayout.Builder.obtain(styled,0,styled.length,paint,box).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        while(layout.height>h*.30f && paint.textSize>unit*.018f){
            paint.textSize*=.86f
            layout=StaticLayout.Builder.obtain(styled,0,styled.length,paint,box).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        }
        val pop=if(simple || s.animation!="Pop")1f else .94f+.06f*(1-(1-phase).pow(3))
        c.save();c.translate(x-box/2f,y-layout.height/2f);c.scale(pop,pop,box/2f,layout.height/2f);layout.draw(c);c.restore();paint.clearShadowLayer()
    }
    private fun drawText(c:Canvas,w:Int,h:Int,text:String,s:TextStyle,time:Long,simple:Boolean,age:Long=time) {
        val unit=min(w.toFloat(),h*16f/9f)
        paint.reset(); paint.isAntiAlias=true; paint.typeface=Typeface.create(s.font,Typeface.BOLD)
        paint.textSize=unit*animated(s.scaleKeys,time,s.size);paint.color=s.color;paint.alpha=(255*s.opacity).toInt().coerceIn(0,255)
        val x=animated(s.xKeys,time,s.x)*w;val y=animated(s.yKeys,time,s.y)*h
        val phase=(age/200f).coerceIn(0f,1f)
        val pop=if(simple || s.animation!="Pop") 1f else .7f+.3f*(1-(1-phase).pow(3))+.1f*sin(phase*PI.toFloat())
        val scale=pop
        if(s.animation=="Fade")paint.alpha=(paint.alpha*phase).toInt()
        val alpha=paint.alpha
        val visible=if(s.animation=="Digitar") text.take((age/45+1).toInt().coerceAtMost(text.length)) else text
        var layout:StaticLayout
        do {
            layout=StaticLayout.Builder.obtain(visible,0,visible.length,paint,(w*.90f).toInt().coerceAtLeast(1)).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
            if(layout.height<h*.35 || paint.textSize<unit*.018f)break
            paint.textSize*=.85f
        }while(true)
        c.save();c.translate(x-w*.45f,y-layout.height*.5f);c.scale(scale,scale,w*.45f,layout.height*.5f)
        if(s.stroke>0){paint.style=Paint.Style.STROKE;paint.strokeJoin=Paint.Join.ROUND;paint.strokeWidth=s.stroke*unit;paint.color=Color.BLACK;paint.alpha=alpha;layout.draw(c)}
        paint.style=Paint.Style.FILL;paint.color=s.color;paint.alpha=alpha
        if(!simple && s.glow>0) {paint.setShadowLayer(s.glow*unit,0f,0f,s.color);layout.draw(c)}
        paint.clearShadowLayer()
        if(s.shadow>0)paint.setShadowLayer(s.shadow*unit,0f,unit*.004f,Color.BLACK)
        layout.draw(c);paint.clearShadowLayer();c.restore()
    }
}
@UnstableApi
class StudioPreview(context:Context):View(context) {
    var model:EditorModel?=null
    private val painter=StudioPainter()
    init {setLayerType(LAYER_TYPE_SOFTWARE,null)}
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas);model?.let { painter.draw(canvas,width,height,it.studio,it.lyrics,it.playhead,it.simpleLyrics) }
        postInvalidateDelayed(33)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(event.action!=MotionEvent.ACTION_UP)return true
        val m=model ?: return false
        val cue=m.lyrics?.at(m.playhead)
        val text=m.studio.texts.lastOrNull { m.playhead in it.start until it.end && abs(event.y/height-it.style.y)<.18f }
        if(text!=null)m.focus("Texto",text.id)
        else if(cue!=null)m.focus("Legenda",m.lyrics!!.cues.indexOf(cue).toString())
        else m.focus("Vídeo")
        performClick();return true
    }
    override fun performClick():Boolean {super.performClick();return true}
}
@UnstableApi
class StudioOverlay(private val project:StudioProject,private val track:SrtTrack?,private val simple:Boolean,private val clock:FrameClock):TextureOverlay() {
    private var bitmap:Bitmap?=null;private var texture=0;private var size=Size(1,1);private var tick=Long.MIN_VALUE
    private val painter=StudioPainter()
    override fun configure(videoSize:Size){release();size=videoSize;val ratio=min(1f,(if(simple)360f else 1080f)/size.width);bitmap=Bitmap.createBitmap(max(1,(size.width*ratio).toInt()),max(1,(size.height*ratio).toInt()),Bitmap.Config.ARGB_8888);texture=GlUtil.createTexture(bitmap!!.width,bitmap!!.height,false);tick=Long.MIN_VALUE}
    override fun getTextureId(presentationTimeUs:Long):Int {
        val t=clock.at(presentationTimeUs);val next=t/(if(simple)66 else 33)
        if(tick!=next){val b=bitmap!!;b.eraseColor(Color.TRANSPARENT);val c=Canvas(b);c.translate(0f,b.height.toFloat());c.scale(1f,-1f);painter.draw(c,b.width,b.height,project,track,t,simple);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,b);GlUtil.checkGlError();tick=next}
        return texture
    }
    override fun getTextureSize(presentationTimeUs:Long)=Size(bitmap!!.width,bitmap!!.height)
    override fun getOverlaySettings(presentationTimeUs:Long)=OverlaySettings.Builder().setScale(size.width.toFloat()/bitmap!!.width,size.height.toFloat()/bitmap!!.height).build()
    override fun release(){if(texture!=0){GlUtil.deleteTexture(texture);texture=0};bitmap?.recycle();bitmap=null}
}
