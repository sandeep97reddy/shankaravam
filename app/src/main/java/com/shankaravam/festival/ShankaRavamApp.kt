package com.shankaravam.festival

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.shankaravam.festival.di.AppContainer

/**
 * Application shell. Owns the manual [AppContainer]; F3 drives the foreground
 * sync manager from the app lifecycle (ledger listeners attach while any
 * screen is visible, detach when backgrounded).
 */
class ShankaRavamApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        runCatching {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> container.foregroundSync.onAppForeground()
                        Lifecycle.Event.ON_STOP -> container.foregroundSync.onAppBackground()
                        else -> Unit
                    }
                }
            )
        }
    }
}
