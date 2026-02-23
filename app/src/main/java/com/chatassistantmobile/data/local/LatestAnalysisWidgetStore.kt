package com.chatassistantmobile.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chatassistantmobile.domain.model.LatestWidgetState

class LatestAnalysisWidgetStore(context: Context) {
    companion object {
        private const val PREFS_FILE = "latest_widget_encrypted_prefs"
        private const val KEY_SUMMARY = "latest_summary"
        private const val KEY_REPLY = "latest_reply"
        private const val KEY_UPDATED_AT = "latest_updated_at"
    }

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

    fun save(summary: String, sampleReply: String) {
        prefs.edit()
            .putString(KEY_SUMMARY, summary)
            .putString(KEY_REPLY, sampleReply)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun read(): LatestWidgetState? {
        val summary = prefs.getString(KEY_SUMMARY, null)?.takeIf { it.isNotBlank() } ?: return null
        val reply = prefs.getString(KEY_REPLY, null).orEmpty()
        val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        return LatestWidgetState(
            summary = summary,
            sampleReply = reply,
            updatedAtEpochMillis = updatedAt
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
