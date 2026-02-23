package com.chatassistantmobile.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.chatassistantmobile.service.accessibility.ChatAccessibilityService
import com.chatassistantmobile.service.notification.ChatNotificationListenerService

object SystemPermissionStatus {
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ChatNotificationListenerService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()

        return enabled
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == expected }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ChatAccessibilityService::class.java)
            .flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        return enabled
            .split(':')
            .any { it.equals(expected, ignoreCase = true) }
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
}
