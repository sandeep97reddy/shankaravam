package com.shankaravam.festival.core.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioRoute { SPEAKER, BLUETOOTH, WIRED }

fun AudioRoute.displayName(): String = when (this) {
    AudioRoute.SPEAKER -> "Phone Speaker"
    AudioRoute.BLUETOOTH -> "Bluetooth Amplifier/Device"
    AudioRoute.WIRED -> "Wired Headset"
}

/**
 * Live output-route badge (plan §11). No Bluetooth permissions needed —
 * plain A2DP routing via AudioManager; disconnects fall back to speaker.
 */
class AudioRouteDetector(context: Context) {

    private val audioManager: AudioManager? = runCatching {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }.getOrNull()

    private val _route = MutableStateFlow(currentRoute())
    val route: StateFlow<AudioRoute> = _route.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            _route.value = currentRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            _route.value = currentRoute()
        }
    }

    fun start() {
        runCatching {
            audioManager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }
        _route.value = currentRoute()
    }

    fun stop() {
        runCatching {
            audioManager?.unregisterAudioDeviceCallback(callback)
        }
    }

    private fun currentRoute(): AudioRoute {
        return runCatching {
            val am = audioManager ?: return AudioRoute.SPEAKER
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
            when {
                outputs.any { it in BLUETOOTH_TYPES } -> AudioRoute.BLUETOOTH
                outputs.any { it in WIRED_TYPES } -> AudioRoute.WIRED
                else -> AudioRoute.SPEAKER
            }
        }.getOrDefault(AudioRoute.SPEAKER)
    }

    companion object {
        // Raw int constants to avoid NoSuchFieldError on Android < 12 (API < 31)
        private const val TYPE_BLUETOOTH_SCO_INT = 7
        private const val TYPE_BLUETOOTH_A2DP_INT = 8
        private const val TYPE_HEARING_AID_INT = 23 // API 28+
        private const val TYPE_BLE_HEADSET_INT = 26 // API 31+
        private const val TYPE_BLE_SPEAKER_INT = 27 // API 31+
        private const val TYPE_BLE_BROADCAST_INT = 30 // API 33+

        private val BLUETOOTH_TYPES = setOf(
            TYPE_BLUETOOTH_SCO_INT,
            TYPE_BLUETOOTH_A2DP_INT,
            TYPE_HEARING_AID_INT,
            TYPE_BLE_HEADSET_INT,
            TYPE_BLE_SPEAKER_INT,
            TYPE_BLE_BROADCAST_INT
        )

        private val WIRED_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE
        )
    }
}
