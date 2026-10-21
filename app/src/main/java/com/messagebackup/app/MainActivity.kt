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
import androidx.lifecycle.lifecycleScope
import com.messagebackup.app.observer.TelephonyContentObserver
import com.messagebackup.app.theme.MessageBackupAppTheme
import com.messagebackup.app.ui.main.MainScreen
import com.messagebackup.app.ui.main.MainViewModel

/**
 * Single-Activity host for the Muninn application.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var telephonyObserver: TelephonyContentObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        registerTelephonyObserver()
        handleIntentAction(intent)

        // Keep Raven Mesh & Global Cellular Relay alive across app sessions for instant out-of-state links
        com.messagebackup.app.domain.mesh.RavenSocketTransport.getInstance(applicationContext)

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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentAction(intent)
    }

    private fun handleIntentAction(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra("OPEN_RUNIC_ARCADE", false) == true) {
            viewModel.setShowRewardArcade(true)
        }
        when (intent?.action) {
            com.messagebackup.app.widget.OdinSearchWidgetProvider.ACTION_OPEN_ODIN -> {
                viewModel.setCopilotOpen(true)
            }
            com.messagebackup.app.widget.FullInsightsWidgetProvider.ACTION_OPEN_INSIGHTS -> {
                viewModel.loadStatistics(forceRefresh = false)
                viewModel.setShowStatsDialog(true)
            }
            com.messagebackup.app.widget.QuickActionsWidgetProvider.ACTION_TRIGGER_BACKUP -> {
                viewModel.setShowExportDialog(true)
            }
            com.messagebackup.app.widget.QuickActionsWidgetProvider.ACTION_OPEN_VAULT -> {
                // Open export history / directory picker
                viewModel.setSettingsOpen(true)
            }
        }
    }

    private fun registerTelephonyObserver() {
        if (telephonyObserver == null) {
            telephonyObserver = TelephonyContentObserver(lifecycleScope) {
                viewModel.onTelephonyContentChanged()
            }
            try {
                contentResolver.registerContentObserver(
                    TelephonyContentObserver.SMS_URI,
                    true,
                    telephonyObserver!!
                )
                contentResolver.registerContentObserver(
                    TelephonyContentObserver.MMS_URI,
                    true,
                    telephonyObserver!!
                )
            } catch (e: Exception) {
                // Ignore permission or initialization errors until granted
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        telephonyObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {}
        }
    }
}
