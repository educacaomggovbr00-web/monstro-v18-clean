package com.monstro.v18.monstro

import android.net.Uri
import com.monstro.v18.engine.AutoSaveState
import com.monstro.v18.engine.ProjectDocument
import com.monstro.v18.model.*
import com.monstro.v18.model.TimelineMarker as NewMarker
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Loss-aware conversion; the originals are never changed or deleted. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
object MonstroProjectConverter {
    /** Recover the last persisted editor state without changing the original document. */
    fun recoverActiveDocument(
        document: JSONObject,
        activeId: String?,
        journal: Map<String, *>,
        captions: JSONArray?,
    ): JSONObject {
        if (activeId != document.optString("id") || document.optBoolean("trashed") ||
            !journal.containsKey("clips")) return document
        val recovered = JSONObject(document.toString())
        val settings = recovered.getJSONObject("settings")
        journal.forEach { (key, value) ->
            if (key != "activeProjectId" && value != null) settings.put(key, value)
        }
        captions?.let { recovered.put("captions", JSONArray(it.toString())) }
        return recovered
    }

    fun convert(document:JSONObject):ProjectDocument {
        require(document.optInt("version",1)==1)
        val settings=document.getJSONObject("settings")
        val studio=StudioCodec.decode(settings.optString("studio","{}"))
        val id=UUID.nameUUIDFromBytes(("monstro:"+document.getString("id")).toByteArray()).toString()
        val all=JSONArray(settings.optString("clips","[]"))
        require(all.length()<=500)
        var cursor=0L
        val clips=(0 until all.length()).map {index->
            val c=all.getJSONObject(index);val oldId=c.getString("id")
            val duration=c.getLong("duration");val start=c.getLong("start");val end=c.getLong("end")
            require(duration in 1..86_400_000L && start>=0 && end>start && end<=duration)
            val motion=studio.motions[oldId]
            val speedCurve=motion?.speed?.takeIf{it.size>=2}?.let {keys->SpeedCurve(points=keys.map{SpeedPoint(position=(it.time.toFloat()/(end-start)).coerceIn(0f,1f),speed=it.value.coerceIn(.25f,4f))})}
            val params=buildMap<String,Float> {
                c.optJSONArray("fx")?.let {a->for(j in 0 until a.length())put(a.getString(j),1f)}
                put("zoom",c.optDouble("zoom",1.0).toFloat().coerceIn(1f,3f))
            }
            val effects=mutableListOf<com.monstro.v18.model.Effect>()
            if(params.size>1 || params.getValue("zoom")>1f)effects+=com.monstro.v18.model.Effect(type=EffectType.MONSTRO_CHAOS,params=params)
            val colors=listOf("neon","trap","dark","cinema")
            colors.indexOf(c.optString("preset")).takeIf{it>=0}?.let {effects+=com.monstro.v18.model.Effect(type=EffectType.MONSTRO_COLOR,params=mapOf("preset" to it.toFloat()))}
            studio.adjustments[oldId]?.let {a->
                effects+=com.monstro.v18.model.Effect(type=EffectType.BRIGHTNESS,params=mapOf("value" to a.brightness))
                effects+=com.monstro.v18.model.Effect(type=EffectType.CONTRAST,params=mapOf("value" to a.contrast+1f))
                effects+=com.monstro.v18.model.Effect(type=EffectType.SATURATION,params=mapOf("value" to a.saturation/100f+1f))
                effects+=com.monstro.v18.model.Effect(type=EffectType.TEMPERATURE,params=mapOf("value" to a.temperature*1.8f))
            }
            val base=Clip(id=oldId,sourceUri=Uri.parse(c.getString("uri")),sourceDurationMs=duration,
                timelineStartMs=cursor,trimStartMs=start,trimEndMs=end,name=c.optString("name"),
                volume=c.optDouble("volume",1.0).toFloat().coerceIn(0f,2f),flipHorizontal=c.optBoolean("mirror"),
                speedCurve=speedCurve,effects=effects)
            studio.fx.filter{it.start<cursor+base.durationMs && it.end>cursor}.forEach {f->
                val p=FxCatalog.get(f.presetId,studio.customFx) ?: return@forEach
                effects+=MonstroEffectBridge.preset(p).copy(params=MonstroEffectBridge.preset(p).params+mapOf(
                    "start" to (f.start-cursor).coerceAtLeast(0).toFloat(),"end" to (f.end-cursor).coerceAtMost(base.durationMs).toFloat(),"intensity" to f.intensity,"speed" to f.speed,"direction" to f.direction))
            }
            val keys=buildList {motion?.let {m->
                fun add(values:List<KeyPoint>,property:KeyframeProperty){values.forEach{add(Keyframe(it.time,property,it.value,interpolation=KeyframeInterpolation.LINEAR))}}
                add(m.zoom,KeyframeProperty.SCALE_X);add(m.zoom,KeyframeProperty.SCALE_Y);add(m.rotation,KeyframeProperty.ROTATION);add(m.x,KeyframeProperty.POSITION_X);add(m.y,KeyframeProperty.POSITION_Y)
            }}
            val captions=document.optJSONArray("captions") ?: JSONArray()
            val localCaptions=(0 until captions.length()).mapNotNull {i->
                val q=captions.getJSONObject(i);val qs=q.getLong("start");val qe=q.getLong("end")
                if(qs>=cursor+base.durationMs || qe<=cursor)null else {
                    val text=q.getString("text");val words=text.split(Regex("\\s+"));val times=q.optJSONArray("words")
                    Caption(text=text,startTimeMs=(qs-cursor).coerceAtLeast(0),endTimeMs=(qe-cursor).coerceAtMost(base.durationMs),
                        words=if(times==null)emptyList()else(0 until minOf(words.size,times.length())).map{j->val w=times.getJSONArray(j);CaptionWord(words[j],(w.getLong(0)-cursor).coerceAtLeast(0),(w.getLong(1)-cursor).coerceAtLeast(0))},
                        style=CaptionStyle(type=CaptionStyleType.KARAOKE,highlightColor=0xFFB46AFF))
                }
            }
            base.copy(effects=effects.toList(),keyframes=keys,captions=localCaptions).also {cursor+=it.durationMs}
        }
        val tracks=mutableListOf(Track(type=TrackType.VIDEO,index=0,clips=clips,isMuted=settings.optBoolean("mute")))
        if(studio.audio.isNotEmpty())tracks+=Track(type=TrackType.AUDIO,index=1,clips=studio.audio.map{a->
            Clip(id=a.id,sourceUri=Uri.parse(a.uri),sourceDurationMs=a.duration,timelineStartMs=a.start,trimStartMs=a.trimStart,trimEndMs=a.trimEnd,volume=a.volume.coerceIn(0f,2f),fadeInMs=a.fadeIn,fadeOutMs=a.fadeOut,name=a.name)})
        fun style(t:TextLayer):TextOverlay {val s=t.style;return TextOverlay(id=t.id,text=t.text.ifBlank{" "},startTimeMs=t.start,endTimeMs=t.end,fontSize=(s.size*1080f).coerceAtLeast(1f),fontFamily=s.font,color=s.color.toLong() and 0xFFFFFFFFL,strokeWidth=s.stroke*1080f,positionX=s.x,positionY=s.y,shadowBlur=s.shadow*1080f,glowRadius=s.glow*1080f,
            animationIn=when(s.animation){"Pop"->TextAnimation.BOUNCE;"Fade"->TextAnimation.FADE;"Digitar"->TextAnimation.TYPEWRITER;else->TextAnimation.NONE})}
        val texts=studio.texts.map(::style)
        val images=studio.images.filter{it.path.isNotBlank() && it.end>it.start}.map{i->ImageOverlay(id=i.id,sourceUri=Uri.fromFile(java.io.File(i.path)),startTimeMs=i.start,endTimeMs=i.end,positionX=i.x,positionY=i.y,scale=i.scale.coerceAtLeast(.01f),rotation=i.rotation,opacity=i.opacity.coerceIn(0f,1f))}
        val aspect=AspectRatio.entries.firstOrNull{it.label==settings.optString("canvasRatio","9:16")} ?: AspectRatio.RATIO_9_16
        val project=Project(id=id,name=document.optString("name","Projeto Monstro"),aspectRatio=aspect,frameRate=settings.optInt("exportFps",30),durationMs=cursor,deletedAtEpochMs=if(document.optBoolean("trashed"))System.currentTimeMillis() else null,
            notes="Migrado do Monstro 18.6. O documento original está preservado em files/projects. Revise composição e tempos antes de exportar.")
        return ProjectDocument(project,AutoSaveState(projectId=id,tracks=tracks,textOverlays=texts,imageOverlays=images,timelineMarkers=studio.markers.map{NewMarker(id=it.id,timeMs=it.time,label=it.label)}))
    }
}
