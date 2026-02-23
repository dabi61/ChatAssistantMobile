package com.chatassistantmobile.data.repository

import com.chatassistantmobile.data.api.ChatAssistantApi
import com.chatassistantmobile.data.model.AnalyzeRequest
import com.chatassistantmobile.data.model.ChatMessage
import com.chatassistantmobile.data.model.GoogleLoginRequest
import com.chatassistantmobile.data.model.LogoutRequest
import com.chatassistantmobile.data.model.LogoutResponse
import com.chatassistantmobile.data.model.RefreshRequest
import com.chatassistantmobile.data.model.TokenResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryParsingUnitTest {

    @Test
    fun analyze_parsesNestedAnalysisSchema_withScenariosArray() = runBlocking {
        val payload = jsonObjectOf(
            """
                {
                  "relationship_role": "crush",
                  "analysis": {
                    "summary": "Doi phuong hoi sai nguoi va dang do hoi thong tin.",
                    "scenarios": [
                      {
                        "color": "red",
                        "title": "Nhầm lẫn hoặc cố tình hỏi sai người",
                        "interpretation": "Ho co the nham nguoi.",
                        "sample_reply": "Ơ, mình không phải Bình lớp 9a2 ạ. Chắc bạn nhầm ai rồi."
                      },
                      {
                        "color": "yellow",
                        "title": "Dò hỏi thông tin gián tiếp",
                        "interpretation": "Dang do hoi thong tin.",
                        "sample_reply": "Mình không phải Bình lớp 9a2 đâu. Nếu bạn cần hỏi gì khác thì cứ nói nhé."
                      }
                    ]
                  }
                }
            """.trimIndent()
        )
        val repository = ChatRepository(FakeChatAssistantApi(payload))

        val result = repository.analyze(
            relationshipRole = "crush",
            chatHistory = listOf(ChatMessage(sender = "other", text = "Hi"))
        )

        assertTrue(result.isSuccess)
        val ui = result.getOrThrow()
        assertEquals("Doi phuong hoi sai nguoi va dang do hoi thong tin.", ui.summary)
        assertEquals(2, ui.suggestions.size)
        assertEquals("red", ui.suggestions[0].key)
        assertEquals(
            "Ơ, mình không phải Bình lớp 9a2 ạ. Chắc bạn nhầm ai rồi.",
            ui.suggestions[0].sampleReply
        )
        assertEquals("yellow", ui.suggestions[1].key)
    }

    @Test
    fun analyze_keepsLegacyTopLevelSchema_compatible() = runBlocking {
        val payload = jsonObjectOf(
            """
                {
                  "summary": "Top-level summary",
                  "scenarios": {
                    "green": {
                      "title": "Good",
                      "interpretation": "Everything is fine.",
                      "sample_reply": "Ban co the tiep tuc nhe."
                    },
                    "red": {
                      "title": "Risk",
                      "interpretation": "Can than trong cach tra loi.",
                      "sample_reply": "Minh can them thong tin truoc khi tra loi."
                    }
                  }
                }
            """.trimIndent()
        )
        val repository = ChatRepository(FakeChatAssistantApi(payload))

        val result = repository.analyze(
            relationshipRole = "friend",
            chatHistory = listOf(ChatMessage(sender = "me", text = "Hello"))
        )

        assertTrue(result.isSuccess)
        val ui = result.getOrThrow()
        assertEquals("Top-level summary", ui.summary)
        assertEquals(2, ui.suggestions.size)
        assertEquals("red", ui.suggestions[0].key)
        assertEquals("green", ui.suggestions[1].key)
    }

    private fun jsonObjectOf(raw: String): JsonObject {
        return Json.parseToJsonElement(raw).jsonObject
    }

    private class FakeChatAssistantApi(
        private val payload: JsonObject
    ) : ChatAssistantApi {
        override suspend fun login(req: GoogleLoginRequest): TokenResponse {
            throw UnsupportedOperationException("not used in test")
        }

        override suspend fun refresh(req: RefreshRequest): TokenResponse {
            throw UnsupportedOperationException("not used in test")
        }

        override suspend fun logout(req: LogoutRequest): LogoutResponse {
            throw UnsupportedOperationException("not used in test")
        }

        override suspend fun analyze(req: AnalyzeRequest): JsonObject = payload
    }
}
