package com.anish1050.hires

import android.app.Application
import android.os.StrictMode

class HiResApp : Application() {

    // Built lazily so Application.onCreate stays empty and cold start stays short.
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build(),
            )
        }
    }
}
