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
        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull().also { if (it != null) migrateIfNeeded(it) }

    val isEncrypted: Boolean get() = secure != null

    fun getSarvamKey(): String =
        secure?.getString(KEY_SARVAM, "") ?: fallback.sarvamApiKey

    fun setSarvamKey(value: String) {
        val trimmed = value.trim()
        if (secure != null) {
            secure.edit().putString(KEY_SARVAM, trimmed).apply()
        } else {
            fallback.sarvamApiKey = trimmed
        }
    }

    private fun migrateIfNeeded(store: SharedPreferences) {
        val plain = fallback.sarvamApiKey
        if (plain.isNotBlank() && store.getString(KEY_SARVAM, "").isNullOrEmpty()) {
            store.edit().putString(KEY_SARVAM, plain).apply()
            fallback.sarvamApiKey = ""
        }
    }

    companion object {
        private const val FILE = "shankaravam_secure"
        private const val KEY_SARVAM = "sarvam_api_key"
    }
}
