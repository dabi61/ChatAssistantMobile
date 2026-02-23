package com.chatassistantmobile.di

import android.content.Context
import com.chatassistantmobile.BuildConfig
import com.chatassistantmobile.data.api.ChatAssistantApi
import com.chatassistantmobile.data.auth.AuthInterceptor
import com.chatassistantmobile.data.auth.GoogleIdTokenProvider
import com.chatassistantmobile.data.auth.SessionStore
import com.chatassistantmobile.data.auth.TokenAuthenticator
import com.chatassistantmobile.data.local.AnalysisHistoryStore
import com.chatassistantmobile.data.local.CurrentScreenCaptureStore
import com.chatassistantmobile.data.local.LatestAnalysisWidgetStore
import com.chatassistantmobile.data.local.NotificationDraftStore
import com.chatassistantmobile.data.local.PrivacyConsentStore
import com.chatassistantmobile.data.repository.AuthRepository
import com.chatassistantmobile.data.repository.ChatRepository
import com.chatassistantmobile.ui.widget.glance.WidgetSyncManager
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalSerializationApi::class)
class AppContainer(context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    val sessionStore = SessionStore(context)
    val analysisHistoryStore = AnalysisHistoryStore(context)
    val currentScreenCaptureStore = CurrentScreenCaptureStore(context)
    val notificationDraftStore = NotificationDraftStore(context)
    val latestAnalysisWidgetStore = LatestAnalysisWidgetStore(context)
    val privacyConsentStore = PrivacyConsentStore(context)
    val googleIdTokenProvider = GoogleIdTokenProvider()
    val widgetSyncManager = WidgetSyncManager(context)

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // Avoid leaking bearer token in logs while still allowing body inspection in debug.
        redactHeader("Authorization")
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE
    }

    private fun retrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private val refreshClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val refreshApi = retrofit(refreshClient).create(ChatAssistantApi::class.java)

    private val appClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(sessionStore))
        .authenticator(TokenAuthenticator(sessionStore, refreshApi))
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val appApi = retrofit(appClient).create(ChatAssistantApi::class.java)

    val authRepository = AuthRepository(appApi, sessionStore)
    val chatRepository = ChatRepository(appApi)
}
