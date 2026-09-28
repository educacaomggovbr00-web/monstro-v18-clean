package com.monstro.v18

/**
 * Deterministic fallback used when Gemini is unavailable or quota-limited.
 * It uses duration, captions and beat markers so "Automático" still adapts
 * to short-form, talking videos and rhythmic edits without cloud calls.
 */
object LocalAutoEdit {
    fun build(
        clips:List<VideoClip>,
        project:StudioProject,
        style:String,
        captions:SrtTrack?=null,
        currentRatio:String="9:16"
    ):AiEditPlan {
        val profile=VideoPerceptionAnalyzer.analyze(clips,project,captions,currentRatio)
        val effectiveStyle=if(style=="Automático"){
            when(profile.contentKind){
                VideoContentKind.TALK->"Vlog / Conversa"
                VideoContentKind.RHYTHMIC,VideoContentKind.MIXED->"Trap / Música"
                VideoContentKind.VISUAL->if(profile.shortForm)"Anime / Edit" else "Cinemático"
            }
        } else style

        val total=profile.durationMs
        val preset=when(effectiveStyle){
            "Trap / Música"->"trap"
            "Cinemático"->"cinema"
            "Gameplay / Ação"->"neon"
            "Anime / Edit"->"neon"
            else->"raw"
        }
        val looks=clips.indices.map {i->
            when(effectiveStyle){
                "Cinemático"->AiClipLook(i,preset,0f,.10f,5f,.06f)
                "Trap / Música"->AiClipLook(i,preset,-.01f,.12f,10f,-.02f)
                "Gameplay / Ação"->AiClipLook(i,preset,.02f,.12f,9f,0f)
                "Anime / Edit"->AiClipLook(i,preset,.01f,.14f,12f,.01f)
                else->AiClipLook(i,preset,0f,.04f,2f,0f)
            }
        }

        val motions=mutableListOf<AiMotionKey>()
        clips.forEachIndexed {i,clip->
            val d=clip.trim.duration.coerceAtLeast(1)
            when(effectiveStyle){
                "Vlog / Conversa"->{
                    motions+=AiMotionKey(i,0,1f,0f,0f,0f)
                    if(profile.shortForm)motions+=AiMotionKey(i,d/2,1.04f,0f,-.008f,0f)
                }
                "Trap / Música","Anime / Edit","Gameplay / Ação"->{
                    motions+=AiMotionKey(i,0,1f,0f,0f,0f)
                    motions+=AiMotionKey(i,d/2,if(profile.shortForm)1.08f else 1.04f,.008f,0f,if(effectiveStyle=="Anime / Edit")0.8f else 0f)
                    if(profile.shortForm)motions+=AiMotionKey(i,(d-1).coerceAtLeast(0),1f,0f,0f,0f)
                }
                "Cinemático"->{
                    motions+=AiMotionKey(i,0,1.01f,-.01f,0f,0f)
                    motions+=AiMotionKey(i,(d-1).coerceAtLeast(0),1.05f,.01f,0f,0f)
                }
            }
        }

        val beats=project.markers.filter {it.label.equals("Beat",true)}.map {it.time}.sorted()
        val category=when(effectiveStyle){
            "Trap / Música"->"Trap"
            "Gameplay / Ação"->"Shake"
            "Anime / Edit"->"RGB"
            "Cinemático"->"Cinematic"
            else->"Light"
        }

        val allowEffects=profile.contentKind!=VideoContentKind.TALK || effectiveStyle!="Vlog / Conversa"
        val effects=if(!allowEffects)emptyList() else beats.take(profile.effectBudget).mapIndexedNotNull {index,time->
            val start=(time-70).coerceAtLeast(0)
            val end=(time+150).coerceAtMost(total)
            if(end<=start)null else AiFxSuggestion(start,end,category,if(profile.shortForm).50f else .32f,index)
        }

        val markers=beats.take(16).mapIndexed {i,time->AiMarker(time,"Beat "+(i+1))}
        return AiEditPlan(
            summary=profile.label+" · edição local limpa",
            ratio=profile.recommendedRatio,
            looks=looks,
            motions=motions,
            speeds=emptyList(),
            effects=effects,
            markers=markers
        )
    }
}
