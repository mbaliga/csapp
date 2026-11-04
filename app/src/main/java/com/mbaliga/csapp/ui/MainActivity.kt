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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                    // Cap content width so wide freeform/desktop windows do not stretch one column;
                    // a no-op at phone widths.
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(modifier = Modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth()) {
                            CsAppNavHost(container)
                        }
                    }
                }
            }
        }
    }
}
