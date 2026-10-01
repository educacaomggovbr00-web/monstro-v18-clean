package com.monstro.v18.monstro

import com.monstro.v18.model.*
import com.monstro.v18.model.Effect as NewEffect
import com.monstro.v18.model.TimelineMarker as NewMarker

object MonstroAiBridge {
    // The original speech/planning engines concatenate input clips. Keep their
    // clock separate from the native timeline, where clips may have gaps/overlaps.
    private fun packedClips(clips:List<Clip>):List<Clip> {
        val motions=oldStudio(clips).motions
        var cursor=0L
        return clips.map { clip ->
            clip.copy(timelineStartMs=cursor).also {
                cursor+=SpeedMap(clip.trimEndMs-clip.trimStartMs,motions[clip.id]?.speed.orEmpty()).outputDuration
            }
        }
    }
    fun oldPackedCaptions(clips:List<Clip>)=oldCaptions(packedClips(clips))
    fun captionsFromPacked(track:SrtTrack,clips:List<Clip>):Map<String,List<Caption>> =
        packedClips(clips).associate { it.id to newCaptions(track,it) }
    fun timelineMarkers(plan:AiEditPlan,clips:List<Clip>):List<NewMarker> {
        val packed=packedClips(clips)
        return plan.markers.mapNotNull { marker ->
            val index=packed.indexOfLast { marker.timeMs>=it.timelineStartMs }
            if(index<0 || marker.timeMs>packed[index].timelineEndMs)null
            else NewMarker(timeMs=clips[index].timelineStartMs+marker.timeMs-packed[index].timelineStartMs,label=marker.label)
        }
    }
    fun oldClips(clips:List<Clip>)=clips.map {c->VideoClip(id=c.id,uri=c.sourceUri.toString(),name=c.name ?: "Vídeo",duration=c.sourceDurationMs,
        trim=TrimRange(c.trimStartMs,c.trimEndMs),volume=c.volume,mirror=c.flipHorizontal)}
    fun oldStudio(clips:List<Clip>)=StudioProject(motions=clips.associate {c->c.id to ClipMotion(speed=
        c.speedCurve?.points?.map{KeyPoint((it.position*(c.trimEndMs-c.trimStartMs)).toLong(),it.speed)} ?: listOf(KeyPoint(0,c.speed),KeyPoint(c.trimEndMs-c.trimStartMs,c.speed)))})
    fun oldCaptions(clips:List<Clip>)=SrtTrack(clips.flatMap{c->c.captions.map{q->SrtCue(q.startTimeMs+c.timelineStartMs,q.endTimeMs+c.timelineStartMs,q.text,
        q.words.map{WordTime(it.startTimeMs+c.timelineStartMs,it.endTimeMs+c.timelineStartMs)})}}.sortedBy{it.startMs})
    fun newCaptions(track:SrtTrack,clip:Clip)=track.cues.mapNotNull {q->
        val start=maxOf(q.startMs,clip.timelineStartMs);val end=minOf(q.endMs,clip.timelineEndMs)
        if(end<=start)null else Caption(text=q.text,startTimeMs=start-clip.timelineStartMs,endTimeMs=end-clip.timelineStartMs,
            words=q.words.mapIndexedNotNull{i,w->q.wordTimes.getOrNull(i)?.let{
                val ws=maxOf(it.start,start);val we=minOf(it.end,end)
                if(we<=ws)null else CaptionWord(w,ws-clip.timelineStartMs,we-clip.timelineStartMs)
            }},
            style=CaptionStyle(type=CaptionStyleType.KARAOKE,highlightColor=0xFFB46AFF))
    }
    fun applyPlan(clips:List<Clip>,plan:AiEditPlan):List<Clip> {
        val packed=packedClips(clips)
        return clips.mapIndexed {index,c->
            val packedClip=packed[index]
            val extra=mutableListOf<NewEffect>()
            plan.looks.firstOrNull{it.clipIndex==index}?.let {a->
                val presets=listOf("neon","trap","dark","cinema")
                presets.indexOf(a.preset).takeIf{it>=0}?.let{extra+=NewEffect(type=EffectType.MONSTRO_COLOR,params=mapOf("preset" to it.toFloat()))}
                extra+=NewEffect(type=EffectType.BRIGHTNESS,params=mapOf("value" to a.brightness))
                extra+=NewEffect(type=EffectType.CONTRAST,params=mapOf("value" to a.contrast+1f))
                extra+=NewEffect(type=EffectType.SATURATION,params=mapOf("value" to a.saturation/100f+1f))
                extra+=NewEffect(type=EffectType.TEMPERATURE,params=mapOf("value" to a.temperature*1.8f))
            }
            val motions=plan.motions.filter{it.clipIndex==index}.flatMap{a->listOf(
                Keyframe(a.timeMs,KeyframeProperty.SCALE_X,a.zoom),Keyframe(a.timeMs,KeyframeProperty.SCALE_Y,a.zoom),Keyframe(a.timeMs,KeyframeProperty.POSITION_X,a.x),Keyframe(a.timeMs,KeyframeProperty.POSITION_Y,a.y),Keyframe(a.timeMs,KeyframeProperty.ROTATION,a.rotation))}
            plan.effects.filter{it.startMs<packedClip.timelineEndMs && it.endMs>packedClip.timelineStartMs}.forEach {a->
                val candidates=FxCatalog.all.filter{it.category.equals(a.category,true)}.ifEmpty{FxCatalog.all}
                val p=candidates[Math.floorMod(a.variant,candidates.size)]
                extra+=MonstroEffectBridge.preset(p).let{it.copy(params=it.params+mapOf("start" to (a.startMs-packedClip.timelineStartMs).coerceAtLeast(0).toFloat(),"end" to (a.endMs-packedClip.timelineStartMs).coerceAtMost(c.durationMs).toFloat(),"intensity" to a.intensity))}
            }
            val speeds=plan.speeds.filter{it.clipIndex==index}.map{SpeedPoint((it.timeMs.toFloat()/(c.trimEndMs-c.trimStartMs)).coerceIn(0f,1f),it.speed.coerceIn(.25f,4f))}
            c.copy(effects=c.effects.filterNot{e->extra.any{it.type==e.type && it.type!=EffectType.MONSTRO_STUDIO}}+extra,keyframes=c.keyframes+motions,speedCurve=if(speeds.size>=2)SpeedCurve(speeds) else c.speedCurve)
        }
    }
}
