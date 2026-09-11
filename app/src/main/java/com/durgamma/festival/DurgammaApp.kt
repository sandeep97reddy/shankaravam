package com.durgamma.festival

import android.app.Application

/**
 * G1 application shell. Holds no singletons yet — G2 adds Room database,
 * G4 adds TTS engine. Kept so the manifest has a stable entry point.
 */
class DurgammaApp : Application()
