package com.chatassistantmobile.data.local

import android.content.Context
import android.content.SharedPreferences

class FloatingBubbleStateStore(context: Context) {
    companion object {
        private const val PREFS_FILE = "floating_bubble_state"
        private const val KEY_RUNNING = "is_running"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun isRunning(): Boolean = prefs.getBoolean(KEY_RUNNING, false)

    fun setRunning(value: Boolean) {
        prefs.edit().putBoolean(KEY_RUNNING, value).apply()
    }
}
