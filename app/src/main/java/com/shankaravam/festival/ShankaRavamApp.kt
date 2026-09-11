package com.shankaravam.festival

import android.app.Application
import com.shankaravam.festival.di.AppContainer

/**
 * Application shell. Owns the manual [AppContainer]; G4 adds the TTS engine here.
 */
class ShankaRavamApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
