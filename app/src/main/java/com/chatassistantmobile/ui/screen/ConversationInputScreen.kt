package com.chatassistantmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chatassistantmobile.domain.model.AnalysisHistoryItem

private val roleOptions = listOf("crush", "friend", "family", "customer")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationInputScreen(
    conversationInput: String,
    selectedRole: String,
    isAnalyzing: Boolean,
    error: String?,
    history: List<AnalysisHistoryItem>,
    notificationDraftCount: Int,
    notificationStatus: String?,
    notificationCaptureEnabled: Boolean,
    onConversationInputChanged: (String) -> Unit,
    onRoleSelected: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onImportNotifications: () -> Unit,
    onClearNotificationDrafts: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversation Input") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Paste chat transcript. Optional prefixes: me: or other:",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = conversationInput,
                onValueChange = onConversationInputChanged,
                minLines = 10,
                maxLines = 16,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("me: Hey, are you free tonight?\\nother: Maybe after 8pm")
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Captured notification lines: $notificationDraftCount",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onImportNotifications,
                        enabled = notificationCaptureEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import from notifications")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onClearNotificationDrafts,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear notification drafts")
                    }
                    if (!notificationStatus.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = notificationStatus,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!notificationCaptureEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enable notification capture consent to use import.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Relationship role",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                roleOptions.forEach { role ->
                    FilterChip(
                        selected = selectedRole == role,
                        onClick = { onRoleSelected(role) },
                        label = { Text(role) }
                    )
                }
            }

            if (!error.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAnalyzeClick,
                enabled = !isAnalyzing && conversationInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isAnalyzing) "Analyzing..." else "Analyze Chat")
            }

            if (history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Recent analyses",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                history.take(3).forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = item.relationshipRole.uppercase(),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = item.summary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
