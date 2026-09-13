package com.shankaravam.festival.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * G6 Sarvam-key home. EncryptedSharedPreferences when the device allows it,
 * plain SessionPrefs otherwise — and a one-way migration of the G4 DEV key
 * the first time the secure store opens. Callers never touch prefs directly.
 */
class SecureKeyStore(context: Context, private val fallback: SessionPrefs) {

    private val secure: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        // Test read to ensure keys are decryptable and not corrupted from debug updates
        prefs.all
        migrateIfNeeded(prefs)
        prefs
    }.getOrElse {
        // If keystore was invalidated or corrupted, delete the stale prefs file
        runCatching {
            val file = java.io.File(context.applicationContext.filesDir.parent, "shared_prefs/$FILE.xml")
            if (file.exists()) file.delete()
        }
        null
    }

    val isEncrypted: Boolean get() = secure != null

    fun getSarvamKey(): String = runCatching {
        // Secure store is authoritative once it exists: migration already moved
        // any legacy plain key at init, so blank here means "no key" — never
        // fall back to the plain pref or a cleared key resurrects itself.
        if (secure != null) secure.getString(KEY_SARVAM, "") ?: ""
        else fallback.sarvamApiKey
    }.getOrDefault("")

    fun setSarvamKey(value: String) {
        val trimmed = value.trim()
        val saved = runCatching {
            secure?.edit()?.putString(KEY_SARVAM, trimmed)?.apply()
            secure != null
        }.getOrDefault(false)

        if (trimmed.isBlank()) {
            // Clearing must kill the legacy plain copy too, or get() resurrects it.
            fallback.sarvamApiKey = ""
        } else if (!saved) {
            fallback.sarvamApiKey = trimmed
        }
    }

    private fun migrateIfNeeded(store: SharedPreferences) {
        runCatching {
            val plain = fallback.sarvamApiKey
            if (plain.isNotBlank() && store.getString(KEY_SARVAM, "").isNullOrEmpty()) {
                store.edit().putString(KEY_SARVAM, plain).apply()
                fallback.sarvamApiKey = ""
            }
        }
    }

    companion object {
        private const val FILE = "shankaravam_secure"
        private const val KEY_SARVAM = "sarvam_api_key"
    }
}
