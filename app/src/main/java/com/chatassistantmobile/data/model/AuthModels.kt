package com.chatassistantmobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GoogleLoginRequest(val id_token: String)

@Serializable
data class RefreshRequest(val refresh_token: String)

@Serializable
data class LogoutRequest(val refresh_token: String)

@Serializable
data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val refresh_token: String,
    val refresh_expires_in: Int,
    val user: UserInfo
)

@Serializable
data class UserInfo(
    val provider: String,
    val subject: String,
    val email: String? = null,
    val name: String? = null,
    val picture: String? = null
)

@Serializable
data class LogoutResponse(
    val success: Boolean? = null,
    val message: String? = null
)
