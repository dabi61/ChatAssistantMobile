package com.chatassistantmobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val sender: String,
    val text: String,
    val timestamp: String? = null
)

@Serializable
data class AnalyzeRequest(
    val relationship_role: String,
    val chat_history: List<ChatMessage>,
    val locale: String = "vi-VN",
    val conversation_id: String? = null
)
