package com.chatassistantmobile.data.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chatassistantmobile.data.api.ChatAssistantApi
import com.chatassistantmobile.data.model.AnalyzeRequest
import com.chatassistantmobile.data.model.GoogleLoginRequest
import com.chatassistantmobile.data.model.LogoutRequest
import com.chatassistantmobile.data.model.LogoutResponse
import com.chatassistantmobile.data.model.RefreshRequest
import com.chatassistantmobile.data.model.TokenResponse
import com.chatassistantmobile.data.model.UserInfo
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenAuthenticatorInstrumentationTest {
    private lateinit var sessionStore: SessionStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sessionStore = SessionStore(context)
        sessionStore.clear()
    }

    @After
    fun teardown() {
        sessionStore.clear()
    }

    @Test
    fun authenticate_updatesEncryptedSessionStore_afterRefreshSuccess() {
        sessionStore.save(
            accessToken = "expired-access",
            refreshToken = "refresh-1",
            expiresInSeconds = 3600
        )

        val api = FakeRefreshApi(
            tokenResponse(
                access = "new-access",
                refresh = "new-refresh"
            )
        )
        val authenticator = TokenAuthenticator(sessionStore, api)

        val retriedRequest = authenticator.authenticate(
            route = null,
            response = unauthorizedResponse(
                path = "/api/v1/analyze-chat",
                bearer = "expired-access"
            )
        )

        assertNotNull(retriedRequest)
        assertEquals("Bearer new-access", retriedRequest?.header("Authorization"))
        assertEquals("new-access", sessionStore.getAccessToken())
        assertEquals("new-refresh", sessionStore.getRefreshToken())
    }

    @Test
    fun authenticate_clearsEncryptedSessionStore_afterRefreshFailure() {
        sessionStore.save(
            accessToken = "expired-access",
            refreshToken = "refresh-1",
            expiresInSeconds = 3600
        )

        val api = FakeRefreshApi(error = IllegalStateException("refresh failed"))
        val authenticator = TokenAuthenticator(sessionStore, api)

        val retriedRequest = authenticator.authenticate(
            route = null,
            response = unauthorizedResponse(
                path = "/api/v1/analyze-chat",
                bearer = "expired-access"
            )
        )

        assertNull(retriedRequest)
        assertNull(sessionStore.getAccessToken())
        assertNull(sessionStore.getRefreshToken())
    }

    private fun tokenResponse(access: String, refresh: String): TokenResponse {
        return TokenResponse(
            access_token = access,
            token_type = "bearer",
            expires_in = 3600,
            refresh_token = refresh,
            refresh_expires_in = 1_209_600,
            user = UserInfo(provider = "google", subject = "google:123")
        )
    }

    private fun unauthorizedResponse(path: String, bearer: String): Response {
        val request = Request.Builder()
            .url("https://example.com$path")
            .header("Authorization", "Bearer $bearer")
            .build()

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }

    private class FakeRefreshApi(
        private val tokenResponse: TokenResponse? = null,
        private val error: Throwable? = null
    ) : ChatAssistantApi {
        override suspend fun login(req: GoogleLoginRequest): TokenResponse {
            throw UnsupportedOperationException("not used in test")
        }

        override suspend fun refresh(req: RefreshRequest): TokenResponse {
            error?.let { throw it }
            return requireNotNull(tokenResponse)
        }

        override suspend fun logout(req: LogoutRequest): LogoutResponse {
            throw UnsupportedOperationException("not used in test")
        }

        override suspend fun analyze(req: AnalyzeRequest): JsonObject {
            throw UnsupportedOperationException("not used in test")
        }
    }
}
