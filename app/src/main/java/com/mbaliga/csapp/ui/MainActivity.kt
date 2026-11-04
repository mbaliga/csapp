package com.mbaliga.csapp.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.mbaliga.csapp.CsAppApplication
import com.mbaliga.csapp.ui.nav.CsAppNavHost
import com.mbaliga.csapp.ui.theme.CsAppCrashRecoveryStyle
import com.mbaliga.csapp.ui.theme.CsAppTheme
import com.mbaliga.csapp.widget.WidgetTap
import dev.aarso.crashrecovery.CrashRecovery

class MainActivity : ComponentActivity() {
    /** Where a home-screen widget tap asked to land; consumed once the nav host has navigated. */
    private val widgetTap = mutableStateOf<WidgetTap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A fresh launch from a widget (not a re-creation after rotation, which keeps its back stack).
        if (savedInstanceState == null) widgetTap.value = tapOf(intent)

        // If the previous run crashed (or AppContainer failed to build during
        // Application.onCreate, which also lands a report via CrashRecovery.captureInitError),
        // show the shared recovery screen — NOT the app — instead of touching the (possibly
        // uninitialised) container. This finishes this Activity, so a device-only launch crash
        // can't brick the install.
        if (CrashRecovery.maybeShowRecovery(this, appLabel = "csapp", style = CsAppCrashRecoveryStyle)) return

        val container = (application as CsAppApplication).container
        setContent {
            CsAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CsAppNavHost(container, widgetTap = widgetTap.value, onWidgetTapHandled = { widgetTap.value = null })
                }
            }
            // Reached only if the nav host composed without throwing → clear the crash flag so
            // the next launch is normal. A composition crash skips this, keeping the flag set.
            LaunchedEffect(Unit) { CrashRecovery.clear(applicationContext) }
        }
    }

    /** A widget tap while the app is already open (CLEAR_TOP | SINGLE_TOP) arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tapOf(intent)?.let { widgetTap.value = it }
    }

    /** Reopening from Recents replays the original intent; that is not a new widget tap. */
    private fun tapOf(intent: Intent?): WidgetTap? =
        intent?.takeIf { it.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 }
            ?.let { WidgetTap.parse(it.getStringExtra(WidgetTap.EXTRA_DEST), it.getStringExtra(WidgetTap.EXTRA_ARG)) }
}
