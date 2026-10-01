package com.monstro.v18.clearcut

import org.junit.Assert.*
import org.junit.Test

class ExportPreflightTest {
    @Test fun storageIncludesAudioAndFinalizationReserve() {
        val payload=60_000L*(2_800_000L+192_000L)/8000
        assertTrue(ExportPreflight.requiredBytes(60_000,2_800_000)>payload)
        assertThrows(IllegalArgumentException::class.java){ExportPreflight.checkStorage(60_000,2_800_000,1)}
        ExportPreflight.checkStorage(60_000,2_800_000,Long.MAX_VALUE)
    }
    @Test fun hostileDurationCannotOverflowStorageEstimate() {
        assertThrows(IllegalArgumentException::class.java){ExportPreflight.requiredBytes(Long.MAX_VALUE,16_000_000)}
        assertThrows(IllegalArgumentException::class.java){ExportPreflight.requiredBytes(0,16_000_000)}
    }
}
