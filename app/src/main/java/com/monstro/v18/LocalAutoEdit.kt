package com.monstro.v18

/**
 * Deterministic fallback used when Gemini is unavailable or quota-limited.
 * It never invents semantic understanding: it uses requested style, clip lengths
 * and existing Beat markers to keep Auto Edit functional offline.
 */
object LocalAutoEdit {
    fun build(clips:List<VideoClip>,project:StudioProject,style:String):AiEditPlan {
        val total=clips.sumOf {clip->SpeedMap(clip.trim.duration,project.motions[clip.id]?.speed ?: emptyList()).outputDuration}
        val preset=when(style){
            "Trap / Música"->"trap"
            "Cinemático"->"cinema"
            "Gameplay / Ação"->"neon"
            "Anime / Edit"->"neon"
            else->"raw"
        }
        val looks=clips.indices.map {i->
            when(style){
                "Cinemático"->AiClipLook(i,preset,0f,.12f,6f,.08f)
                "Trap / Música"->AiClipLook(i,preset,-.02f,.16f,14f,-.03f)
                "Gameplay / Ação"->AiClipLook(i,preset,.03f,.14f,12f,0f)
                "Anime / Edit"->AiClipLook(i,preset,.02f,.18f,18f,.02f)
                else->AiClipLook(i,preset,0f,.05f,3f,0f)
            }
        }
        val motions=mutableListOf<AiMotionKey>()
        clips.forEachIndexed {i,clip->
            val d=clip.trim.duration.coerceAtLeast(1)
            when(style){
                "Vlog / Conversa"->{
                    motions+=AiMotionKey(i,0,1f,0f,0f,0f)
                    motions+=AiMotionKey(i,d/2,1.06f,0f,-.01f,0f)
                }
                "Trap / Música","Anime / Edit","Gameplay / Ação"->{
                    motions+=AiMotionKey(i,0,1f,0f,0f,0f)
                    motions+=AiMotionKey(i,d/2,1.10f,.01f,0f,if(style=="Anime / Edit")1.2f else 0f)
                    motions+=AiMotionKey(i,(d-1).coerceAtLeast(0),1f,0f,0f,0f)
                }
                "Cinemático"->{
                    motions+=AiMotionKey(i,0,1.02f,-.015f,0f,0f)
                    motions+=AiMotionKey(i,(d-1).coerceAtLeast(0),1.08f,.015f,0f,0f)
                }
            }
        }
        val beats=project.markers.filter {it.label.equals("Beat",true)}.map {it.time}.sorted()
        val category=when(style){
            "Trap / Música"->"Trap"
            "Gameplay / Ação"->"Shake"
            "Anime / Edit"->"Anime"
            "Cinemático"->"Cinematic"
            else->"Light"
        }
        val effects=beats.take(10).mapIndexedNotNull {index,time->
            val start=(time-90).coerceAtLeast(0)
            val end=(time+180).coerceAtMost(total)
            if(end<=start)null else AiFxSuggestion(start,end,category,if(style=="Vlog / Conversa").35f else .65f,index%20)
        }
        val markers=beats.take(16).mapIndexed {i,time->AiMarker(time,"Beat ${i+1}")}
        return AiEditPlan(
            summary="Edição local por ritmo e estilo",
            ratio="9:16",
            looks=looks,
            motions=motions,
            speeds=emptyList(),
            effects=effects,
            markers=markers
        )
    }
}
