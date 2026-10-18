package com.messagebackup.app.ui.dialogs

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.messagebackup.app.data.preferences.AppPreferences

/**
 * Privacy Lock Screen / Modal supporting both:
 * 1. Custom SHA-256 hashed Privacy PIN
 * 2. Native Android Device Biometric / Screen Lock (Fingerprint, Face Unlock, or Device PIN via KeyguardManager)
 */
@Composable
fun PrivacyUnlockDialog(
    title: String,
    subtitle: String,
    prefs: AppPreferences,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val keyguardManager = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager }
    val isDeviceSecure = remember { keyguardManager?.isDeviceSecure == true }
    val hasCustomPin = prefs.hasPrivacyPin()

    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val deviceCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onUnlocked()
        }
    }

    fun launchDeviceBiometric() {
        @Suppress("DEPRECATION")
        val intent = keyguardManager?.createConfirmDeviceCredentialIntent(
            title,
            "Verify your identity with Fingerprint, Face, or Device Screen Lock"
        )
        if (intent != null) {
            deviceCredentialLauncher.launch(intent)
        } else {
            Toast.makeText(context, "No device screen lock configured in Android Settings.", Toast.LENGTH_SHORT).show()
        }
    }

    // If user enabled lock without setting a custom PIN and device has biometric/screen lock, trigger automatically
    LaunchedEffect(Unit) {
        if (!hasCustomPin && isDeviceSecure) {
            launchDeviceBiometric()
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Privacy Lock",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (hasCustomPin) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it
                            errorMessage = null
                        },
                        label = { Text("Enter Privacy PIN") },
                        placeholder = { Text("••••") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )

                    errorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (prefs.verifyPrivacyPin(pinInput)) {
                                onUnlocked()
                            } else {
                                errorMessage = "Incorrect Privacy PIN. Please try again."
                            }
                        },
                        enabled = pinInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Unlock with PIN")
                    }
                }

                if (isDeviceSecure) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { launchDeviceBiometric() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use Fingerprint / Device Lock")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}

/**
 * Dialog for creating or updating the user's SHA-256 hashed Privacy PIN.
 */
@Composable
fun SetPrivacyPinDialog(
    prefs: AppPreferences,
    onPinSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text(if (prefs.hasPrivacyPin()) "Change Privacy PIN" else "Set Privacy PIN")
        },
        text = {
            Column {
                Text(
                    text = "Set a 4 to 8 digit Privacy PIN. Your PIN is salted and hashed with SHA-256 on-device and never stored in plaintext.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newPin,
                    onValueChange = {
                        if (it.length <= 8 && it.all { ch -> ch.isDigit() }) {
                            newPin = it
                            errorMsg = null
                        }
                    },
                    label = { Text("New PIN (4-8 digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        if (it.length <= 8 && it.all { ch -> ch.isDigit() }) {
                            confirmPin = it
                            errorMsg = null
                        }
                    },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                errorMsg?.let { err ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        newPin.length < 4 -> errorMsg = "PIN must be at least 4 digits."
                        newPin != confirmPin -> errorMsg = "PINs do not match."
                        else -> {
                            prefs.setPrivacyPin(newPin)
                            Toast.makeText(context, "🔒 Privacy PIN saved!", Toast.LENGTH_SHORT).show()
                            onPinSaved()
                        }
                    }
                }
            ) {
                Text("Save PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
