package com.chatassistantmobile.domain.model

data class AnalysisSuggestion(
    val key: String,
    val title: String,
    val interpretation: String,
    val sampleReply: String
)

data class AnalysisUiModel(
    val summary: String?,
    val suggestions: List<AnalysisSuggestion>,
    val rawJson: String
)
