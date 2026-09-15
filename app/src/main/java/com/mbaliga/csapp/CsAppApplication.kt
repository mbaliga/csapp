package com.mbaliga.csapp

import android.app.Application
import com.mbaliga.csapp.di.AppContainer
import com.mbaliga.csapp.work.PollScheduler
import dev.aarso.crashrecovery.CrashRecovery

private const val APP_LABEL = "csapp"

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
        PollScheduler.scheduleIfNotAlreadyScheduled(this)
    }
}
