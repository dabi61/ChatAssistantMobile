package com.chatassistantmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.chatassistantmobile.ui.AppViewModel
import com.chatassistantmobile.ui.AppViewModelFactory
import com.chatassistantmobile.ui.ChatAssistantMobileApp
import com.chatassistantmobile.ui.theme.ChatAssistantTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels {
        val app = application as ChatAssistantApplication
        AppViewModelFactory(app.appContainer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatAssistantMobileApp(viewModel = appViewModel)
                }
            }
        }
    }
}
