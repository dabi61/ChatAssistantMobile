package com.chatassistantmobile.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationDraftItem(
    val id: String,
    val packageName: String,
    val senderLabel: String,
    val text: String,
    val postedAtEpochMillis: Long
)
