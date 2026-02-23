package com.chatassistantmobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import com.chatassistantmobile.ui.screen.AnalysisResultScreen
import com.chatassistantmobile.ui.screen.ConversationInputScreen
import com.chatassistantmobile.ui.screen.LoginScreen
import com.chatassistantmobile.ui.screen.PrivacyConsentScreen
import com.chatassistantmobile.ui.screen.SettingsScreen
import kotlinx.coroutines.launch

private enum class AppScreen {
    Login,
    Consent,
    Conversation,
    Result,
    Settings
}

@Composable
fun ChatAssistantMobileApp(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context.findActivity()

    var screen by rememberSaveable { mutableStateOf(AppScreen.Login.name) }

    LaunchedEffect(
        uiState.isCheckingSession,
        uiState.isAuthenticated,
        uiState.privacyConsentAccepted,
        screen
    ) {
        if (!uiState.isCheckingSession) {
            screen = when {
                !uiState.isAuthenticated -> AppScreen.Login.name
                !uiState.privacyConsentAccepted -> AppScreen.Consent.name
                screen == AppScreen.Login.name || screen == AppScreen.Consent.name -> {
                    AppScreen.Conversation.name
                }
                else -> screen
            }
        }
    }

    LaunchedEffect(screen) {
        if (screen == AppScreen.Conversation.name) {
            viewModel.refreshNotificationDrafts()
        }
    }

    if (uiState.isCheckingSession) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text(text = "Checking session...", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    when (AppScreen.valueOf(screen)) {
        AppScreen.Login -> {
            LoginScreen(
                isLoading = uiState.isSigningIn,
                error = uiState.loginError,
                onClearError = viewModel::clearLoginError,
                onSignInClick = {
                    if (activity != null) {
                        viewModel.signIn(activity)
                    }
                }
            )
        }

        AppScreen.Consent -> {
            PrivacyConsentScreen(
                notificationCaptureEnabled = uiState.notificationCaptureEnabled,
                accessibilityCaptureEnabled = uiState.accessibilityCaptureEnabled,
                onNotificationCaptureChanged = viewModel::setNotificationCaptureEnabled,
                onAccessibilityCaptureChanged = viewModel::setAccessibilityCaptureEnabled,
                onAcceptConsent = viewModel::acceptPrivacyConsent
            )
        }

        AppScreen.Conversation -> {
            ConversationInputScreen(
                conversationInput = uiState.conversationInput,
                selectedRole = uiState.selectedRole,
                isAnalyzing = uiState.isAnalyzing,
                error = uiState.analyzeError,
                history = uiState.history,
                notificationDraftCount = uiState.notificationDraftCount,
                notificationStatus = uiState.notificationStatus,
                notificationCaptureEnabled = uiState.notificationCaptureEnabled,
                onConversationInputChanged = viewModel::onConversationInputChanged,
                onRoleSelected = viewModel::onRoleSelected,
                onAnalyzeClick = {
                    scope.launch {
                        if (viewModel.analyzeConversation()) {
                            screen = AppScreen.Result.name
                        }
                    }
                },
                onImportNotifications = viewModel::importNotificationDrafts,
                onClearNotificationDrafts = viewModel::clearNotificationDrafts,
                onOpenSettings = { screen = AppScreen.Settings.name }
            )
        }

        AppScreen.Result -> {
            val analysis = uiState.analysis
            if (analysis == null) {
                LaunchedEffect(Unit) {
                    screen = AppScreen.Conversation.name
                }
            } else {
                AnalysisResultScreen(
                    analysis = analysis,
                    onBack = {
                        viewModel.clearAnalysis()
                        screen = AppScreen.Conversation.name
                    },
                    onOpenSettings = { screen = AppScreen.Settings.name }
                )
            }
        }

        AppScreen.Settings -> {
            SettingsScreen(
                historyCount = uiState.history.size,
                notificationDraftCount = uiState.notificationDraftCount,
                privacyConsentAccepted = uiState.privacyConsentAccepted,
                notificationCaptureEnabled = uiState.notificationCaptureEnabled,
                accessibilityCaptureEnabled = uiState.accessibilityCaptureEnabled,
                accessibilityCapturePackage = uiState.accessibilityCapturePackage,
                accessibilityCaptureAtEpochMillis = uiState.accessibilityCaptureAtEpochMillis,
                accessibilityRawPreview = uiState.accessibilityRawPreview,
                accessibilityFilteredPreview = uiState.accessibilityFilteredPreview,
                onBack = {
                    screen = if (uiState.analysis != null) {
                        AppScreen.Result.name
                    } else {
                        AppScreen.Conversation.name
                    }
                },
                onNotificationCaptureChanged = viewModel::setNotificationCaptureEnabled,
                onAccessibilityCaptureChanged = viewModel::setAccessibilityCaptureEnabled,
                onRefreshAccessibilityPreview = viewModel::refreshAccessibilityCapturePreview,
                onClearAccessibilityPreview = viewModel::clearAccessibilityCapturePreview,
                onSyncSystemPermissionState = viewModel::syncSystemCapturePermissions,
                onRevokePrivacyConsent = viewModel::revokePrivacyConsent,
                onClearHistory = viewModel::clearHistory,
                onClearNotificationDrafts = viewModel::clearNotificationDrafts,
                onLogout = {
                    viewModel.logout()
                    screen = AppScreen.Login.name
                }
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
