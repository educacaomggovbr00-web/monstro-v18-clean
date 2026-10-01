package com.monstro.v18.monstro

enum class VideoLengthKind { MICRO, SHORT, STANDARD, LONG }
enum class VideoContentKind { TALK, RHYTHMIC, MIXED, VISUAL }

data class VideoPerception(
    val durationMs:Long,
    val lengthKind:VideoLengthKind,
    val contentKind:VideoContentKind,
    val speechRatio:Float,
    val beatsPerMinute:Float,
    val effectBudget:Int,
    val frameBudget:Int,
    val recommendedRatio:String
) {
    val shortForm get()=lengthKind==VideoLengthKind.MICRO || lengthKind==VideoLengthKind.SHORT
    val label:String get() {
        val length=when(lengthKind){
            VideoLengthKind.MICRO->"Vídeo muito curto"
            VideoLengthKind.SHORT->"Vídeo curto"
            VideoLengthKind.STANDARD->"Vídeo médio"
            VideoLengthKind.LONG->"Vídeo longo"
        }
        val content=when(contentKind){
            VideoContentKind.TALK->"fala/conversa"
            VideoContentKind.RHYTHMIC->"ritmo/música"
            VideoContentKind.MIXED->"fala + ritmo"
            VideoContentKind.VISUAL->"visual"
        }
        return "$length · $content"
    }
}

object VideoPerceptionAnalyzer {
    fun analyze(
        clips:List<VideoClip>,
        project:StudioProject,
        captions:SrtTrack?,
        currentRatio:String="9:16"
    ):VideoPerception {
        val total=clips.sumOf {clip->
            SpeedMap(clip.trim.duration,project.motions[clip.id]?.speed ?: emptyList()).outputDuration
        }.coerceAtLeast(1L)
        val length=when {
            total<=15_000L->VideoLengthKind.MICRO
            total<=90_000L->VideoLengthKind.SHORT
            total<=300_000L->VideoLengthKind.STANDARD
            else->VideoLengthKind.LONG
        }
        val spoken=captions?.cues.orEmpty().sumOf {cue->
            (cue.endMs-cue.startMs).coerceAtLeast(0)
        }.coerceAtMost(total)
        val speechRatio=(spoken.toDouble()/total).toFloat().coerceIn(0f,1f)
        val beatCount=project.markers.count {it.label.equals("Beat",true)}
        val minutes=(total/60_000.0).coerceAtLeast(.05)
        val bpm=(beatCount/minutes).toFloat()
        val content=when {
            speechRatio>=.30f && bpm>=18f->VideoContentKind.MIXED
            speechRatio>=.30f->VideoContentKind.TALK
            beatCount>=3 && bpm>=18f->VideoContentKind.RHYTHMIC
            else->VideoContentKind.VISUAL
        }
        val effectBudget=when(length){
            VideoLengthKind.MICRO->4
            VideoLengthKind.SHORT->6
            VideoLengthKind.STANDARD->4
            VideoLengthKind.LONG->2
        }.let {base->if(content==VideoContentKind.TALK)minOf(base,2) else base}
        val frameBudget=when(length){
            VideoLengthKind.MICRO->4
            VideoLengthKind.SHORT->6
            VideoLengthKind.STANDARD->8
            VideoLengthKind.LONG->10
        }
        val ratio=currentRatio.takeIf {it in setOf("9:16","16:9","1:1","4:5")}
            ?: if(length==VideoLengthKind.MICRO || length==VideoLengthKind.SHORT)"9:16" else "16:9"
        return VideoPerception(total,length,content,speechRatio,bpm,effectBudget,frameBudget,ratio)
    }
}
