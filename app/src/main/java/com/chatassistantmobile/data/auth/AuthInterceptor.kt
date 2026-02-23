package com.chatassistantmobile.data.auth

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionStore: SessionTokenStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val shouldSkipAuth = path.endsWith("/auth/google/login") || path.endsWith("/auth/refresh")
        val token = sessionStore.getAccessToken()

        if (shouldSkipAuth || token.isNullOrBlank()) {
            return chain.proceed(request)
        }

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        )
    }
}
