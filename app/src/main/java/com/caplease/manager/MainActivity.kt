package com.caplease.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.caplease.manager.ui.ManagerApp
import com.caplease.manager.ui.theme.CapLeaseTheme
import com.caplease.manager.ui.theme.CapLeaseTypography

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CapLeaseTheme {
                MaterialTheme(typography = CapLeaseTypography) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ManagerApp()
                    }
                }
            }
        }
    }
}