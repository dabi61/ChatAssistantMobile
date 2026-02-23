package com.chatassistantmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyConsentScreen(
    notificationCaptureEnabled: Boolean,
    accessibilityCaptureEnabled: Boolean,
    onNotificationCaptureChanged: (Boolean) -> Unit,
    onAccessibilityCaptureChanged: (Boolean) -> Unit,
    onAcceptConsent: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Privacy Consent",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The app can process chat content only for analysis features you enable.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Allow notification capture",
            style = MaterialTheme.typography.titleMedium
        )
        Switch(
            checked = notificationCaptureEnabled,
            onCheckedChange = onNotificationCaptureChanged
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Allow accessibility capture (future feature)",
            style = MaterialTheme.typography.titleMedium
        )
        Switch(
            checked = accessibilityCaptureEnabled,
            onCheckedChange = onAccessibilityCaptureChanged
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "You can change these choices anytime in Settings.",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAcceptConsent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I Agree and Continue")
        }
    }
}
