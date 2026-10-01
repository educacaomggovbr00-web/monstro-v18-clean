package com.monstro.v18.monstro

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix

/** RGB only. Media3's input sampler performs YUV/range/transfer conversion using ColorInfo.
 * Do NOT apply a second YUV matrix to an RGB texture, or force BT.601 onto BT.709/HDR.
 * RgbMatrix is applied in linear BT.709 (SDR) / BT.2020 (HDR) by DefaultShaderProgram.
 * Diagonal gains preserve channel order and avoid negative contrast offsets crushing colors.
 */
@UnstableApi
class ColorPreset(private val name: String) : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        val gains = when(name) {
            "neon" -> floatArrayOf(1.08f,.96f,1.12f)
            "trap" -> floatArrayOf(1.10f,.93f,1.02f)
            "dark" -> floatArrayOf(.60f,.65f,.74f)
            "cinema" -> floatArrayOf(1.02f,1f,.94f)
            else -> floatArrayOf(1f,1f,1f)
        }
        return floatArrayOf(gains[0],0f,0f,0f, 0f,gains[1],0f,0f, 0f,0f,gains[2],0f, 0f,0f,0f,1f)
    }
}
