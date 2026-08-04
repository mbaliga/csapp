package com.mbaliga.csapp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mbaliga.csapp.CsAppApplication
import com.mbaliga.csapp.ui.nav.CsAppNavHost
import com.mbaliga.csapp.ui.theme.CsAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CsAppApplication).container
        setContent {
            CsAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CsAppNavHost(container)
                }
            }
        }
    }
}
