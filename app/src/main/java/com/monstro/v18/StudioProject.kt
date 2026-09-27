package com.monstro.v18

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.*

data class KeyPoint(val time: Long, val value: Float)
fun animated(keys: List<KeyPoint>, time: Long, fallback: Float): Float {
    if (keys.isEmpty()) return fallback
    val sorted = keys.sortedBy { it.time }
    if (time <= sorted.first().time) return sorted.first().value
    val right = sorted.indexOfFirst { it.time > time }
    if (right < 0) return sorted.last().value
    val a = sorted[right-1]; val b = sorted[right]
    val t = (time-a.time).toFloat()/(b.time-a.time)
    return a.value + (b.value-a.value)*t
}
fun putKey(keys: List<KeyPoint>, time: Long, value: Float) = (keys.filter { abs(it.time-time)>30 } + KeyPoint(time,value)).sortedBy { it.time }

data class SpeedSlice(val sourceStart: Long, val sourceEnd: Long, val speed: Float, val outputStart: Long) {
    val outputDuration get() = ((sourceEnd-sourceStart)/speed).roundToLong().coerceAtLeast(1)
}
/** The same piecewise speed map drives playback, speech times and export. */
class SpeedMap(val duration: Long, val keys: List<KeyPoint>) {
    val slices: List<SpeedSlice> = buildList {
        if (keys.isEmpty()) { add(SpeedSlice(0,duration,1f,0)); return@buildList }
        val step = max(200L, (duration+119)/120)
        var start = 0L; var output = 0L
        while (start < duration) {
            val end = min(duration,start+step)
            val speed = animated(keys,(start+end)/2,1f).coerceIn(.25f,4f)
            val previous = lastOrNull()
            if (previous != null && abs(previous.speed-speed)<.001f) {
                removeAt(lastIndex); add(previous.copy(sourceEnd=end)); output=last().outputStart+last().outputDuration
            } else {
                val slice = SpeedSlice(start,end,speed,output); add(slice); output += slice.outputDuration
            }
            start=end
        }
    }
    val outputDuration get() = slices.lastOrNull()?.let { it.outputStart+it.outputDuration } ?: 0L
    fun toOutput(source: Long): Long { val s=slices.lastOrNull { source>=it.sourceStart } ?: return 0; return (s.outputStart+(source.coerceAtMost(duration)-s.sourceStart)/s.speed).roundToLong() }
    fun toSource(output: Long): Long { val s=slices.lastOrNull { output>=it.outputStart } ?: return 0; return (s.sourceStart+(output-s.outputStart)*s.speed).roundToLong().coerceIn(0,duration) }
    fun speedAt(source: Long) = slices.lastOrNull { source>=it.sourceStart }?.speed ?: 1f
}

data class TextStyle(
    val font: String="sans-serif-condensed", val size: Float=.11f, val color: Int=0xffc250ff.toInt(),
    val stroke: Float=.005f, val shadow: Float=.008f, val glow: Float=.02f,
    val x: Float=.5f, val y: Float=.72f, val animation: String="Pop", val opacity: Float=1f,
    val xKeys: List<KeyPoint> = emptyList(), val yKeys: List<KeyPoint> = emptyList(), val scaleKeys: List<KeyPoint> = emptyList()
)
data class TextLayer(val id:String=UUID.randomUUID().toString(),val text:String="MONSTRO",val start:Long=0,val end:Long=3000,val style:TextStyle=TextStyle())
data class AudioLayer(val id:String=UUID.randomUUID().toString(),val uri:String,val name:String,val duration:Long,val start:Long=0,val trimStart:Long=0,val trimEnd:Long=duration,val volume:Float=1f) { val end get()=start+trimEnd-trimStart }
data class FxLayer(val id:String=UUID.randomUUID().toString(),val presetId:String,val start:Long,val end:Long,val intensity:Float=1f,val speed:Float=1f,val direction:Float=0f,val keys:List<KeyPoint> = emptyList())
data class ClipMotion(val speed:List<KeyPoint> = emptyList(),val zoom:List<KeyPoint> = emptyList())
data class StudioProject(
    val texts:List<TextLayer> = emptyList(),val audio:List<AudioLayer> = emptyList(),val fx:List<FxLayer> = emptyList(),
    val motions:Map<String,ClipMotion> = emptyMap(), val captionStyle:TextStyle=TextStyle(),
    val captionStyles:Map<Int,TextStyle> = emptyMap(),val favorites:Set<String> = emptySet(),val recent:List<String> = emptyList()
)

private fun keysJson(keys:List<KeyPoint>)=JSONArray().also { a->keys.forEach { a.put(JSONArray().put(it.time).put(it.value.toDouble())) } }
private fun readKeys(a:JSONArray?)=if(a==null) emptyList() else (0 until a.length()).map { val k=a.getJSONArray(it); KeyPoint(k.getLong(0),k.getDouble(1).toFloat()) }
private fun TextStyle.json()=JSONObject().put("font",font).put("size",size.toDouble()).put("color",color).put("stroke",stroke.toDouble()).put("shadow",shadow.toDouble()).put("glow",glow.toDouble()).put("x",x.toDouble()).put("y",y.toDouble()).put("animation",animation).put("opacity",opacity.toDouble()).put("xk",keysJson(xKeys)).put("yk",keysJson(yKeys)).put("sk",keysJson(scaleKeys))
private fun style(o:JSONObject?)=if(o==null) TextStyle() else TextStyle(o.optString("font","sans-serif-condensed"),o.optDouble("size",.11).toFloat(),o.optInt("color",0xffc250ff.toInt()),o.optDouble("stroke",.005).toFloat(),o.optDouble("shadow",.008).toFloat(),o.optDouble("glow",.02).toFloat(),o.optDouble("x",.5).toFloat(),o.optDouble("y",.72).toFloat(),o.optString("animation","Pop"),o.optDouble("opacity",1.0).toFloat(),readKeys(o.optJSONArray("xk")),readKeys(o.optJSONArray("yk")),readKeys(o.optJSONArray("sk")))
private fun <T> JSONArray?.objects(f:(JSONObject)->T):List<T> = if(this==null) emptyList() else (0 until length()).map { f(getJSONObject(it)) }
object StudioCodec {
    fun encode(p:StudioProject):String = JSONObject().apply {
        put("texts",JSONArray().also { a->p.texts.forEach { a.put(JSONObject().put("id",it.id).put("text",it.text).put("start",it.start).put("end",it.end).put("style",it.style.json())) } })
        put("audio",JSONArray().also { a->p.audio.forEach { a.put(JSONObject().put("id",it.id).put("uri",it.uri).put("name",it.name).put("duration",it.duration).put("start",it.start).put("in",it.trimStart).put("out",it.trimEnd).put("volume",it.volume.toDouble())) } })
        put("fx",JSONArray().also { a->p.fx.forEach { a.put(JSONObject().put("id",it.id).put("preset",it.presetId).put("start",it.start).put("end",it.end).put("intensity",it.intensity.toDouble()).put("speed",it.speed.toDouble()).put("direction",it.direction.toDouble()).put("keys",keysJson(it.keys))) } })
        put("motions",JSONObject().also { o->p.motions.forEach { (id,m)->o.put(id,JSONObject().put("speed",keysJson(m.speed)).put("zoom",keysJson(m.zoom))) } })
        put("caption",p.captionStyle.json()); put("styles",JSONObject().also { o->p.captionStyles.forEach { (i,s)->o.put(i.toString(),s.json()) } })
        put("favorites",JSONArray(p.favorites.toList())); put("recent",JSONArray(p.recent))
    }.toString()
    fun decode(source:String):StudioProject {
        val o=JSONObject(source);val motions=mutableMapOf<String,ClipMotion>(); val styles=mutableMapOf<Int,TextStyle>()
        o.optJSONObject("motions")?.let { m->m.keys().forEach { id-> val j=m.getJSONObject(id); motions[id]=ClipMotion(readKeys(j.optJSONArray("speed")),readKeys(j.optJSONArray("zoom"))) } }
        o.optJSONObject("styles")?.let { m->m.keys().forEach { id->id.toIntOrNull()?.let { styles[it]=style(m.getJSONObject(id)) } } }
        fun strings(name:String)=o.optJSONArray(name)?.let { a->(0 until a.length()).map { a.getString(it) } } ?: emptyList()
        return StudioProject(o.optJSONArray("texts").objects { TextLayer(it.getString("id"),it.getString("text"),it.getLong("start"),it.getLong("end"),style(it.optJSONObject("style"))) },
            o.optJSONArray("audio").objects { AudioLayer(it.getString("id"),it.getString("uri"),it.getString("name"),it.getLong("duration"),it.getLong("start"),it.getLong("in"),it.getLong("out"),it.getDouble("volume").toFloat()) },
            o.optJSONArray("fx").objects { FxLayer(it.getString("id"),it.getString("preset"),it.getLong("start"),it.getLong("end"),it.getDouble("intensity").toFloat(),it.getDouble("speed").toFloat(),it.getDouble("direction").toFloat(),readKeys(it.optJSONArray("keys"))) },motions,style(o.optJSONObject("caption")),styles,strings("favorites").toSet(),strings("recent"))
    }
}
