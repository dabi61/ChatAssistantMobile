package com.chatassistantmobile.data.model

data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAtEpochSeconds: Long
) {
    fun isAccessTokenExpired(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        return nowEpochSeconds >= accessTokenExpiresAtEpochSeconds
    }
}
