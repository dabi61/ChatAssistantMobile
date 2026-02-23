package com.chatassistantmobile.data.repository

import com.chatassistantmobile.data.api.ChatAssistantApi
import com.chatassistantmobile.data.model.AnalyzeRequest
import com.chatassistantmobile.data.model.ChatMessage
import com.chatassistantmobile.domain.model.AnalysisSuggestion
import com.chatassistantmobile.domain.model.AnalysisUiModel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

class ChatRepository(
    private val api: ChatAssistantApi
) {
    private companion object {
        val orderedScenarioKeys = listOf("red", "yellow", "green", "gray")
    }

    private val retryableCodes = setOf(429, 502, 504)
    private val retryDelaysMs = listOf(700L, 1400L)

    suspend fun analyze(
        relationshipRole: String,
        chatHistory: List<ChatMessage>,
        locale: String = "vi-VN"
    ): Result<AnalysisUiModel> {
        val request = AnalyzeRequest(
            relationship_role = relationshipRole,
            chat_history = chatHistory,
            locale = locale
        )

        return runCatching {
            val response = analyzeWithRetry(request)
            response.toUiModel()
        }
    }

    private suspend fun analyzeWithRetry(request: AnalyzeRequest): JsonObject {
        var currentAttempt = 0
        while (true) {
            try {
                return api.analyze(request)
            } catch (throwable: Throwable) {
                val canRetry = throwable.isRetryable() && currentAttempt < retryDelaysMs.size
                if (!canRetry) throw throwable
                delay(retryDelaysMs[currentAttempt])
                currentAttempt++
            }
        }
    }

    private fun JsonObject.toUiModel(): AnalysisUiModel {
        val analysisNode = this["analysis"]?.asObjectOrNull()
        val primaryNode = analysisNode ?: this

        val summary = primaryNode.firstString("summary", "analysis_summary", "overview")
            ?: this.firstString("summary", "analysis_summary", "overview")
        val suggestions = primaryNode.extractSuggestions().ifEmpty {
            this.extractSuggestions()
        }

        return AnalysisUiModel(
            summary = summary,
            suggestions = suggestions,
            rawJson = toString()
        )
    }

    private fun JsonObject.extractSuggestions(): List<AnalysisSuggestion> {
        val suggestions = mutableListOf<AnalysisSuggestion>()
        parseScenariosElement(this["scenarios"], suggestions)

        if (suggestions.isEmpty()) {
            orderedScenarioKeys.forEach { key ->
                this[key]
                    ?.asObjectOrNull()
                    ?.toSuggestion(key)
                    ?.let(suggestions::add)
            }
        }

        if (suggestions.isEmpty()) {
            val array = this["suggestions"] as? JsonArray
            array?.forEachIndexed { index, element ->
                element.asObjectOrNull()
                    ?.toSuggestion("item_${index + 1}")
                    ?.let(suggestions::add)
            }
        }

        return suggestions
    }

    private fun parseScenariosElement(
        scenariosElement: JsonElement?,
        output: MutableList<AnalysisSuggestion>
    ) {
        when (scenariosElement) {
            is JsonArray -> {
                scenariosElement.forEachIndexed { index, element ->
                    val scenarioObject = element.asObjectOrNull() ?: return@forEachIndexed
                    val colorKey = scenarioObject.firstString("color", "key")
                        ?.trim()
                        ?.lowercase()
                    val key = when {
                        !colorKey.isNullOrBlank() -> colorKey
                        else -> "item_${index + 1}"
                    }
                    output += scenarioObject.toSuggestion(key)
                }
            }

            else -> {
                val scenariosObject = scenariosElement?.asObjectOrNull() ?: return
                orderedScenarioKeys.forEach { key ->
                    scenariosObject[key]
                        ?.asObjectOrNull()
                        ?.toSuggestion(key)
                        ?.let(output::add)
                }
            }
        }
    }

    private fun JsonObject.toSuggestion(key: String): AnalysisSuggestion {
        val title = firstString("title", "label") ?: key.replaceFirstChar { it.uppercase() }
        val interpretation = firstString("interpretation", "meaning", "analysis") ?: ""
        val sampleReply = firstString(
            "sample_reply",
            "sampleReply",
            "reply",
            "reply_strategy"
        ) ?: ""
        return AnalysisSuggestion(
            key = key,
            title = title,
            interpretation = interpretation,
            sampleReply = sampleReply
        )
    }

    private fun JsonObject.firstString(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key]?.jsonPrimitive?.contentOrNull
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return null
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? {
        return runCatching { jsonObject }.getOrNull()
    }

    private fun Throwable.isRetryable(): Boolean {
        return when (this) {
            is IOException -> true
            is HttpException -> code() in retryableCodes
            else -> false
        }
    }
}
