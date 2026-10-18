package com.animator222.ytdownloader

import android.app.Application
import com.animator222.ytdownloader.download.Notifications
import com.animator222.ytdownloader.engine.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // Unpacking python/ffmpeg takes a few seconds on first launch, so start it right away.
        appScope.launch { runCatching { Engine.ensureReady(this@App) } }
    }
}
