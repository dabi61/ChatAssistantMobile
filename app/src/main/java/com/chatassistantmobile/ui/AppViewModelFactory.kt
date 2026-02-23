package com.chatassistantmobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.chatassistantmobile.di.AppContainer

class AppViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            return AppViewModel(
                authRepository = appContainer.authRepository,
                chatRepository = appContainer.chatRepository,
                sessionStore = appContainer.sessionStore,
                analysisHistoryStore = appContainer.analysisHistoryStore,
                currentScreenCaptureStore = appContainer.currentScreenCaptureStore,
                notificationDraftStore = appContainer.notificationDraftStore,
                latestAnalysisWidgetStore = appContainer.latestAnalysisWidgetStore,
                privacyConsentStore = appContainer.privacyConsentStore,
                widgetSyncManager = appContainer.widgetSyncManager,
                googleIdTokenProvider = appContainer.googleIdTokenProvider
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
