package com.durgamma.festival

import android.app.Application
import com.durgamma.festival.di.AppContainer

/**
 * Application shell. Owns the manual [AppContainer]; G4 adds the TTS engine here.
 */
class DurgammaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
