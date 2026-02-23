package com.chatassistantmobile.ui.widget.glance

import android.content.Context
import androidx.glance.appwidget.updateAll

class WidgetSyncManager(
    private val context: Context
) {
    suspend fun refreshLatestSuggestionWidget() {
        LatestSuggestionWidget().updateAll(context)
    }
}
