package com.chatassistantmobile.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chatassistantmobile.data.model.SessionTokens
import com.chatassistantmobile.data.model.TokenResponse

class SessionStore(context: Context) : SessionTokenStore {
    companion object {
        private const val PREFS_FILE = "session_encrypted_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
    }

    private val prefs: SharedPreferences by lazy {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun save(token: TokenResponse) {
        save(
            accessToken = token.access_token,
            refreshToken = token.refresh_token,
            expiresInSeconds = token.expires_in
        )
    }

    fun save(accessToken: String, refreshToken: String, expiresInSeconds: Int) {
        val expiresAt = System.currentTimeMillis() / 1000 + expiresInSeconds - 30
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun getSessionTokens(): SessionTokens? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_ACCESS_EXPIRES_AT, 0L)

        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank() || expiresAt <= 0L) {
            return null
        }
        return SessionTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAtEpochSeconds = expiresAt
        )
    }

    override fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    override fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun hasRefreshToken(): Boolean = !getRefreshToken().isNullOrBlank()

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
