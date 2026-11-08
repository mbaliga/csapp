package com.asoc.ping.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.asoc.ping.CsAppApplication
import com.asoc.ping.ui.nav.CsAppNavHost
import com.asoc.ping.ui.theme.CsAppCrashRecoveryStyle
import com.asoc.ping.ui.theme.CsAppTheme
import dev.aarso.crashrecovery.CrashRecovery

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the previous run crashed (or AppContainer failed to build during
        // Application.onCreate, which also lands a report via CrashRecovery.captureInitError),
        // show the shared recovery screen — NOT the app — instead of touching the (possibly
        // uninitialised) container. This finishes this Activity, so a device-only launch crash
        // can't brick the install.
        if (CrashRecovery.maybeShowRecovery(this, appLabel = "Ping", style = CsAppCrashRecoveryStyle)) return

        val container = (application as CsAppApplication).container
        setContent {
            CsAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CsAppNavHost(container)
                }
            }
            // Reached only if the nav host composed without throwing → clear the crash flag so
            // the next launch is normal. A composition crash skips this, keeping the flag set.
            LaunchedEffect(Unit) { CrashRecovery.clear(applicationContext) }
        }
    }
}
