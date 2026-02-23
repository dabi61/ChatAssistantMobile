package com.chatassistantmobile.data.auth

import android.app.Activity
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.CredentialManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

class GoogleIdTokenProvider {
    suspend fun getGoogleIdToken(activity: Activity, serverClientId: String): Result<String> {
        if (serverClientId.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Missing GOOGLE_WEB_CLIENT_ID. Add it in gradle.properties."
                )
            )
        }

        val credentialManager = CredentialManager.create(activity)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return runCatching {
            val response = credentialManager.getCredential(
                context = activity,
                request = request
            )
            parseResponse(response)
        }.recoverCatching { throwable ->
            when (throwable) {
                is GetCredentialException -> throw throwable
                else -> throw IllegalStateException("Google sign-in failed", throwable)
            }
        }
    }

    private fun parseResponse(response: GetCredentialResponse): String {
        val credential = response.credential
        if (credential !is CustomCredential) {
            throw IllegalStateException("Unsupported credential type")
        }

        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException("Credential is not a Google ID token")
        }

        return try {
            val googleTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            googleTokenCredential.idToken.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Google ID token is empty")
        } catch (e: GoogleIdTokenParsingException) {
            throw IllegalStateException("Unable to parse Google ID token", e)
        }
    }
}
