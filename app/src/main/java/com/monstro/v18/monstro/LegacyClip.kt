package com.monstro.v18.monstro

import java.util.UUID

data class TrimRange(val start: Long, val end: Long) {
    init { require(start >= 0 && end > start) }
    val duration: Long get() = end - start
    fun split(offset: Long): Pair<TrimRange, TrimRange>? {
        if (offset <= 0 || offset >= duration) return null
        return TrimRange(start, start + offset) to TrimRange(start + offset, end)
    }
}

data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    val duration: Long,
    val trim: TrimRange = TrimRange(0, duration),
    val preset: String = "raw",
    val chaos: ChaosSettings = ChaosSettings(),
    val volume: Float = 1f,
    val mirror: Boolean = false
)

