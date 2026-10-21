package com.messagebackup.app.domain.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.messagebackup.app.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GoogleUserData(
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
    val idToken: String
)

sealed class GoogleSignInResult {
    data class Success(val user: GoogleUserData) : GoogleSignInResult()
    data class Cancelled(val message: String = "Sign-in cancelled") : GoogleSignInResult()
    data class Error(val errorMessage: String) : GoogleSignInResult()
}

/**
 * Modern Google Identity Sign-In Manager using Android Credential Manager & Google ID.
 * Works seamlessly across Android 14, 15, and 16.
 */
class GoogleAuthManager(
    private val context: Context,
    private val prefs: AppPreferences
) {
    private val credentialManager = CredentialManager.create(context)

    /**
     * Triggers Google Sign-In sheet via Android Credential Manager.
     * @param serverClientId Optional OAuth 2.0 Web Client ID from Firebase / Google Cloud Console.
     */
    suspend fun signIn(activityContext: Context, serverClientId: String = ""): GoogleSignInResult = withContext(Dispatchers.Main) {
        try {
            // Use provided client ID or default placeholder if still in local dev mode
            val targetClientId = if (serverClientId.isNotBlank()) {
                serverClientId.trim()
            } else {
                // Fallback default client ID or generic registration
                "1084285871261-fallback.apps.googleusercontent.com"
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(targetClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result: GetCredentialResponse = credentialManager.getCredential(
                context = activityContext,
                request = request
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val user = GoogleUserData(
                    email = googleIdTokenCredential.id,
                    displayName = googleIdTokenCredential.displayName,
                    photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                    idToken = googleIdTokenCredential.idToken
                )

                // Persist session to AppPreferences
                prefs.setGoogleAccount(
                    email = user.email,
                    displayName = user.displayName ?: "",
                    photoUrl = user.photoUrl ?: "",
                    idToken = user.idToken
                )

                GoogleSignInResult.Success(user)
            } else {
                GoogleSignInResult.Error("Unsupported credential type returned: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled()
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Error(e.localizedMessage ?: "Failed to sign in with Google")
        } catch (e: Exception) {
            GoogleSignInResult.Error(e.localizedMessage ?: "Unexpected error during authentication")
        }
    }

    /**
     * Signs out the current user and clears saved credentials.
     */
    suspend fun signOut(): Boolean = withContext(Dispatchers.IO) {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            prefs.clearGoogleAccount()
            true
        } catch (e: Exception) {
            prefs.clearGoogleAccount()
            false
        }
    }
}
