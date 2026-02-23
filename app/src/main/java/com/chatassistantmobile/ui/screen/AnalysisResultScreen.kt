package com.chatassistantmobile.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chatassistantmobile.domain.model.AnalysisUiModel
import com.chatassistantmobile.ui.widget.AnalysisSuggestionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisResultScreen(
    analysis: AnalysisUiModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val summaryText = analysis.summary?.takeIf { it.isNotBlank() } ?: "No summary provided by backend."
    val topReply = analysis.suggestions
        .firstOrNull { it.sampleReply.isNotBlank() }
        ?.sampleReply
        .orEmpty()
    var showRawResponse by rememberSaveable(analysis.rawJson) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis Result") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SummaryCard(
                    summary = summaryText,
                    suggestionCount = analysis.suggestions.size,
                    hasQuickReply = topReply.isNotBlank()
                )
            }

            if (topReply.isNotBlank()) {
                item {
                    QuickReplyCard(
                        reply = topReply,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(topReply))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            item {
                Text(
                    text = "Suggested actions",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (analysis.suggestions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "No structured suggestions found from backend response.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(analysis.suggestions) { index, suggestion ->
                    AnalysisSuggestionCard(
                        suggestion = suggestion,
                        order = index + 1,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(suggestion.sampleReply))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = { showRawResponse = !showRawResponse },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Code, contentDescription = "Raw payload")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (showRawResponse) "Hide raw response" else "Show raw response")
                }
            }

            if (showRawResponse) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = analysis.rawJson,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun SummaryCard(
    summary: String,
    suggestionCount: Int,
    hasQuickReply: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Conversation summary",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(text = "$suggestionCount suggestions")
                if (hasQuickReply) {
                    StatPill(text = "Quick reply ready")
                }
            }
        }
    }
}

@Composable
private fun QuickReplyCard(
    reply: String,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Quick reply",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = reply,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            FilledTonalButton(
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy quick reply")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy quick reply")
            }
        }
    }
}

@Composable
private fun StatPill(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurface
    )
}
