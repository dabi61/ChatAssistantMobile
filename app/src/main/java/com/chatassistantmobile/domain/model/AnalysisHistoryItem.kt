package com.chatassistantmobile.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisHistoryItem(
    val id: String,
    val createdAtEpochMillis: Long,
    val relationshipRole: String,
    val summary: String,
    val sampleReply: String
)
