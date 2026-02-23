package com.chatassistantmobile.ui.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.chatassistantmobile.MainActivity
import com.chatassistantmobile.data.local.LatestAnalysisWidgetStore
import com.chatassistantmobile.domain.model.LatestWidgetState

class LatestSuggestionWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val latest = LatestAnalysisWidgetStore(context).read()
        provideContent {
            LatestSuggestionWidgetContent(latest)
        }
    }
}

class LatestSuggestionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LatestSuggestionWidget()
}

@Composable
private fun LatestSuggestionWidgetContent(latest: LatestWidgetState?) {
    val openAppAction = actionStartActivity<MainActivity>()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFFF6F9FF)))
            .padding(12.dp)
            .clickable(openAppAction),
        verticalAlignment = Alignment.Vertical.Top,
        horizontalAlignment = Alignment.Horizontal.Start
    ) {
        Text(
            text = "Quick Reply",
            style = TextStyle(
                color = ColorProvider(Color(0xFF1B3A8A)),
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (latest == null) {
            Text(
                text = "Analyze a conversation in-app to populate this widget.",
                style = TextStyle(color = ColorProvider(Color(0xFF243B53)))
            )
            return@Column
        }

        Text(
            text = latest.summary,
            maxLines = 3,
            style = TextStyle(color = ColorProvider(Color(0xFF102A43)))
        )

        if (latest.sampleReply.isNotBlank()) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = "Reply: ${latest.sampleReply}",
                maxLines = 3,
                style = TextStyle(color = ColorProvider(Color(0xFF334E68)))
            )
        }
    }
}
