package com.chatassistantmobile.data.auth

import com.chatassistantmobile.data.api.ChatAssistantApi
import com.chatassistantmobile.data.model.RefreshRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val sessionStore: SessionTokenStore,
    private val refreshApi: ChatAssistantApi
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) {
            return null
        }

        val path = response.request.url.encodedPath
        if (path.endsWith("/auth/google/login") || path.endsWith("/auth/refresh")) {
            return null
        }

        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()

        return runBlocking {
            refreshMutex.withLock {
                val latestToken = sessionStore.getAccessToken()
                if (!latestToken.isNullOrBlank() && latestToken != failedToken) {
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer $latestToken")
                        .build()
                }

                val refreshToken = sessionStore.getRefreshToken() ?: return@withLock null
                val refreshed = runCatching {
                    refreshApi.refresh(RefreshRequest(refresh_token = refreshToken))
                }.getOrNull() ?: run {
                    sessionStore.clear()
                    return@withLock null
                }

                sessionStore.save(refreshed)
                return@withLock response.request.newBuilder()
                    .header("Authorization", "Bearer ${refreshed.access_token}")
                    .build()
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var priorResponse = response.priorResponse
        while (priorResponse != null) {
            count++
            priorResponse = priorResponse.priorResponse
        }
        return count
    }
}
