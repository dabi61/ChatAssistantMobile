package com.chatassistantmobile.service.notification

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.os.BundleCompat
import com.chatassistantmobile.data.local.NotificationDraftStore
import com.chatassistantmobile.data.local.PrivacyConsentStore

class ChatNotificationListenerService : NotificationListenerService() {
    private val draftStore: NotificationDraftStore by lazy {
        NotificationDraftStore(applicationContext)
    }
    private val privacyConsentStore: PrivacyConsentStore by lazy {
        PrivacyConsentStore(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == packageName) return
        if (!privacyConsentStore.canCaptureNotifications()) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        if (!isLikelyMessageNotification(notification, extras)) {
            return
        }

        val extractedMessages = extractMessages(extras)
        if (extractedMessages.isNotEmpty()) {
            extractedMessages.forEach { message ->
                draftStore.append(
                    packageName = sbn.packageName,
                    senderLabel = message.sender,
                    text = message.text,
                    postedAtEpochMillis = sbn.postTime
                )
            }
            return
        }

        val fallbackText = firstNonBlank(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        ) ?: return

        val title = firstNonBlank(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString(),
            "other"
        ) ?: "other"

        draftStore.append(
            packageName = sbn.packageName,
            senderLabel = title,
            text = fallbackText,
            postedAtEpochMillis = sbn.postTime
        )
    }

    private fun isLikelyMessageNotification(notification: Notification, extras: Bundle): Boolean {
        val categoryMatch = notification.category == Notification.CATEGORY_MESSAGE
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val templateMatch = template.contains("MessagingStyle", ignoreCase = true)
        return categoryMatch || templateMatch
    }

    private fun extractMessages(extras: Bundle): List<ExtractedMessage> {
        val raw = BundleCompat.getParcelableArray(
            extras,
            Notification.EXTRA_MESSAGES,
            Bundle::class.java
        )
            ?: return emptyList()

        return raw.mapNotNull { element ->
            val bundle = element as? Bundle ?: return@mapNotNull null
            val text = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
            if (text.isBlank()) return@mapNotNull null

            val sender = firstNonBlank(
                bundle.getCharSequence("sender")?.toString(),
                extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                "other"
            ) ?: "other"

            ExtractedMessage(sender = sender, text = text)
        }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        values.forEach { value ->
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private data class ExtractedMessage(
        val sender: String,
        val text: String
    )
}
