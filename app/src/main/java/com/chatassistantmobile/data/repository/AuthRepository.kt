package com.chatassistantmobile.data.repository

import com.chatassistantmobile.data.api.ChatAssistantApi
import com.chatassistantmobile.data.auth.SessionStore
import com.chatassistantmobile.data.model.GoogleLoginRequest
import com.chatassistantmobile.data.model.LogoutRequest
import com.chatassistantmobile.data.model.SessionTokens
import com.chatassistantmobile.data.model.UserInfo

class AuthRepository(
    private val api: ChatAssistantApi,
    private val sessionStore: SessionStore
) {

    fun hasActiveSession(): Boolean = sessionStore.hasRefreshToken()

    fun currentSession(): SessionTokens? = sessionStore.getSessionTokens()

    suspend fun exchangeGoogleIdToken(googleIdToken: String): Result<UserInfo> {
        return runCatching {
            val response = api.login(GoogleLoginRequest(id_token = googleIdToken))
            sessionStore.save(response)
            response.user
        }
    }

    suspend fun logout(): Result<Unit> {
        val refreshToken = sessionStore.getRefreshToken()
        val result = runCatching {
            if (!refreshToken.isNullOrBlank()) {
                api.logout(LogoutRequest(refresh_token = refreshToken))
            }
        }
        sessionStore.clear()
        return result.map { Unit }
    }
}
