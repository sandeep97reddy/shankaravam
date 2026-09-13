package com.shankaravam.festival.presentation.common

import android.content.Context
import android.content.Intent

private const val PKG_WHATSAPP = "com.whatsapp"
private const val PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

/**
 * Shares plain text through WhatsApp when installed (regular → Business),
 * else falls back to the system chooser. Native ACTION_SEND only — the app
 * itself makes zero network calls (feature #5, Rule #1). Never throws.
 *
 * P1 fix: launch WhatsApp directly when resolved (no empty chooser), with a
 * chooser fallback inside the same guard — a setPackage intent can still
 * resolve to nothing on odd OEM ROMs.
 */
fun shareTextViaWhatsApp(context: Context, text: String) {
    runCatching {
        val targeted = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val pm = context.packageManager
        // Needs the <package> entries in AndroidManifest <queries> on API 30+.
        val target = when {
            runCatching { pm.getPackageInfo(PKG_WHATSAPP, 0) }.isSuccess -> PKG_WHATSAPP
            runCatching { pm.getPackageInfo(PKG_WHATSAPP_BUSINESS, 0) }.isSuccess -> PKG_WHATSAPP_BUSINESS
            else -> null
        }
        // NEW_TASK only when we lack an Activity (application context off-screen):
        // with an Activity, omitting it keeps Back returning to our ledger.
        // createChooser does NOT inherit the inner intent's flags, so every
        // intent we actually start gets the flag explicitly.
        fun Intent.withTaskFlag(): Intent {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return this
        }
        targeted.withTaskFlag()
        if (target != null) {
            targeted.setPackage(target)
            runCatching { context.startActivity(targeted) }.onFailure {
                // Targeted launch failed (odd OEM ROM): retry fully untargeted
                // so the chooser can actually resolve something.
                val plain = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(plain, null).withTaskFlag())
            }
        } else {
            context.startActivity(Intent.createChooser(targeted, null).withTaskFlag())
        }
    }
}

/**
 * Shares a local audio file (.mp3) via FileProvider to WhatsApp or system chooser.
 */
fun shareAudioViaApps(context: Context, audioFile: java.io.File, title: String = "Announcement Audio") {
    runCatching {
        if (!audioFile.exists() || audioFile.length() == 0L) return
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            audioFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
