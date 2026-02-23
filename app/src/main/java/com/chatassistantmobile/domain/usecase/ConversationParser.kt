package com.chatassistantmobile.domain.usecase

import com.chatassistantmobile.data.model.ChatMessage

object ConversationParser {
    private const val MAX_MESSAGES = 60
    private const val MAX_MESSAGE_LENGTH = 500

    fun parse(rawText: String): List<ChatMessage> {
        val lines = rawText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        if (lines.isEmpty()) return emptyList()

        val results = mutableListOf<ChatMessage>()
        var fallbackSender = "me"

        lines.forEach { line ->
            val explicit = parseWithPrefix(line)
            if (explicit != null) {
                results += explicit
                fallbackSender = flip(explicit.sender)
            } else {
                results += ChatMessage(
                    sender = fallbackSender,
                    text = line.take(MAX_MESSAGE_LENGTH)
                )
                fallbackSender = flip(fallbackSender)
            }
        }

        return results
            .takeLast(MAX_MESSAGES)
            .filter { it.text.isNotBlank() }
    }

    private fun parseWithPrefix(line: String): ChatMessage? {
        val separatorIndex = line.indexOf(':')
        if (separatorIndex <= 0) return null

        val rawPrefix = line.substring(0, separatorIndex).trim().lowercase()
        val message = line.substring(separatorIndex + 1).trim().take(MAX_MESSAGE_LENGTH)
        if (message.isBlank()) return null

        val sender = when (rawPrefix) {
            "me", "toi", "you", "minh" -> "me"
            "other", "partner", "ban", "ho" -> "other"
            else -> return null
        }

        return ChatMessage(sender = sender, text = message)
    }

    private fun flip(sender: String): String = if (sender == "me") "other" else "me"
}
