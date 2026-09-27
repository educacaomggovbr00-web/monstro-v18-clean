package com.monstro.v18

import org.junit.Assert.*
import org.junit.Test

class TrimRangeTest {
    @Test fun splitUsesPositionRelativeToTrimmedPreview() {
        val (left, right) = TrimRange(3000, 10000).split(2000)!!
        assertEquals(TrimRange(3000, 5000), left)
        assertEquals(TrimRange(5000, 10000), right)
        assertEquals(7000L, left.duration + right.duration)
    }
    @Test fun splitRejectsEndpointsAndOutOfBounds() {
        val trim = TrimRange(3000, 10000)
        listOf(-1L, 0L, 7000L, 8000L).forEach { assertNull(trim.split(it)) }
    }
    @Test(expected = IllegalArgumentException::class) fun emptyRangeIsInvalid() { TrimRange(1000, 1000) }
    @Test(expected = IllegalArgumentException::class) fun negativeStartIsInvalid() { TrimRange(-1, 1000) }
    @Test fun oneMillisecondClipRemainsValid() { assertEquals(1L, TrimRange(3, 4).duration) }
}
