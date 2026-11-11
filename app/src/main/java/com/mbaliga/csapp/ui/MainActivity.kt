package com.mbaliga.csapp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.mbaliga.csapp.CsAppApplication
import com.mbaliga.csapp.ui.nav.CsAppNavHost
import com.mbaliga.csapp.ui.nav.handleShortcut
import com.mbaliga.csapp.ui.theme.CsAppCrashRecoveryStyle
import com.mbaliga.csapp.ui.theme.CsAppTheme
import dev.aarso.crashrecovery.CrashRecovery

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the previous run crashed (or AppContainer failed to build during
        // Application.onCreate, which also lands a report via CrashRecovery.captureInitError),
        // show the shared recovery screen — NOT the app — instead of touching the (possibly
        // uninitialised) container. This finishes this Activity, so a device-only launch crash
        // can't brick the install.
        if (CrashRecovery.maybeShowRecovery(this, appLabel = "csapp", style = CsAppCrashRecoveryStyle)) return

        val container = (application as CsAppApplication).container
        setContent {
            val nav = rememberNavController()
            CsAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Cap content width so wide freeform/desktop windows do not stretch one column;
                    // a no-op at phone widths.
                    Box(
                        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { nav.handleShortcut(it) },
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(modifier = Modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth()) {
                            CsAppNavHost(container, nav)
                        }
                    }
                }
            }
            // Reached only if the nav host composed without throwing → clear the crash flag so
            // the next launch is normal. A composition crash skips this, keeping the flag set.
            LaunchedEffect(Unit) { CrashRecovery.clear(applicationContext) }
        }
    }
}
