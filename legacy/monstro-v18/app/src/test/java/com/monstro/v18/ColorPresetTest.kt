package com.monstro.v18

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class ColorPresetTest {
    @Test fun presetsPreserveRgbChannelOrderAndBlackInSdrAndHdr() {
        for(hdr in listOf(false,true)) for(preset in listOf("raw","neon","trap","dark","cinema")) {
            val matrix = ColorPreset(preset).getMatrix(0,hdr)
            assertEquals(16,matrix.size)
            for(i in 0..15) {
                assertTrue(matrix[i].isFinite())
                if(i in listOf(0,5,10,15)) assertTrue(matrix[i]>0f) else assertEquals(0f,matrix[i],0f)
            }
            assertEquals(1f,matrix[15],0f)
        }
    }
}
