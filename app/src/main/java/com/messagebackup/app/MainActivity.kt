package com.messagebackup.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.messagebackup.app.theme.MessageBackupAppTheme
import com.messagebackup.app.ui.main.MainScreen
import com.messagebackup.app.ui.main.MainViewModel

/**
 * Single-Activity host for the Message Backup & Export application.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val appTheme by viewModel.prefs.appTheme.collectAsState()
            val isDark = when (appTheme) {
                com.messagebackup.app.model.ExportTheme.DARK -> true
                com.messagebackup.app.model.ExportTheme.LIGHT -> false
                com.messagebackup.app.model.ExportTheme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            MessageBackupAppTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
