package com.monstro.v18

import java.util.Locale

/** ClearCut subtitle export adapted to Monstro's caption and per-word timing model. */
object SubtitleExport {
    private fun time(ms:Long,separator:Char):String {
        val t=ms.coerceAtLeast(0)
        return String.format(Locale.US,"%02d:%02d:%02d%c%03d",t/3600000,t/60000%60,t/1000%60,separator,t%1000)
    }
    fun srt(track:SrtTrack):String=track.cues.sortedBy {it.startMs}.mapIndexed {i,c->
        "${i+1}\n${time(c.startMs,',')} --> ${time(c.endMs,',')}\n${c.text}\n"
    }.joinToString("\n")
    private fun vttText(text:String)=text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
    fun vtt(track:SrtTrack):String=buildString {
        append("WEBVTT\n\n")
        track.cues.sortedBy {it.startMs}.forEach {c->
            append("${time(c.startMs,'.')} --> ${time(c.endMs,'.')}\n")
            val valid=c.words.zip(c.wordTimes).filter {(_,w)->w.start in c.startMs until c.endMs && w.end>w.start}
            append(if(valid.size==c.words.size && valid.isNotEmpty()) valid.joinToString(" "){(word,w)->"<${time(w.start,'.')}><c>${vttText(word)}</c>"} else vttText(c.text))
            append("\n\n")
        }
    }
    private fun assTime(ms:Long):String {
        val t=ms.coerceAtLeast(0)
        return String.format(Locale.US,"%d:%02d:%02d.%02d",t/3600000,t/60000%60,t/1000%60,t%1000/10)
    }
    private fun assText(text:String)=text.replace("\\","\\\\").replace("{","\\{").replace("}","\\}")
        .replace("\r\n","\n").replace("\r","\n").replace("\n","\\N")
    fun ass(track:SrtTrack,style:TextStyle):String=buildString {
        append("[Script Info]\nTitle: Monstro V18\nScriptType: v4.00+\nPlayResX: 1920\nPlayResY: 1080\n\n")
        append("[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, OutlineColour, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
        val bgr=((style.color and 255) shl 16) or (style.color and 0xff00) or ((style.color ushr 16) and 255)
        val alpha=(255-(255*style.opacity.coerceIn(0f,1f)).toInt()) shl 24
        append(String.format(Locale.US,"Style: Monstro,Arial,%.1f,&H%08X,&H00000000,1,%.1f,%.1f,5,20,20,20,1\n\n",style.size*1080,alpha or bgr,style.stroke*1080,style.shadow*1080))
        append("[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
        track.cues.sortedBy {it.startMs}.forEach {c->
            append("Dialogue: 0,${assTime(c.startMs)},${assTime(c.endMs)},Monstro,,0,0,0,,")
            append("{\\pos(${(style.x*1920).toInt()},${(style.y*1080).toInt()})}${assText(c.text)}\n")
        }
    }
}
