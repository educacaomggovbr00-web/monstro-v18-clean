package com.monstro.v18

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.Executors
import kotlin.math.*

/**
 * Mobile-editor timeline with a fixed center playhead: content moves underneath it,
 * matching the interaction model shown in the user's reference screenshots.
 * Thumbnail work stays off the UI thread and cache size is bounded.
 */
@UnstableApi
class StudioTimeline(context:Context):View(context) {
    var model:EditorModel?=null
    var onAddMedia:(()->Unit)?=null
    var onFocusChanged:((String,String)->Unit)?=null

    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val density=resources.displayMetrics.density
    private val thumbnails=linkedMapOf<String,Bitmap>()
    private val pending=mutableSetOf<String>()
    private val worker=Executors.newSingleThreadExecutor()
    private var pixelsPerSecond=58f*density

    private val ruler=27f*density
    private val mainRow=44f*density
    private val row=34f*density
    private var downX=0f
    private var downY=0f
    private var moved=false
    private var scrubbing=false
    private var dragStartPlayhead=0L
    private var trimEdge=0
    private var trimPreviewTime:Long?=null
    private var snappedAt:Long?=null
    private var addRect=RectF()

    private val pinch=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(detector:ScaleGestureDetector):Boolean{
            pixelsPerSecond=(pixelsPerSecond*detector.scaleFactor).coerceIn(12*density,260*density)
            invalidate()
            return true
        }
    })

    private fun centerX()=width/2f
    private fun x(ms:Long,m:EditorModel)=centerX()+(ms-m.playhead)/1000f*pixelsPerSecond
    private fun timeAt(px:Float,m:EditorModel)=
        (m.playhead+((px-centerX())/pixelsPerSecond*1000f).toLong()).coerceIn(0,(m.totalDuration-1).coerceAtLeast(0))

    override fun onDraw(c:Canvas){
        super.onDraw(c)
        val m=model ?: return
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)

        paint.color=0xff1b1b1b.toInt()
        c.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)

        // Ruler centered around the fixed playhead.
        paint.textSize=9*density
        val visibleMs=(width/pixelsPerSecond*1000f).toLong()
        val from=((m.playhead-visibleMs/2)/1000L-2).coerceAtLeast(0)
        val to=((m.playhead+visibleMs/2)/1000L+2).coerceAtLeast(from)
        for(sec in from..to){
            val px=x(sec*1000L,m)
            if(px !in -20f..width+20f)continue
            paint.color=0xff747474.toInt()
            c.drawCircle(px,16*density,1.1f*density,paint)
            c.drawText(String.format("%02d:%02d",sec/60,sec%60),px-13*density,10*density,paint)
        }

        fun drawBlock(start:Long,end:Long,top:Float,label:String,color:Int,selected:Boolean=false,thumbnail:String?=null){
            val left=x(start,m)
            val right=x(end,m)
            if(right<0 || left>width)return
            val rect=RectF(left,top,max(left+3*density,right),top+mainRow-5*density)
            paint.color=color
            c.drawRoundRect(rect,4*density,4*density,paint)
            thumbnail?.let {key->
                thumbnails[key]?.let {b->
                    c.save();c.clipRect(rect)
                    var bx=left
                    while(bx<right){
                        c.drawBitmap(b,null,RectF(bx,top,bx+48*density,rect.bottom),paint)
                        bx+=48*density
                    }
                    c.restore()
                }
            }
            if(selected){
                paint.style=Paint.Style.STROKE
                paint.strokeWidth=2*density
                paint.color=0xfff5f5f5.toInt()
                c.drawRoundRect(rect,4*density,4*density,paint)
                paint.style=Paint.Style.FILL
                paint.color=Color.WHITE
                c.drawRoundRect(RectF(rect.left-2*density,top+5*density,rect.left+3*density,rect.bottom-5*density),2*density,2*density,paint)
                c.drawRoundRect(RectF(rect.right-3*density,top+5*density,rect.right+2*density,rect.bottom-5*density),2*density,2*density,paint)
            }
            if(label.isNotBlank()){
                c.save();c.clipRect(rect)
                paint.color=0xeeffffff.toInt()
                paint.textSize=9*density
                c.drawText(label,left+6*density,top+26*density,paint)
                c.restore()
            }
        }

        val videoTop=ruler+2*density
        var offset=0L
        m.clips.forEachIndexed {index,clip->
            val duration=m.speedMap(clip).outputDuration
            val end=offset+duration
            val key="${clip.id}:${clip.trim.start}"
            requestThumbnail(clip,key)
            drawBlock(offset,end,videoTop,if(index==m.selected)clip.name else "",0xff333333.toInt(),index==m.selected,key)
            offset=end
        }

        // Add-media button at the end of the main track.
        val addX=x(offset,m)
        val button=40*density
        addRect=RectF(addX+6*density,videoTop,addX+6*density+button,videoTop+mainRow-5*density)
        if(addRect.right>=0 && addRect.left<=width){
            paint.color=Color.WHITE
            c.drawRoundRect(addRect,6*density,6*density,paint)
            paint.color=0xff2d2d2d.toInt()
            paint.strokeWidth=2*density
            c.drawLine(addRect.centerX()-8*density,addRect.centerY(),addRect.centerX()+8*density,addRect.centerY(),paint)
            c.drawLine(addRect.centerX(),addRect.centerY()-8*density,addRect.centerX(),addRect.centerY()+8*density,paint)
        }

        var laneTop=videoTop+mainRow+6*density
        fun layerBlock(start:Long,end:Long,label:String,color:Int,selected:Boolean){
            val left=x(start,m);val right=x(end,m)
            if(right>=0 && left<=width){
                val rect=RectF(left,laneTop,right,laneTop+row-4*density)
                paint.color=color;c.drawRoundRect(rect,4*density,4*density,paint)
                if(selected){paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density;paint.color=Color.WHITE;c.drawRoundRect(rect,4*density,4*density,paint);paint.style=Paint.Style.FILL}
                c.save();c.clipRect(rect);paint.color=Color.WHITE;paint.textSize=9*density;c.drawText(label,left+5*density,laneTop+20*density,paint);c.restore()
            }
        }

        if(m.studio.audio.isEmpty()){
            paint.color=0xff777777.toInt();paint.textSize=9*density;c.drawText("Adicionar áudio",8*density,laneTop+20*density,paint)
        } else m.studio.audio.forEach {layerBlock(it.start,it.end,it.name,0xff305d55.toInt(),m.focusedId==it.id)}
        laneTop+=row

        if(m.studio.texts.isEmpty() && m.studio.images.isEmpty()){
            paint.color=0xff777777.toInt();paint.textSize=9*density;c.drawText("Adicionar texto / camada",8*density,laneTop+20*density,paint)
        } else {
            m.studio.images.forEach {layerBlock(it.start,it.end,it.name,0xff3d5f87.toInt(),m.focusedId==it.id)}
            m.studio.texts.forEach {layerBlock(it.start,it.end,it.text,0xff775523.toInt(),m.focusedId==it.id)}
        }
        laneTop+=row

        m.lyrics?.cues?.forEachIndexed {i,it->layerBlock(it.startMs,it.endMs,it.text,0xff514690.toInt(),m.inspector=="Legenda"&&m.focusedId==i.toString())}
        laneTop+=row

        m.studio.fx.forEach {layerBlock(it.start,it.end,FxCatalog.get(it.presetId)?.name ?: "FX",0xff832d6d.toInt(),m.focusedId==it.id)}

        // Keyframe diamonds.
        fun diamond(time:Long,y:Float){
            val cx=x(time,m)
            if(cx !in -8f..width+8f)return
            paint.color=0xfff3f3f3.toInt()
            val cy=y
            val p=Path().apply{
                moveTo(cx,cy-4*density);lineTo(cx+4*density,cy);lineTo(cx,cy+4*density);lineTo(cx-4*density,cy);close()
            }
            c.drawPath(p,paint)
        }
        offset=0L
        m.clips.forEach {clip->
            val map=m.speedMap(clip)
            m.studio.motions[clip.id]?.let {motion->
                (motion.zoom+motion.rotation+motion.x+motion.y).map{it.time}.distinct().forEach{diamond(offset+it,videoTop+5*density)}
                motion.speed.forEach{diamond(offset+map.toOutput(it.time),videoTop+5*density)}
            }
            offset+=map.outputDuration
        }

        m.studio.markers.forEach {mark->
            val mx=x(mark.time,m)
            if(mx in 0f..width.toFloat()){
                paint.color=0xff37dbe4.toInt()
                c.drawCircle(mx,ruler-5*density,3*density,paint)
            }
        }

        trimPreviewTime?.let {
            val px=x(it,m);paint.color=0xff20d9e7.toInt();paint.strokeWidth=2*density
            c.drawLine(px,ruler,px,videoTop+mainRow,paint)
        }

        // Fixed center playhead.
        val cx=centerX()
        paint.color=Color.WHITE
        paint.strokeWidth=1.7f*density
        c.drawLine(cx,ruler,cx,height.toFloat(),paint)
        paint.color=Color.WHITE
        c.drawCircle(cx,ruler,5*density,paint)
    }

    private fun requestThumbnail(clip:VideoClip,key:String){
        if(thumbnails.containsKey(key)||!pending.add(key))return
        worker.submit {
            var image:Bitmap?=null
            try{
                val retriever=MediaMetadataRetriever()
                try{
                    retriever.setDataSource(context,Uri.parse(clip.uri))
                    val full=if(android.os.Build.VERSION.SDK_INT>=27)
                        retriever.getScaledFrameAtTime(clip.trim.start*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,144,84)
                    else retriever.getFrameAtTime(clip.trim.start*1000)
                    image=full?.let {Bitmap.createScaledBitmap(it,144,84,true).also {small->if(small!==it)it.recycle()}}
                }finally{retriever.release()}
            }catch(_:Exception){}
            post {
                pending.remove(key)
                image?.let {thumbnails[key]=it}
                while(thumbnails.size>40){val first=thumbnails.keys.first();thumbnails.remove(first)?.recycle()}
                invalidate()
            }
        }
    }

    private fun snapTime(m:EditorModel,raw:Long):Long{
        val points=mutableListOf<Long>(0L,m.totalDuration)
        var offset=0L
        m.clips.forEach {clip->points+=offset;offset+=m.speedMap(clip).outputDuration;points+=offset}
        m.studio.audio.forEach {points+=it.start;points+=it.end}
        m.studio.images.forEach {points+=it.start;points+=it.end}
        m.studio.texts.forEach {points+=it.start;points+=it.end}
        m.studio.fx.forEach {points+=it.start;points+=it.end}
        m.lyrics?.cues?.forEach {points+=it.startMs;points+=it.endMs}
        m.studio.markers.forEach {points+=it.time}
        val threshold=(12*density/pixelsPerSecond*1000f).toLong().coerceAtLeast(25L)
        val target=points.minByOrNull {abs(it-raw)}
        if(target!=null && abs(target-raw)<=threshold){
            if(snappedAt!=target){performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);snappedAt=target}
            return target
        }
        snappedAt=null
        return raw
    }

    override fun onTouchEvent(e:MotionEvent):Boolean{
        pinch.onTouchEvent(e)
        val m=model ?: return false
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{
                parent.requestDisallowInterceptTouchEvent(true)
                downX=e.x;downY=e.y;moved=false;scrubbing=false;trimEdge=0;trimPreviewTime=null;snappedAt=null
                dragStartPlayhead=m.playhead

                if(addRect.contains(e.x,e.y)){
                    return true
                }

                val clip=m.current
                if(clip!=null && e.y>=ruler && e.y<ruler+mainRow){
                    val start=m.timelineOffset
                    val end=start+m.speedMap(clip).outputDuration
                    val hit=13*density
                    trimEdge=when{
                        abs(e.x-x(start,m))<hit->-1
                        abs(e.x-x(end,m))<hit->1
                        else->0
                    }
                    if(trimEdge!=0){
                        m.pauseAll()
                        trimPreviewTime=if(trimEdge<0)start else end
                        return true
                    }
                }
                if(abs(e.x-centerX())<22*density || e.y<ruler){
                    scrubbing=true
                    m.pauseAll()
                    m.seekTimeline(snapTime(m,timeAt(e.x,m)))
                }
            }
            MotionEvent.ACTION_MOVE->{
                if(pinch.isInProgress)return true
                if(abs(e.x-downX)>5*density)moved=true
                if(trimEdge!=0){
                    val clip=m.current ?: return true
                    val start=m.timelineOffset
                    val end=start+m.speedMap(clip).outputDuration
                    trimPreviewTime=snapTime(m,timeAt(e.x,m)).coerceIn(start+80,(end-80).coerceAtLeast(start+80))
                }else{
                    val delta=((e.x-downX)/pixelsPerSecond*1000f).toLong()
                    m.seekTimeline(snapTime(m,dragStartPlayhead-delta))
                }
                invalidate()
            }
            MotionEvent.ACTION_UP->{
                if(addRect.contains(e.x,e.y) && !moved){
                    onAddMedia?.invoke()
                }else if(trimEdge!=0){
                    val clip=m.current
                    val preview=trimPreviewTime
                    if(clip!=null && preview!=null){
                        val start=m.timelineOffset
                        val map=m.speedMap(clip)
                        val local=(preview-start).coerceIn(1,(map.outputDuration-1).coerceAtLeast(1))
                        val source=map.toSource(local)
                        val next=if(trimEdge<0)
                            TrimRange((clip.trim.start+source).coerceAtMost(clip.trim.end-1),clip.trim.end)
                        else TrimRange(clip.trim.start,(clip.trim.start+source).coerceAtLeast(clip.trim.start+1))
                        if(next!=clip.trim)m.edit(trim=next)
                    }
                }else if(!moved && !scrubbing){
                    val t=timeAt(e.x,m)
                    m.seekTimeline(t)
                    when{
                        e.y<ruler+mainRow->{
                            var start=0L
                            val index=m.clips.indexOfFirst {clip->
                                val end=start+m.speedMap(clip).outputDuration
                                val hit=t in start until end
                                start=end
                                hit
                            }
                            if(index>=0)m.select(index)
                            m.focus("Vídeo")
                            onFocusChanged?.invoke("Vídeo","")
                        }
                        e.y<ruler+mainRow+row+6*density->{
                            val id=m.studio.audio.lastOrNull {t in it.start until it.end}?.id.orEmpty()
                            m.focus("Áudio",id);onFocusChanged?.invoke("Áudio",id)
                        }
                        e.y<ruler+mainRow+row*2+6*density->{
                            val image=m.studio.images.lastOrNull {t in it.start until it.end}
                            val text=m.studio.texts.lastOrNull {t in it.start until it.end}
                            when{
                                image!=null->{m.focus("Camada",image.id);onFocusChanged?.invoke("Camada",image.id)}
                                text!=null->{m.focus("Texto",text.id);onFocusChanged?.invoke("Texto",text.id)}
                            }
                        }
                        else->{
                            val cue=m.lyrics?.cues?.indexOfLast {t in it.startMs until it.endMs} ?: -1
                            if(cue>=0){m.focus("Legenda",cue.toString());onFocusChanged?.invoke("Legenda",cue.toString())}
                            else m.studio.fx.lastOrNull {t in it.start until it.end}?.let {fx->m.focus("FX",fx.id);onFocusChanged?.invoke("FX",fx.id)}
                        }
                    }
                }
                trimEdge=0;trimPreviewTime=null;scrubbing=false;snappedAt=null
                performClick()
                parent.requestDisallowInterceptTouchEvent(false)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL->{
                trimEdge=0;trimPreviewTime=null;scrubbing=false;snappedAt=null
                parent.requestDisallowInterceptTouchEvent(false);invalidate()
            }
        }
        return true
    }

    override fun performClick():Boolean{super.performClick();return true}

    override fun onDetachedFromWindow(){
        worker.shutdownNow()
        thumbnails.values.forEach {if(!it.isRecycled)it.recycle()}
        thumbnails.clear()
        super.onDetachedFromWindow()
    }
}
