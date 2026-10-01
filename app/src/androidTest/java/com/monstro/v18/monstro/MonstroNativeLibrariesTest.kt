package com.monstro.v18.monstro

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sun.jna.Native
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.LibVosk
import org.vosk.LogLevel

@RunWith(AndroidJUnit4::class)
class MonstroNativeLibrariesTest {
    @Test
    fun offlineSpeechNativeLibrariesLoadWithoutDownloadingModel() {
        assertTrue(Native.POINTER_SIZE == 4 || Native.POINTER_SIZE == 8)
        // Exercises JNA dispatch into Vosk, including native symbol resolution.
        LibVosk.setLogLevel(LogLevel.INFO)
    }
}
