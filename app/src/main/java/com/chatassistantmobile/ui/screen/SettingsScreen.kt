package com.chatassistantmobile.ui.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.chatassistantmobile.service.SystemPermissionStatus
import com.chatassistantmobile.service.overlay.FloatingBubbleService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    historyCount: Int,
    notificationDraftCount: Int,
    privacyConsentAccepted: Boolean,
    notificationCaptureEnabled: Boolean,
    accessibilityCaptureEnabled: Boolean,
    accessibilityCapturePackage: String,
    accessibilityCaptureAtEpochMillis: Long,
    accessibilityRawPreview: String,
    accessibilityFilteredPreview: String,
    onBack: () -> Unit,
    onNotificationCaptureChanged: (Boolean) -> Unit,
    onAccessibilityCaptureChanged: (Boolean) -> Unit,
    onRefreshAccessibilityPreview: () -> Unit,
    onClearAccessibilityPreview: () -> Unit,
    onSyncSystemPermissionState: (Boolean, Boolean) -> Unit,
    onRevokePrivacyConsent: () -> Unit,
    onClearHistory: () -> Unit,
    onClearNotificationDrafts: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var canOverlay by remember { mutableStateOf(SystemPermissionStatus.canDrawOverlays(context)) }
    var bubbleRunning by remember { mutableStateOf(FloatingBubbleService.isRunning(context)) }

    LaunchedEffect(Unit) {
        onRefreshAccessibilityPreview()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canOverlay = SystemPermissionStatus.canDrawOverlays(context)
                bubbleRunning = FloatingBubbleService.isRunning(context)
                onSyncSystemPermissionState(
                    SystemPermissionStatus.isNotificationListenerEnabled(context),
                    SystemPermissionStatus.isAccessibilityServiceEnabled(context)
                )
                onRefreshAccessibilityPreview()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    openSettings(
                        context = context,
                        action = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Notification Listener Settings")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    openSettings(
                        context = context,
                        action = Settings.ACTION_ACCESSIBILITY_SETTINGS
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Accessibility Settings")
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    openOverlaySettings(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Overlay Permission Settings")
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Floating bubble",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (canOverlay) {
                    "Overlay permission: granted"
                } else {
                    "Overlay permission: missing"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enable floating bubble",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = bubbleRunning,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (!canOverlay) {
                                openOverlaySettings(context)
                                Toast.makeText(
                                    context,
                                    "Grant overlay permission before enabling bubble.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Switch
                            }
                            FloatingBubbleService.start(context)
                            bubbleRunning = true
                        } else {
                            FloatingBubbleService.stop(context)
                            bubbleRunning = false
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Bubble includes modes: set rule, analyze current screen, quick options.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Privacy: only submit the minimum conversation text required for analysis.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (privacyConsentAccepted) {
                    "Privacy consent: accepted"
                } else {
                    "Privacy consent: pending"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Notification capture",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = notificationCaptureEnabled,
                    onCheckedChange = { enabled ->
                        onNotificationCaptureChanged(enabled)
                        if (enabled) {
                            openSettings(
                                context = context,
                                action = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                            )
                            Toast.makeText(
                                context,
                                "Enable Chat Assistant in notification listener settings.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Accessibility capture",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = accessibilityCaptureEnabled,
                    onCheckedChange = { enabled ->
                        onAccessibilityCaptureChanged(enabled)
                        if (enabled) {
                            openSettings(
                                context = context,
                                action = Settings.ACTION_ACCESSIBILITY_SETTINGS
                            )
                            Toast.makeText(
                                context,
                                "Enable Chat Assistant in accessibility settings.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Accessibility capture preview",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Package: ${accessibilityCapturePackage.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Captured at: ${formatCaptureTime(accessibilityCaptureAtEpochMillis)}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRefreshAccessibilityPreview,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Refresh preview")
                }
                OutlinedButton(
                    onClick = onClearAccessibilityPreview,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear preview")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Filtered input (used for analyze)",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = accessibilityFilteredPreview.ifBlank { "(empty)" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Raw captured text (before filtering)",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = accessibilityRawPreview.ifBlank { "(empty)" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onRevokePrivacyConsent()
                    FloatingBubbleService.stop(context)
                    bubbleRunning = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Revoke privacy consent")
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Saved analyses: $historyCount",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onClearHistory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear analysis history")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Saved notification drafts: $notificationDraftCount",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onClearNotificationDrafts,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear notification drafts")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("Logout")
            }
        }
    }
}

private fun openSettings(context: android.content.Context, action: String) {
    try {
        context.startActivity(
            Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Settings screen unavailable", Toast.LENGTH_SHORT).show()
    }
}

private fun openOverlaySettings(context: android.content.Context) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Overlay settings unavailable", Toast.LENGTH_SHORT).show()
    }
}

private fun formatCaptureTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "-"
    val formatter = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
