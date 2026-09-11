@file:Suppress("DEPRECATION") // GoogleSignIn classic API: still the stable intent flow.

package com.durgamma.festival.data.remote

import android.content.Context
import android.content.Intent
import com.durgamma.festival.core.util.Outcome
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
class AuthRepository(private val appContext: Context) {

    /** Null on builds without Firebase config — all calls below then no-op gracefully. */
    private val auth: FirebaseAuth? = runCatching { Firebase.auth }.getOrNull()

    val isConfigured: Boolean get() = auth != null

    private val _user = MutableStateFlow(auth?.currentUser?.toCloudUser())
    val user: StateFlow<CloudUser?> = _user.asStateFlow()

    private val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _user.value = firebaseAuth.currentUser?.toCloudUser()
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
                onSuccess = { Outcome.Ok(it) },
                onFailure = { Outcome.Err(it.message ?: "Google sign-in failed.") }
            )
        }

    fun signOut() {
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
