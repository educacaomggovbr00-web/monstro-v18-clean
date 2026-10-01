package com.monstro.v18

import org.junit.Assert.*
import org.junit.Test

class SubtitleExportTest {
    @Test fun srtKeepsHoursMillisecondsAndCueOrder() {
        val track=SrtTrack(listOf(SrtCue(3600123,3601999,"Olá"),SrtCue(0,1000,"Início")))
        val result=SubtitleExport.srt(track)
        assertTrue(result.startsWith("1\n00:00:00,000"))
        assertTrue(result.contains("01:00:00,123 --> 01:00:01,999"))
    }
    @Test fun vttEscapesMarkupAndKeepsWordTiming() {
        val track=SrtTrack(listOf(SrtCue(0,2000,"<a> &",listOf(WordTime(100,700),WordTime(1000,1700)))))
        val vtt=SubtitleExport.vtt(track)
        assertTrue(vtt.startsWith("WEBVTT\n\n"))
        assertTrue(vtt.contains("<00:00:00.100><c>&lt;a&gt;</c>"))
        assertTrue(vtt.contains("<00:00:01.000><c>&amp;</c>"))
    }
    @Test fun assTextCannotInjectOverrideCommands() {
        val ass=SubtitleExport.ass(SrtTrack(listOf(SrtCue(0,1000,"{\\pos(0,0)}\nOlá"))),TextStyle())
        assertTrue(ass.contains("\\{\\\\pos(0,0)\\}\\NOlá"))
        assertTrue(ass.contains("[V4+ Styles]"))
    }
}
