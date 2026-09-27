package com.monstro.v18

import kotlin.math.floor

data class SrtCue(val startMs: Long, val endMs: Long, val text: String) {
    val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
    fun wordAt(timeMs: Long): Int = floor((timeMs-startMs).toDouble() * words.size / (endMs-startMs)).toInt().coerceIn(0, words.lastIndex)
    fun wordStart(index: Int): Long = startMs + (endMs-startMs) * index / words.size
}
data class SrtParseResult(val track: SrtTrack, val skipped: Int)
class SrtTrack(val cues: List<SrtCue>) {
    private val maxEnds = cues.runningFold(0L) { end, cue -> maxOf(end, cue.endMs) }
    fun at(timeMs: Long): SrtCue? {
        var lo = 0; var hi = cues.size
        while (lo < hi) { val mid = (lo+hi)/2; if (cues[mid].startMs <= timeMs) lo = mid+1 else hi = mid }
        var i = lo-1
        while (i >= 0 && maxEnds[i+1] > timeMs) {
            if (timeMs < cues[i].endMs) return cues[i]
            i--
        }
        return null
    }
}
object SrtParser {
    private val timing = Regex("^(\\d{1,3}):([0-5]\\d):([0-5]\\d)[,.](\\d{3})\\s*-->\\s*(\\d{1,3}):([0-5]\\d):([0-5]\\d)[,.](\\d{3})(?:\\s+.*)?$")
    fun parse(source: String): SrtParseResult {
        require(source.length <= 2_000_000) { "SRT muito grande (máximo 2 MB)." }
        var skipped = 0
        val cues = source.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r','\n').trim()
            .split(Regex("\n[ \\t]*\n+")).mapNotNull { block ->
                val lines = block.lines().map { it.trim() }
                val index = if (lines.firstOrNull()?.matches(Regex("\\d+")) == true) 1 else 0
                val match = lines.getOrNull(index)?.let { timing.matchEntire(it) }
                if (match == null) { if (block.isNotBlank()) skipped++; return@mapNotNull null }
                fun ms(i: Int) = match.groupValues[i].toLong()*3600000 + match.groupValues[i+1].toLong()*60000 + match.groupValues[i+2].toLong()*1000 + match.groupValues[i+3].toLong()
                val start = ms(1); val end = ms(5)
                val text = lines.drop(index+1).joinToString(" ").replace(Regex("<[^>]*>"), "").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").trim()
                if (end <= start || text.isBlank() || text.length > 2000) { skipped++; null } else SrtCue(start,end,text)
            }.sortedBy { it.startMs }
        require(cues.isNotEmpty()) { "Nenhuma legenda válida encontrada." }
        require(cues.size <= 5000) { "Máximo de 5.000 frases por arquivo." }
        return SrtParseResult(SrtTrack(cues), skipped)
    }
}
