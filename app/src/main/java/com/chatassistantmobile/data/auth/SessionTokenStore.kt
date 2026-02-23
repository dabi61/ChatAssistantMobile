package com.chatassistantmobile.data.auth

import com.chatassistantmobile.data.model.TokenResponse

interface SessionTokenStore {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun save(token: TokenResponse)
    fun clear()
}
