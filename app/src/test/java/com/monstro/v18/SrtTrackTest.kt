package com.monstro.v18

import org.junit.Assert.*
import org.junit.Test

class SrtTrackTest {
    @Test fun bomCrLfMultilineAndHalfOpenBoundaries() {
        val result = SrtParser.parse("\uFEFF1\r\n00:00:01,000 --> 00:00:03,000\r\n<b>Olá</b>\r\nmundo\r\n\r\n2\r\n00:00:03,000 --> 00:00:04,000\r\nFim")
        assertNull(result.track.at(999))
        assertEquals("Olá mundo",result.track.at(1000)!!.text)
        assertEquals(0,result.track.at(1999)!!.wordAt(1999))
        assertEquals(1,result.track.at(2000)!!.wordAt(2000))
        assertEquals("Fim",result.track.at(3000)!!.text)
        assertNull(result.track.at(4000))
    }
    @Test fun sortedOverlapAndMalformedBlocks() {
        val result = SrtParser.parse("2\n00:00:02.000 --> 00:00:03.000\ncurta\n\n1\n00:00:00,000 --> 00:00:05,000\nlonga\n\n3\n00:00:05,000 --> 00:00:04,000\ninválida")
        assertEquals(1,result.skipped)
        assertEquals("curta",result.track.at(2500)!!.text)
        assertEquals("longa",result.track.at(3500)!!.text)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyFile() { SrtParser.parse("isto não é SRT") }
    @Test fun exportSizesAreExactAndEven() {
        for(light in listOf(true,false)) for(vertical in listOf(true,false)) {
            val f = ExportFormat(vertical,light)
            assertEquals(0,f.width%2); assertEquals(0,f.height%2)
            assertEquals(if(vertical) 9f/16 else 16f/9,f.width.toFloat()/f.height,.00001f)
        }
        assertEquals(1080,ExportFormat(true,false).width)
        assertEquals(1920,ExportFormat(true,false).height)
    }
}
