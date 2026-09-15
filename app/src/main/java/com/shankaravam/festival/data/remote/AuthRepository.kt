@file:Suppress("DEPRECATION") // GoogleSignIn classic API: still the stable intent flow.

package com.shankaravam.festival.data.remote

import android.content.Context
import android.content.Intent
import com.shankaravam.festival.core.util.Outcome
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.domain.model.AdminConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class CloudUser(val uid: String, val email: String?, val displayName: String?)

/**
 * Lazy Google Sign-In (plan §7, §20). Everything degrades to a clear
 * "not configured" outcome when google-services.json is absent — the app
 * never crashes, never blocks launch, never demands login (Rule #1).
 *
 * Classic sign-in-intent flow (play-services-auth): stable across Credential
 * Manager library moves, and the result handler stays unit-testable.
 */
class AuthRepository(
    private val appContext: Context,
    private val sessionPrefs: SessionPrefs
) {

    /** Null on builds without Firebase config — all calls below then no-op gracefully. */
    private val auth: FirebaseAuth? = runCatching { Firebase.auth }.getOrNull()

    val isConfigured: Boolean get() = auth != null

    private val _user = MutableStateFlow(auth?.currentUser?.toCloudUser())
    val user: StateFlow<CloudUser?> = _user.asStateFlow()

    /**
     * Sole owner of SessionPrefs.isGlobalHeadUser (ADMIN_HEAD_PLAN S2.4).
     * Fires immediately on register (covers cold start) and on every change
     * incl. token refresh (diffed write) and sign-out (null → false).
     * UI gating only — firestore.rules is the boundary.
     */
    private val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val cloud = firebaseAuth.currentUser?.toCloudUser()
        _user.value = cloud
        sessionPrefs.googleDisplayName = cloud?.displayName
        sessionPrefs.googleEmail = cloud?.email
        runCatching {
            val headsUp = AdminConfig.isGlobalHeadEmail(cloud?.email)
            if (sessionPrefs.isGlobalHeadUser != headsUp) {
                sessionPrefs.isGlobalHeadUser = headsUp
            }
        }
    }

    init {
        auth?.addAuthStateListener(listener)
    }

    /** Intent for ActivityResultContracts.StartActivityForResult, or Err when unconfigured. */
    fun googleSignInIntent(): Outcome<Intent> {
        if (auth == null) {
            return Outcome.Err("Cloud sync is not configured on this build yet (missing google-services.json).")
        }
        val webClientId = appContext.resources.getIdentifier(
            "default_web_client_id", "string", appContext.packageName
        ).takeIf { it != 0 }?.let { appContext.getString(it) }
            ?: return Outcome.Err("Missing google-services.json — see Cloud settings help.")
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return Outcome.Ok(GoogleSignIn.getClient(appContext, gso).signInIntent)
    }

    suspend fun handleSignInResult(data: Intent?): Outcome<CloudUser> =
        withContext(Dispatchers.IO) {
            val firebaseAuth = auth
                ?: return@withContext Outcome.Err("Cloud sync is not configured on this build yet.")
            runCatching {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                val result = firebaseAuth.signInWithCredential(credential).await()
                result.user?.toCloudUser()
                    ?: throw IllegalStateException("Sign-in returned no user.")
            }.fold(
                onSuccess = { cloud ->
                    sessionPrefs.googleDisplayName = cloud.displayName
                    sessionPrefs.googleEmail = cloud.email
                    // Belt-and-braces alongside the listener (timing-safe).
                    runCatching {
                        sessionPrefs.isGlobalHeadUser = AdminConfig.isGlobalHeadEmail(cloud.email)
                    }
                    Outcome.Ok(cloud)
                },
                onFailure = { e ->
                    val msg = when (e) {
                        is ApiException -> when (e.statusCode) {
                            10 -> "Google Sign-In failed (Code 10: DEVELOPER_ERROR — SHA-1 fingerprint mismatch in Firebase Console)."
                            12500 -> "Google Sign-In failed (Code 12500: Play Services error. Check Google account and Firebase config)."
                            7 -> "Network error during Google Sign-In. Check your internet connection."
                            12501 -> "Google Sign-In was cancelled."
                            else -> "Google Sign-In failed (Code ${e.statusCode}: ${e.statusMessage ?: e.message ?: "unknown"})."
                        }
                        else -> e.message ?: "Google sign-in failed."
                    }
                    Outcome.Err(msg)
                }
            )
        }

    /**
     * Phase-3 gateway calls: fresh Firebase ID token for the Worker's
     * `Authorization: Bearer` gate. Null when signed out/unconfigured — the
     * caller falls back (native TTS / local file), never blocks. Never throws.
     */
    suspend fun idToken(): String? = withContext(Dispatchers.IO) {
        val a = auth ?: return@withContext null
        runCatching {
            var user = a.currentUser
            if (user == null) {
                user = runCatching { a.signInAnonymously().await().user }.getOrNull()
            }
            user?.getIdToken(false)?.await()?.token?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun signOut() {
        sessionPrefs.googleDisplayName = null
        sessionPrefs.googleEmail = null
        runCatching {
            auth?.signOut()
            GoogleSignIn.getClient(
                appContext,
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            ).signOut()
        }
    }

    private fun com.google.firebase.auth.FirebaseUser.toCloudUser() =
        CloudUser(uid = uid, email = email, displayName = displayName)
}
