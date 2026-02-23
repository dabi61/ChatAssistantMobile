package com.chatassistantmobile.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PrivacyConsentStore(context: Context) {
    companion object {
        private const val PREFS_FILE = "privacy_consent_encrypted_prefs"
        private const val KEY_PRIVACY_ACCEPTED = "privacy_accepted"
        private const val KEY_NOTIFICATION_CAPTURE = "notification_capture"
        private const val KEY_ACCESSIBILITY_CAPTURE = "accessibility_capture"
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

    fun isPrivacyAccepted(): Boolean = prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false)

    fun isNotificationCaptureEnabled(): Boolean =
        prefs.getBoolean(KEY_NOTIFICATION_CAPTURE, false)

    fun isAccessibilityCaptureEnabled(): Boolean =
        prefs.getBoolean(KEY_ACCESSIBILITY_CAPTURE, false)

    fun setPrivacyAccepted(value: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY_ACCEPTED, value).apply()
    }

    fun setNotificationCaptureEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_CAPTURE, value).apply()
    }

    fun setAccessibilityCaptureEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ACCESSIBILITY_CAPTURE, value).apply()
    }

    fun canCaptureNotifications(): Boolean {
        return isPrivacyAccepted() && isNotificationCaptureEnabled()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
