package com.chatassistantmobile.data.auth

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
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRefreshFlowUnitTest {
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun authInterceptor_addsBearerHeader_forProtectedApi() {
        val store = FakeSessionTokenStore(
            currentAccessToken = "access-123",
            currentRefreshToken = "refresh-123"
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .build()

        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val request = Request.Builder()
            .url(server.url("/api/v1/analyze-chat"))
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { }

        val recorded = server.takeRequest()
        assertEquals("Bearer access-123", recorded.getHeader("Authorization"))
    }

    @Test
    fun authInterceptor_skipsBearerHeader_forRefreshApi() {
        val store = FakeSessionTokenStore(
            currentAccessToken = "access-123",
            currentRefreshToken = "refresh-123"
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .build()

        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val request = Request.Builder()
            .url(server.url("/api/v1/auth/refresh"))
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { }

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun tokenAuthenticator_refreshesAndRetries_withNewAccessToken() {
        val store = FakeSessionTokenStore(
            currentAccessToken = "expired-access",
            currentRefreshToken = "refresh-1"
        )
        val api = FakeChatAssistantApi(
            refreshResult = tokenResponse(
                access = "new-access",
                refresh = "new-refresh"
            )
        )
        val authenticator = TokenAuthenticator(store, api)

        val request = authenticator.authenticate(
            route = null,
            response = unauthorizedResponse(
                path = "/api/v1/analyze-chat",
                bearer = "expired-access"
            )
        )

        requireNotNull(request)
        assertEquals("Bearer new-access", request.header("Authorization"))
        assertEquals("new-access", store.currentAccessToken)
        assertEquals("new-refresh", store.currentRefreshToken)
        assertTrue(!store.clearCalled)
    }

    @Test
    fun tokenAuthenticator_clearsSession_whenRefreshFails() {
        val store = FakeSessionTokenStore(
            currentAccessToken = "expired-access",
            currentRefreshToken = "refresh-1"
        )
        val api = FakeChatAssistantApi(refreshError = IllegalStateException("refresh failed"))
        val authenticator = TokenAuthenticator(store, api)

        val request = authenticator.authenticate(
            route = null,
            response = unauthorizedResponse(
                path = "/api/v1/analyze-chat",
                bearer = "expired-access"
            )
        )

        assertNull(request)
        assertTrue(store.clearCalled)
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

    private class FakeSessionTokenStore(
        var currentAccessToken: String? = null,
        var currentRefreshToken: String? = null
    ) : SessionTokenStore {
        var clearCalled: Boolean = false

        override fun getAccessToken(): String? = currentAccessToken

        override fun getRefreshToken(): String? = currentRefreshToken

        override fun save(token: TokenResponse) {
            currentAccessToken = token.access_token
            currentRefreshToken = token.refresh_token
        }

        override fun clear() {
            clearCalled = true
            currentAccessToken = null
            currentRefreshToken = null
        }
    }

    private class FakeChatAssistantApi(
        private val refreshResult: TokenResponse? = null,
        private val refreshError: Throwable? = null
    ) : ChatAssistantApi {
        override suspend fun login(req: GoogleLoginRequest): TokenResponse {
            throw UnsupportedOperationException("not used in test")
        }

        override suspend fun refresh(req: RefreshRequest): TokenResponse {
            refreshError?.let { throw it }
            return requireNotNull(refreshResult)
        }

        override suspend fun logout(req: LogoutRequest): LogoutResponse {
            throw UnsupportedOperationException("not used in test")
        }

        override suspend fun analyze(req: AnalyzeRequest): JsonObject {
            throw UnsupportedOperationException("not used in test")
        }
    }
}
