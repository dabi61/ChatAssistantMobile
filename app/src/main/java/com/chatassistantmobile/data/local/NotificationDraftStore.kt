package com.chatassistantmobile.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chatassistantmobile.domain.model.NotificationDraftItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class NotificationDraftStore(context: Context) {
    companion object {
        private const val PREFS_FILE = "notification_draft_encrypted_prefs"
        private const val KEY_DRAFTS_JSON = "notification_drafts_json"
        private const val MAX_DRAFTS = 120
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val prefs: SharedPreferences by lazy {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Synchronized
    fun readAll(): List<NotificationDraftItem> {
        val raw = prefs.getString(KEY_DRAFTS_JSON, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<NotificationDraftItem>>(raw)
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun append(
        packageName: String,
        senderLabel: String,
        text: String,
        postedAtEpochMillis: Long
    ) {
        val sanitizedText = text
            .trim()
            .replace("\\n", " ")
            .take(400)

        if (sanitizedText.isBlank()) return

        val updated = readAll() + NotificationDraftItem(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            senderLabel = senderLabel.trim().ifBlank { "other" }.take(80),
            text = sanitizedText,
            postedAtEpochMillis = postedAtEpochMillis
        )

        val deduped = updated
            .distinctBy { "${it.packageName}|${it.senderLabel}|${it.text}" }
            .takeLast(MAX_DRAFTS)

        prefs.edit()
            .putString(KEY_DRAFTS_JSON, json.encodeToString(deduped))
            .apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_DRAFTS_JSON).apply()
    }

    @Synchronized
    fun toConversationDraft(limit: Int = 40): String {
        return readAll()
            .takeLast(limit)
            .joinToString("\n") { item ->
                "other: ${item.text}"
            }
    }
}
