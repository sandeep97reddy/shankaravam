package com.shankaravam.festival.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private fun sanitizeKey(raw: String): String =
        raw.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()

    private fun readKey(): String = runCatching {
        val sec = if (secure != null) secure.getString(KEY_SARVAM, "") ?: "" else ""
        if (sec.isNotBlank()) sec else fallback.sarvamApiKey
    }.getOrDefault("")

    private val _sarvamKeyFlow = MutableStateFlow(readKey())
    val sarvamKeyFlow: StateFlow<String> = _sarvamKeyFlow.asStateFlow()

    private val _hasKeyFlow = MutableStateFlow(readKey().isNotBlank())
    val hasKeyFlow: StateFlow<Boolean> = _hasKeyFlow.asStateFlow()

    /**
     * Phase 2 reactivity (RC1 remedy). Bumped on every [setSarvamKey] so voice
     * pickers can reload key presence instead of reading it once into
     * `remember {}` and going stale when another screen saves/clears the key.
     */
    private val _keyVersion = MutableStateFlow(0)
    val keyVersion: StateFlow<Int> = _keyVersion.asStateFlow()

    fun getSarvamKey(): String = _sarvamKeyFlow.value.ifBlank { readKey() }

    fun setSarvamKey(value: String) {
        val clean = sanitizeKey(value)
        // Spend guard: identical writes must not bump keyVersion — it
        // re-runs the announcement prefetch pass (worker invocations) and
        // reloads voice pickers for zero change.
        if (clean == _sarvamKeyFlow.value) return
        runCatching {
            secure?.edit()?.putString(KEY_SARVAM, clean)?.commit()
        }
        // Always mirror to fallback SessionPrefs for resilience against keystore wipe/resets
        fallback.sarvamApiKey = clean

        _sarvamKeyFlow.value = clean
        _hasKeyFlow.value = clean.isNotBlank()
        _keyVersion.value += 1
    }

    private fun migrateIfNeeded(store: SharedPreferences) {
        runCatching {
            val plain = fallback.sarvamApiKey
            if (plain.isNotBlank() && store.getString(KEY_SARVAM, "").isNullOrEmpty()) {
                store.edit().putString(KEY_SARVAM, plain).commit()
            }
        }
    }

    companion object {
        private const val FILE = "shankaravam_secure"
        private const val KEY_SARVAM = "sarvam_api_key"
    }
}
