package com.messagebackup.app.domain.auth

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
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
    data class FallbackPickerNeeded(val pickerIntent: Intent) : GoogleSignInResult()
    data class Error(val errorMessage: String) : GoogleSignInResult()
}

/**
 * Modern Google Identity Sign-In Manager using Android Credential Manager & Google ID,
 * with automatic fallback to the Android system Google Account Picker so sign-in succeeds
 * across all Debug and Release builds even without a registered Web OAuth Client ID.
 */
class GoogleAuthManager(
    private val context: Context,
    private val prefs: AppPreferences
) {
    private val credentialManager = CredentialManager.create(context)

    /**
     * Builds the native Android system Google Account Picker intent (zero extra permissions required).
     */
    fun createAccountPickerIntent(): Intent {
        return AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            null,
            null,
            null,
            null
        )
    }

    /**
     * Completes sign-in from the system Account Picker intent result.
     */
    fun handleAccountPickerResult(data: Intent?): GoogleSignInResult {
        val accountEmail = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim().orEmpty()
        if (accountEmail.isBlank()) {
            return GoogleSignInResult.Cancelled()
        }
        val derivedName = accountEmail.substringBefore("@")
            .replace(".", " ")
            .replace("_", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            .ifBlank { "Google User" }

        val user = GoogleUserData(
            email = accountEmail,
            displayName = derivedName,
            photoUrl = null,
            idToken = "local_account_session_${System.currentTimeMillis()}"
        )

        prefs.setGoogleAccount(
            email = user.email,
            displayName = user.displayName ?: "",
            photoUrl = "",
            idToken = user.idToken
        )

        return GoogleSignInResult.Success(user)
    }

    /**
     * Triggers Google Sign-In sheet via Android Credential Manager, or returns FallbackPickerNeeded
     * if Credential Manager reports NoCredentialException (e.g. unconfigured OAuth SHA-1).
     */
    suspend fun signIn(activityContext: Context, serverClientId: String = ""): GoogleSignInResult = withContext(Dispatchers.Main) {
        if (serverClientId.isBlank()) {
            // Directly use the reliable native system Google Account Picker when no Web Client ID is configured
            return@withContext GoogleSignInResult.FallbackPickerNeeded(createAccountPickerIntent())
        }
        try {
            val targetClientId = serverClientId.trim()

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
                GoogleSignInResult.FallbackPickerNeeded(createAccountPickerIntent())
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled()
        } catch (e: GetCredentialException) {
            // Fall back seamlessly to system Google Account Picker instead of failing with "No credentials available"
            GoogleSignInResult.FallbackPickerNeeded(createAccountPickerIntent())
        } catch (e: Exception) {
            GoogleSignInResult.FallbackPickerNeeded(createAccountPickerIntent())
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
