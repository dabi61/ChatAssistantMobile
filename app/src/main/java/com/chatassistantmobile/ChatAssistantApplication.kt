package com.chatassistantmobile

import android.app.Application
import com.chatassistantmobile.di.AppContainer

class ChatAssistantApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }
}
