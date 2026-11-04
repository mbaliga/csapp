package com.mbaliga.csapp

import android.app.Application
import android.content.res.Configuration
import com.mbaliga.csapp.di.AppContainer
import com.mbaliga.csapp.widget.CsWidgets
import com.mbaliga.csapp.work.PollScheduler
import dev.aarso.crashrecovery.CrashRecovery

private const val APP_LABEL = "csapp"

class CsAppApplication : Application() {

    lateinit var container: AppContainer
        private set

    /** The container, or null if it failed to build (widgets then show their error state). */
    val containerOrNull: AppContainer? get() = if (::container.isInitialized) container else null

    /** Widgets pick their layout by size and font scale; a font-scale change asks for a redraw. */
    private var fontScale = 0f

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
        if (::container.isInitialized) {
            PollScheduler.schedule(this, container.settingsStore.pollIntervalMinutes)
            // Home-screen widgets redraw whenever the data they show changes (push, not poll).
            CsWidgets.start(this, container.database)
        }
        fontScale = resources.configuration.fontScale
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.fontScale != fontScale) {
            fontScale = newConfig.fontScale
            CsWidgets.requestRefresh()
        }
    }
}
