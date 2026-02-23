package com.chatassistantmobile.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chatassistantmobile.domain.model.AnalysisHistoryItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AnalysisHistoryStore(context: Context) {
    companion object {
        private const val PREFS_FILE = "analysis_history_encrypted_prefs"
        private const val KEY_HISTORY_JSON = "analysis_history_json"
        private const val MAX_HISTORY = 20
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
    fun readAll(): List<AnalysisHistoryItem> {
        val raw = prefs.getString(KEY_HISTORY_JSON, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<AnalysisHistoryItem>>(raw)
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun save(item: AnalysisHistoryItem) {
        val updated = listOf(item) + readAll()
        val deduped = updated
            .distinctBy { it.id }
            .take(MAX_HISTORY)

        prefs.edit()
            .putString(KEY_HISTORY_JSON, json.encodeToString(deduped))
            .apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_HISTORY_JSON).apply()
    }
}
