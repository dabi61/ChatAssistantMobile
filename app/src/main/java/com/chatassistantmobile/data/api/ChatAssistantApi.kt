package com.chatassistantmobile.data.api

import com.chatassistantmobile.data.model.AnalyzeRequest
import com.chatassistantmobile.data.model.GoogleLoginRequest
import com.chatassistantmobile.data.model.LogoutRequest
import com.chatassistantmobile.data.model.LogoutResponse
import com.chatassistantmobile.data.model.RefreshRequest
import com.chatassistantmobile.data.model.TokenResponse
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST

interface ChatAssistantApi {
    @POST("/api/v1/auth/google/login")
    suspend fun login(@Body req: GoogleLoginRequest): TokenResponse

    @POST("/api/v1/auth/refresh")
    suspend fun refresh(@Body req: RefreshRequest): TokenResponse

    @POST("/api/v1/auth/logout")
    suspend fun logout(@Body req: LogoutRequest): LogoutResponse

    @POST("/api/v1/analyze-chat")
    suspend fun analyze(@Body req: AnalyzeRequest): JsonObject
}
