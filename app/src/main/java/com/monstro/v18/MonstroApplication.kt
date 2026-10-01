package com.monstro.v18

import android.app.Application
import com.monstro.v18.clearcut.CrashRecordStore

/** Records bounded local diagnostics before delegating to Android's crash handler. */
class MonstroApplication:Application() {
    override fun onCreate() {
        super.onCreate()
        CrashRecordStore(this).installGlobalHandler("18.6-Studio")
    }
}
