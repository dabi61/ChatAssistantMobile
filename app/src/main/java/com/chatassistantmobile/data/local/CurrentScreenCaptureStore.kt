package com.chatassistantmobile.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CurrentScreenCaptureStore(context: Context) {
    companion object {
        private const val PREFS_FILE = "current_screen_capture_encrypted_prefs"
        private const val KEY_PACKAGE_NAME = "package_name"
        private const val KEY_CAPTURED_TEXT = "captured_text"
        private const val KEY_CAPTURED_RAW_TEXT = "captured_raw_text"
        private const val KEY_CAPTURED_FILTERED_TEXT = "captured_filtered_text"
        private const val KEY_CAPTURED_AT = "captured_at"
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

    fun save(packageName: String, lines: List<String>) {
        save(
            packageName = packageName,
            rawLines = lines,
            filteredLines = lines
        )
    }

    fun save(packageName: String, rawLines: List<String>, filteredLines: List<String>) {
        val normalizedRaw = rawLines
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .takeLast(220)

        val normalizedFiltered = filteredLines
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .takeLast(80)

        if (normalizedRaw.isEmpty() && normalizedFiltered.isEmpty()) return

        val analyzeDraft = normalizedFiltered
            .map(::ensureSpeakerPrefix)
            .joinToString("\n")
        prefs.edit()
            .putString(KEY_PACKAGE_NAME, packageName)
            .putString(KEY_CAPTURED_TEXT, analyzeDraft)
            .putString(KEY_CAPTURED_RAW_TEXT, normalizedRaw.joinToString("\n"))
            .putString(KEY_CAPTURED_FILTERED_TEXT, normalizedFiltered.joinToString("\n"))
            .putLong(KEY_CAPTURED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun ensureSpeakerPrefix(line: String): String {
        val normalized = line.trim()
        if (normalized.startsWith("me:", ignoreCase = true) ||
            normalized.startsWith("other:", ignoreCase = true)
        ) {
            return normalized
        }
        return "other: $normalized"
    }

    fun readCapturedText(): String {
        return prefs.getString(KEY_CAPTURED_TEXT, "").orEmpty()
    }

    fun readRawCapturedText(): String {
        return prefs.getString(KEY_CAPTURED_RAW_TEXT, "").orEmpty()
    }

    fun readFilteredCapturedText(): String {
        return prefs.getString(KEY_CAPTURED_FILTERED_TEXT, "").orEmpty()
    }

    fun readPackageName(): String {
        return prefs.getString(KEY_PACKAGE_NAME, "").orEmpty()
    }

    fun readCapturedAtEpochMillis(): Long {
        return prefs.getLong(KEY_CAPTURED_AT, 0L)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
