package com.chatassistantmobile.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatassistantmobile.BuildConfig
import com.chatassistantmobile.data.auth.GoogleIdTokenProvider
import com.chatassistantmobile.data.auth.SessionStore
import com.chatassistantmobile.data.local.AnalysisHistoryStore
import com.chatassistantmobile.data.local.CurrentScreenCaptureStore
import com.chatassistantmobile.data.local.LatestAnalysisWidgetStore
import com.chatassistantmobile.data.local.NotificationDraftStore
import com.chatassistantmobile.data.local.PrivacyConsentStore
import com.chatassistantmobile.data.repository.AuthRepository
import com.chatassistantmobile.data.repository.ChatRepository
import com.chatassistantmobile.domain.model.AnalysisHistoryItem
import com.chatassistantmobile.domain.model.AnalysisUiModel
import com.chatassistantmobile.domain.usecase.ConversationParser
import com.chatassistantmobile.ui.widget.glance.WidgetSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.UUID

data class AppUiState(
    val isCheckingSession: Boolean = true,
    val isAuthenticated: Boolean = false,
    val isSigningIn: Boolean = false,
    val loginError: String? = null,
    val conversationInput: String = "",
    val selectedRole: String = "friend",
    val isAnalyzing: Boolean = false,
    val analyzeError: String? = null,
    val analysis: AnalysisUiModel? = null,
    val history: List<AnalysisHistoryItem> = emptyList(),
    val notificationDraftCount: Int = 0,
    val notificationStatus: String? = null,
    val privacyConsentAccepted: Boolean = false,
    val notificationCaptureEnabled: Boolean = false,
    val accessibilityCaptureEnabled: Boolean = false,
    val accessibilityCapturePackage: String = "",
    val accessibilityCaptureAtEpochMillis: Long = 0L,
    val accessibilityRawPreview: String = "",
    val accessibilityFilteredPreview: String = ""
)

class AppViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val sessionStore: SessionStore,
    private val analysisHistoryStore: AnalysisHistoryStore,
    private val currentScreenCaptureStore: CurrentScreenCaptureStore,
    private val notificationDraftStore: NotificationDraftStore,
    private val latestAnalysisWidgetStore: LatestAnalysisWidgetStore,
    private val privacyConsentStore: PrivacyConsentStore,
    private val widgetSyncManager: WidgetSyncManager,
    private val googleIdTokenProvider: GoogleIdTokenProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        bootstrapSession()
    }

    fun onConversationInputChanged(value: String) {
        _uiState.update {
            it.copy(
                conversationInput = value,
                analyzeError = null,
                notificationStatus = null
            )
        }
    }

    fun onRoleSelected(value: String) {
        _uiState.update { it.copy(selectedRole = value, analyzeError = null) }
    }

    fun clearAnalysis() {
        _uiState.update { it.copy(analysis = null, analyzeError = null) }
    }

    fun clearLoginError() {
        _uiState.update { it.copy(loginError = null) }
    }

    fun clearHistory() {
        analysisHistoryStore.clear()
        latestAnalysisWidgetStore.clear()
        viewModelScope.launch {
            runCatching {
                widgetSyncManager.refreshLatestSuggestionWidget()
            }
        }
        _uiState.update { it.copy(history = emptyList()) }
    }

    fun refreshPrivacyState() {
        _uiState.update {
            it.copy(
                privacyConsentAccepted = privacyConsentStore.isPrivacyAccepted(),
                notificationCaptureEnabled = privacyConsentStore.isNotificationCaptureEnabled(),
                accessibilityCaptureEnabled = privacyConsentStore.isAccessibilityCaptureEnabled()
            )
        }
    }

    fun acceptPrivacyConsent() {
        privacyConsentStore.setPrivacyAccepted(true)
        refreshPrivacyState()
    }

    fun setNotificationCaptureEnabled(enabled: Boolean) {
        privacyConsentStore.setNotificationCaptureEnabled(enabled)
        refreshPrivacyState()
        if (!enabled) {
            clearNotificationDrafts()
        }
    }

    fun setAccessibilityCaptureEnabled(enabled: Boolean) {
        privacyConsentStore.setAccessibilityCaptureEnabled(enabled)
        refreshPrivacyState()
    }

    fun syncSystemCapturePermissions(
        notificationListenerEnabled: Boolean,
        accessibilityServiceEnabled: Boolean
    ) {
        val desiredNotification = privacyConsentStore.isNotificationCaptureEnabled()
        val desiredAccessibility = privacyConsentStore.isAccessibilityCaptureEnabled()

        if (!notificationListenerEnabled && desiredNotification) {
            privacyConsentStore.setNotificationCaptureEnabled(false)
        }
        if (!accessibilityServiceEnabled && desiredAccessibility) {
            privacyConsentStore.setAccessibilityCaptureEnabled(false)
        }

        _uiState.update {
            it.copy(
                notificationCaptureEnabled = privacyConsentStore.isNotificationCaptureEnabled(),
                accessibilityCaptureEnabled = privacyConsentStore.isAccessibilityCaptureEnabled()
            )
        }

        if (!notificationListenerEnabled) {
            notificationDraftStore.clear()
            _uiState.update { state ->
                state.copy(notificationDraftCount = 0)
            }
        }
    }

    fun revokePrivacyConsent() {
        privacyConsentStore.clearAll()
        notificationDraftStore.clear()
        currentScreenCaptureStore.clear()
        analysisHistoryStore.clear()
        latestAnalysisWidgetStore.clear()
        viewModelScope.launch {
            runCatching {
                widgetSyncManager.refreshLatestSuggestionWidget()
            }
        }
        _uiState.update {
            it.copy(
                privacyConsentAccepted = false,
                notificationCaptureEnabled = false,
                accessibilityCaptureEnabled = false,
                notificationDraftCount = 0,
                notificationStatus = "Privacy consent revoked.",
                history = emptyList(),
                accessibilityCapturePackage = "",
                accessibilityCaptureAtEpochMillis = 0L,
                accessibilityRawPreview = "",
                accessibilityFilteredPreview = ""
            )
        }
    }

    fun refreshNotificationDrafts() {
        val count = if (_uiState.value.notificationCaptureEnabled) {
            notificationDraftStore.readAll().size
        } else {
            0
        }
        _uiState.update { it.copy(notificationDraftCount = count, notificationStatus = null) }
    }

    fun refreshAccessibilityCapturePreview() {
        _uiState.update {
            it.copy(
                accessibilityCapturePackage = currentScreenCaptureStore.readPackageName(),
                accessibilityCaptureAtEpochMillis = currentScreenCaptureStore.readCapturedAtEpochMillis(),
                accessibilityRawPreview = currentScreenCaptureStore.readRawCapturedText(),
                accessibilityFilteredPreview = currentScreenCaptureStore.readFilteredCapturedText()
            )
        }
    }

    fun clearAccessibilityCapturePreview() {
        currentScreenCaptureStore.clear()
        refreshAccessibilityCapturePreview()
    }

    fun importNotificationDrafts() {
        if (!_uiState.value.privacyConsentAccepted || !_uiState.value.notificationCaptureEnabled) {
            _uiState.update {
                it.copy(
                    notificationStatus = "Enable privacy consent and notification capture first."
                )
            }
            return
        }

        val draft = notificationDraftStore.toConversationDraft()
        if (draft.isBlank()) {
            _uiState.update {
                it.copy(
                    notificationDraftCount = 0,
                    notificationStatus = "No chat notifications captured yet."
                )
            }
            return
        }

        val existing = _uiState.value.conversationInput.trim()
        val merged = if (existing.isBlank()) {
            draft
        } else {
            "$existing\\n$draft"
        }
        val importedLines = draft.lineSequence().count()
        _uiState.update {
            it.copy(
                conversationInput = merged,
                notificationDraftCount = notificationDraftStore.readAll().size,
                notificationStatus = "Imported $importedLines lines from notifications.",
                analyzeError = null
            )
        }
    }

    fun clearNotificationDrafts() {
        notificationDraftStore.clear()
        _uiState.update {
            it.copy(
                notificationDraftCount = 0,
                notificationStatus = "Notification drafts cleared."
            )
        }
    }

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningIn = true, loginError = null) }

            val idTokenResult = googleIdTokenProvider.getGoogleIdToken(
                activity = activity,
                serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
            )

            if (idTokenResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isSigningIn = false,
                        loginError = mapError(idTokenResult.exceptionOrNull())
                    )
                }
                return@launch
            }

            val loginResult = authRepository.exchangeGoogleIdToken(idTokenResult.getOrThrow())
            _uiState.update {
                if (loginResult.isSuccess) {
                    it.copy(
                        isSigningIn = false,
                        isAuthenticated = true,
                        loginError = null,
                        history = analysisHistoryStore.readAll(),
                        notificationDraftCount = if (privacyConsentStore.canCaptureNotifications()) {
                            notificationDraftStore.readAll().size
                        } else {
                            0
                        },
                        privacyConsentAccepted = privacyConsentStore.isPrivacyAccepted(),
                        notificationCaptureEnabled = privacyConsentStore.isNotificationCaptureEnabled(),
                        accessibilityCaptureEnabled = privacyConsentStore.isAccessibilityCaptureEnabled(),
                        accessibilityCapturePackage = currentScreenCaptureStore.readPackageName(),
                        accessibilityCaptureAtEpochMillis = currentScreenCaptureStore.readCapturedAtEpochMillis(),
                        accessibilityRawPreview = currentScreenCaptureStore.readRawCapturedText(),
                        accessibilityFilteredPreview = currentScreenCaptureStore.readFilteredCapturedText()
                    )
                } else {
                    it.copy(
                        isSigningIn = false,
                        isAuthenticated = false,
                        loginError = mapError(loginResult.exceptionOrNull())
                    )
                }
            }
        }
    }

    suspend fun analyzeConversation(): Boolean {
        val snapshot = _uiState.value
        val parsedMessages = ConversationParser.parse(snapshot.conversationInput)
        if (parsedMessages.isEmpty()) {
            _uiState.update {
                it.copy(analyzeError = "No valid messages found. Add lines or use me:/other: prefix.")
            }
            return false
        }

        _uiState.update { it.copy(isAnalyzing = true, analyzeError = null) }
        val result = chatRepository.analyze(
            relationshipRole = snapshot.selectedRole,
            chatHistory = parsedMessages
        )

        return if (result.isSuccess) {
            val analysis = result.getOrThrow()
            val latestHistory = saveHistoryAndWidget(
                relationshipRole = snapshot.selectedRole,
                analysis = analysis
            )
            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    analyzeError = null,
                    analysis = analysis,
                    history = latestHistory
                )
            }
            true
        } else {
            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    analyzeError = mapError(result.exceptionOrNull())
                )
            }
            false
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            latestAnalysisWidgetStore.clear()
            runCatching {
                widgetSyncManager.refreshLatestSuggestionWidget()
            }
            _uiState.update {
                AppUiState(
                    isCheckingSession = false,
                    isAuthenticated = false
                )
            }
        }
    }

    private fun bootstrapSession() {
        val isAuthenticated = authRepository.hasActiveSession()
        if (!isAuthenticated) {
            sessionStore.clear()
        }
        _uiState.update {
            it.copy(
                isCheckingSession = false,
                isAuthenticated = isAuthenticated,
                history = analysisHistoryStore.readAll(),
                notificationDraftCount = if (privacyConsentStore.canCaptureNotifications()) {
                    notificationDraftStore.readAll().size
                } else {
                    0
                },
                privacyConsentAccepted = privacyConsentStore.isPrivacyAccepted(),
                notificationCaptureEnabled = privacyConsentStore.isNotificationCaptureEnabled(),
                accessibilityCaptureEnabled = privacyConsentStore.isAccessibilityCaptureEnabled(),
                accessibilityCapturePackage = currentScreenCaptureStore.readPackageName(),
                accessibilityCaptureAtEpochMillis = currentScreenCaptureStore.readCapturedAtEpochMillis(),
                accessibilityRawPreview = currentScreenCaptureStore.readRawCapturedText(),
                accessibilityFilteredPreview = currentScreenCaptureStore.readFilteredCapturedText()
            )
        }
    }

    private suspend fun saveHistoryAndWidget(
        relationshipRole: String,
        analysis: AnalysisUiModel
    ): List<AnalysisHistoryItem> {
        val summary = analysis.summary
            ?.takeIf { it.isNotBlank() }
            ?: "No summary"
        val sampleReply = analysis.suggestions
            .firstOrNull { it.sampleReply.isNotBlank() }
            ?.sampleReply
            ?: ""

        analysisHistoryStore.save(
            AnalysisHistoryItem(
                id = UUID.randomUUID().toString(),
                createdAtEpochMillis = System.currentTimeMillis(),
                relationshipRole = relationshipRole,
                summary = summary,
                sampleReply = sampleReply
            )
        )
        latestAnalysisWidgetStore.save(summary = summary, sampleReply = sampleReply)
        runCatching {
            widgetSyncManager.refreshLatestSuggestionWidget()
        }
        return analysisHistoryStore.readAll()
    }

    private fun mapError(throwable: Throwable?): String {
        val message = throwable?.message.orEmpty()
        if (message.contains("10:", ignoreCase = true) ||
            message.contains("developer console", ignoreCase = true) ||
            message.contains("DEVELOPER_ERROR", ignoreCase = true)
        ) {
            return "Google Sign-In config mismatch (code 10). Check package name, SHA-1/SHA-256, and GOOGLE_WEB_CLIENT_ID."
        }

        val code = (throwable as? HttpException)?.code()
        return when (code) {
            401 -> "Session expired. Sign in again."
            429 -> "Too many requests. Please wait and retry."
            500 -> "Backend internal error. Check server configuration."
            502, 504 -> "Upstream AI timeout. Retry in a few seconds."
            else -> throwable?.message ?: "Unexpected error"
        }
    }
}
