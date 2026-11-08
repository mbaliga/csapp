package com.asoc.ping

import android.app.Application
import com.asoc.ping.di.AppContainer
import com.asoc.ping.work.PollScheduler
import dev.aarso.crashrecovery.CrashRecovery

private const val APP_LABEL = "Ping"

class CsAppApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        // Install crash capture FIRST, so even a failure in container construction below (or a
        // later first-frame/Compose crash) lands a readable trace for the recovery screen.
        // Local-only: writes to this app's private files dir, never sent anywhere.
        CrashRecovery.install(this, appLabel = APP_LABEL)
        super.onCreate()
        // Don't let an init failure brick the process silently — record it and let MainActivity
        // show the recovery screen instead.
        try {
            container = AppContainer(this)
        } catch (e: Throwable) {
            CrashRecovery.captureInitError(this, appLabel = APP_LABEL, e)
        }
        if (::container.isInitialized) PollScheduler.schedule(this, container.settingsStore.pollIntervalMinutes)
    }
}
