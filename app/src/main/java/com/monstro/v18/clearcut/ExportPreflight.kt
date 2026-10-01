package com.monstro.v18.clearcut

/** Port of ClearCut's bounded storage/codec preflight for Monstro's MP4 path. */
object ExportPreflight {
    fun requiredBytes(durationMs: Long, videoBitrate: Int): Long {
        require(MediaDurationPolicy.isPlausible(durationMs) && videoBitrate > 0)
        val payload = durationMs * (videoBitrate.toLong() + 192_000L) / 8_000L
        return payload + payload / 4L + 32L * 1024 * 1024
    }

    fun checkStorage(durationMs: Long, videoBitrate: Int, availableBytes: Long) {
        val required = requiredBytes(durationMs, videoBitrate)
        require(availableBytes >= required) {
            "Espaço insuficiente: libere pelo menos ${(required - availableBytes + 1048575) / 1048576} MB antes de exportar."
        }
    }
}
