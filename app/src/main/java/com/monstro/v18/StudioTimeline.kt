package com.monstro.v18

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.Executors
import kotlin.math.*

/** A viewport, not a giant bitmap. Thumbnails are decoded off the UI thread. */
@UnstableApi
class StudioTimeline(context:Context):View(context) {
    var model:EditorModel?=null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val density=resources.displayMetrics.density
    private val thumbnails=linkedMapOf<String,Bitmap>()
    private val pending=mutableSetOf<String>()
    private val worker=Executors.newSingleThreadExecutor()
    private var pixelsPerSecond=42f*density
    private var scroll=0f
    private var downX=0f;private var downY=0f;private var lastX=0f;private var moved=false;private var scrubbing=false
    private val labelWidth=49f*density
    private val ruler=25f*density;private val row=33f*density
    private val pinch=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(detector:ScaleGestureDetector):Boolean {val time=(scroll+detector.focusX-labelWidth)/pixelsPerSecond;pixelsPerSecond=(pixelsPerSecond*detector.scaleFactor).coerceIn(8*density,220*density);scroll=(time*pixelsPerSecond-detector.focusX+labelWidth).coerceAtLeast(0f);invalidate();return true}})
    private fun x(ms:Long)=labelWidth+ms/1000f*pixelsPerSecond-scroll
    private fun time(x:Float)=((x-labelWidth+scroll)/pixelsPerSecond*1000).toLong().coerceAtLeast(0)
    override fun onDraw(c:Canvas){super.onDraw(c);val m=model ?: return
        c.drawColor(Color.rgb(14,15,22));paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=10*density
        val step=if(pixelsPerSecond<20*density)10 else if(pixelsPerSecond<50*density)5 else 1
        val start=(time(labelWidth)/1000/step*step).toInt()
        c.save();c.clipRect(labelWidth,0f,width.toFloat(),height.toFloat())
        for(s in start..(time(width.toFloat())/1000).toInt()+step step step){paint.color=Color.GRAY;c.drawText("${s}s",x(s*1000L)+3*density,15*density,paint);paint.color=0xff292a38.toInt();c.drawLine(x(s*1000L),ruler,x(s*1000L),height.toFloat(),paint)}
        fun block(start:Long,end:Long,lane:Int,label:String,color:Int,selected:Boolean=false,thumbnail:String?=null){val left=x(start);val right=x(end);if(right<labelWidth || left>width)return
            val top=ruler+lane*row+2*density;val rect=RectF(left,top,max(left+3*density,right-2*density),top+row-5*density)
            paint.color=color;c.drawRoundRect(rect,5*density,5*density,paint)
            thumbnail?.let {key->thumbnails[key]?.let {b->c.save();c.clipRect(rect);var bx=left;while(bx<right){c.drawBitmap(b,null,RectF(bx,top,bx+44*density,rect.bottom),paint);bx+=44*density};paint.color=0x99000000.toInt();c.drawRect(rect,paint);c.restore()}}
            if(selected){paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density;paint.color=Color.WHITE;c.drawRoundRect(rect,5*density,5*density,paint);paint.style=Paint.Style.FILL}
            c.save();c.clipRect(rect);paint.color=Color.WHITE;paint.textSize=10*density;c.drawText(label,max(left,labelWidth)+5*density,top+19*density,paint);c.restore()
        }
        var offset=0L
        m.clips.forEachIndexed {i,clip->val end=offset+m.speedMap(clip).outputDuration;val key="${clip.id}:${clip.trim.start}";requestThumbnail(clip,key);block(offset,end,0,clip.name,0xff493170.toInt(),i==m.selected,key);offset=end}
        m.studio.audio.forEach {block(it.start,it.end,1,it.name,0xff205c54.toInt(),m.focusedId==it.id)}
        m.studio.texts.forEach {block(it.start,it.end,2,it.text,0xff71501b.toInt(),m.focusedId==it.id)}
        m.lyrics?.cues?.forEachIndexed {i,it->block(it.startMs,it.endMs,3,it.text,0xff433880.toInt(),m.inspector=="Legenda" && m.focusedId==i.toString())}
        m.studio.fx.forEach {block(it.start,it.end,4,FxCatalog.get(it.presetId)?.name ?: "FX",0xff752b62.toInt(),m.focusedId==it.id)}
        val play=x(m.playhead);paint.color=Color.WHITE;paint.strokeWidth=2*density;c.drawLine(play,0f,play,height.toFloat(),paint);c.drawCircle(play,5*density,4*density,paint)
        c.restore();paint.color=0xff161720.toInt();c.drawRect(0f,0f,labelWidth,height.toFloat(),paint)
        listOf("VÍDEO","ÁUDIO","TEXTO","SRT","FX").forEachIndexed {i,label->paint.color=0xffb1aabd.toInt();paint.textSize=9*density;c.drawText(label,5*density,ruler+i*row+21*density,paint)}
    }
    private fun requestThumbnail(clip:VideoClip,key:String){if(thumbnails.containsKey(key) || !pending.add(key))return
        worker.submit {var image:Bitmap?=null;try{val r=MediaMetadataRetriever();try{r.setDataSource(context,Uri.parse(clip.uri));val full=if(android.os.Build.VERSION.SDK_INT>=27)r.getScaledFrameAtTime(clip.trim.start*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,120,80)else r.getFrameAtTime(clip.trim.start*1000);image=full?.let {Bitmap.createScaledBitmap(it,120,80,true).also {small->if(small!==it)it.recycle()}}}finally{r.release()}}catch(_:Exception){}
            post {image?.let {thumbnails[key]=it};if(thumbnails.size>64){val first=thumbnails.keys.first();thumbnails.remove(first)?.recycle()};invalidate()}
        }
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {pinch.onTouchEvent(e);val m=model ?: return false
        when(e.actionMasked){MotionEvent.ACTION_DOWN->{parent.requestDisallowInterceptTouchEvent(true);downX=e.x;lastX=e.x;downY=e.y;moved=false;scrubbing=e.y<ruler || abs(e.x-x(m.playhead))<12*density;if(scrubbing){m.pauseAll();m.seekTimeline(time(e.x))}}
            MotionEvent.ACTION_MOVE->{if(pinch.isInProgress)return true;if(abs(e.x-downX)>5*density)moved=true;if(scrubbing)m.seekTimeline(time(e.x))else scroll=(scroll+lastX-e.x).coerceIn(0f,max(0f,m.totalDuration/1000f*pixelsPerSecond-width+labelWidth+60*density));lastX=e.x;invalidate()}
            MotionEvent.ACTION_UP->{if(!moved && !scrubbing){val t=time(e.x);val lane=((downY-ruler)/row).toInt();m.seekTimeline(t);when(lane){0->m.focus("Vídeo");1->m.focus("Áudio",m.studio.audio.lastOrNull {t in it.start until it.end}?.id ?: "");2->m.focus("Texto",m.studio.texts.lastOrNull {t in it.start until it.end}?.id ?: "");3->m.focus("Legenda",m.lyrics?.cues?.indexOfLast {t in it.startMs until it.endMs}?.toString() ?: "");4->m.focus("FX",m.studio.fx.lastOrNull {t in it.start until it.end}?.id ?: "")}};performClick();parent.requestDisallowInterceptTouchEvent(false);invalidate()}
        };return true
    }
    override fun performClick():Boolean{super.performClick();return true}
    override fun onDetachedFromWindow(){worker.shutdownNow();thumbnails.values.forEach {it.recycle()};thumbnails.clear();super.onDetachedFromWindow()}
}
