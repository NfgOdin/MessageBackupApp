package com.messagebackup.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.messagebackup.app.observer.TelephonyContentObserver
import com.messagebackup.app.theme.MessageBackupAppTheme
import com.messagebackup.app.ui.main.MainScreen
import com.messagebackup.app.ui.main.MainViewModel

/**
 * Single-Activity host for the Muninn application with Biometric Vault Lock protection.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var telephonyObserver: TelephonyContentObserver? = null
    private var isVaultUnlocked by mutableStateOf(false)

    companion object {
        private const val KEY_VAULT_UNLOCKED = "muninn_vault_unlocked_session"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        registerTelephonyObserver()
        handleIntentAction(intent)

        // Keep Raven Mesh & Global Cellular Relay alive across app sessions for instant out-of-state links
        com.messagebackup.app.domain.mesh.RavenSocketTransport.getInstance(applicationContext)

        val wasUnlocked = savedInstanceState?.getBoolean(KEY_VAULT_UNLOCKED, false) ?: false
        if (!viewModel.prefs.appLockEnabled.value || wasUnlocked) {
            isVaultUnlocked = true
        } else {
            promptBiometricUnlock()
        }

        setContent {
            val appTheme by viewModel.prefs.appTheme.collectAsState()
            val isDark = when (appTheme) {
                com.messagebackup.app.model.ExportTheme.DARK -> true
                com.messagebackup.app.model.ExportTheme.LIGHT -> false
                com.messagebackup.app.model.ExportTheme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            val appLockActive by viewModel.prefs.appLockEnabled.collectAsState()

            MessageBackupAppTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (appLockActive && !isVaultUnlocked) {
                        MuninnVaultLockScreen(
                            onUnlockRequest = { promptBiometricUnlock() },
                            onPinUnlock = { enteredPin ->
                                if (viewModel.prefs.verifyPrivacyPin(enteredPin)) {
                                    isVaultUnlocked = true
                                    true
                                } else {
                                    false
                                }
                            },
                            hasPinSet = viewModel.prefs.hasPrivacyPin()
                        )
                    } else {
                        MainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    private fun promptBiometricUnlock() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isVaultUnlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                        errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE ||
                        errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT
                    ) {
                        if (!viewModel.prefs.hasPrivacyPin()) {
                            isVaultUnlocked = true
                        }
                    }
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Muninn Vault Security")
            .setSubtitle("Confirm your biometric identity or PIN to access your message archives")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (_: Exception) {
            if (!viewModel.prefs.hasPrivacyPin()) {
                isVaultUnlocked = true
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
            } catch (_: Exception) {
                // Ignore permission or initialization errors until granted
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_VAULT_UNLOCKED, isVaultUnlocked)
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

/**
 * Fullscreen Vault Security Lock overlay preventing unauthorized view of messages.
 */
@Composable
fun MuninnVaultLockScreen(
    onUnlockRequest: () -> Unit,
    onPinUnlock: (String) -> Boolean,
    hasPinSet: Boolean
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Vault Locked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Muninn Vault Locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Biometric authentication or security PIN is required to open your private message archives.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onUnlockRequest,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Unlock with Biometrics",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            if (hasPinSet) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        pinText = ""
                        pinError = null
                        showPinDialog = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Pin,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enter Security PIN",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = {
                Text(
                    text = "Enter Security PIN",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your Muninn vault security PIN to unlock.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = {
                            pinText = it
                            pinError = null
                        },
                        label = { Text("4 to 8 Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pinError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pinError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onPinUnlock(pinText)) {
                            showPinDialog = false
                        } else {
                            pinError = "Incorrect PIN. Please try again."
                        }
                    }
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
