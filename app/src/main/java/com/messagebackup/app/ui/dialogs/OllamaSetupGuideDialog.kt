package com.messagebackup.app.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.messagebackup.app.domain.ai.AiClientEngine
import kotlinx.coroutines.launch

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

/**
 * Interactive setup guide for running Ollama + Tailscale for 100% private, local AI.
 */
@Composable
fun OllamaSetupGuideDialog(
    currentEndpoint: String,
    currentModel: String = "llama3.2",
    onSaveEndpoint: (endpoint: String, model: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiEngine = remember { AiClientEngine() }

    var testEndpoint by remember { mutableStateOf(currentEndpoint) }
    var selectedModel by remember { mutableStateOf(currentModel.ifBlank { "llama3.2" }) }
    var isTesting by remember { mutableStateOf(false) }
    var testResultMsg by remember { mutableStateOf<String?>(null) }
    var detectedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModelDropdown by remember { mutableStateOf(false) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lan,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Ollama + Tailscale Guide",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = "Run a free AI model on your home PC and chat with your messages privately over your secure Tailscale mesh network with zero cloud leaks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Step 1
                StepHeader(number = "1", title = "Install Ollama & Download a Model")
                Text(
                    text = "Download Ollama from ollama.com on your computer (Windows, Mac, or Linux). Open your terminal/PowerShell and run:",
                    style = MaterialTheme.typography.bodySmall
                )
                CodeBox(
                    code = "ollama run llama3.2",
                    onCopy = { copyToClipboard("Step 1 Command", "ollama run llama3.2") }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Step 2
                StepHeader(number = "2", title = "Allow Tailscale / Network Access")
                Text(
                    text = "By default, Ollama only listens locally. Tell it to accept network requests from your phone's Tailscale connection:",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Windows (PowerShell as Admin):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                CodeBox(
                    code = "[System.Environment]::SetEnvironmentVariable('OLLAMA_HOST', '0.0.0.0:11434', 'User')",
                    onCopy = {
                        copyToClipboard(
                            "Windows Command",
                            "[System.Environment]::SetEnvironmentVariable('OLLAMA_HOST', '0.0.0.0:11434', 'User')"
                        )
                    }
                )

                Text(
                    text = "Mac / Linux (Terminal):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                CodeBox(
                    code = "export OLLAMA_HOST=0.0.0.0:11434 && ollama serve",
                    onCopy = {
                        copyToClipboard("Mac/Linux Command", "export OLLAMA_HOST=0.0.0.0:11434 && ollama serve")
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Step 3
                StepHeader(number = "3", title = "Connect via Tailscale")
                Text(
                    text = "Install Tailscale on both your PC and this phone. Copy your PC's Tailscale IP (e.g. 100.85.12.34) and enter it below with port 11434.",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = testEndpoint,
                    onValueChange = { testEndpoint = it },
                    label = { Text("Ollama Host URL") },
                    placeholder = { Text("http://100.x.y.z:11434") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Model Selector (Dropdown when models are detected, or editable field with dropdown icon)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = { selectedModel = it },
                        label = {
                            Text(
                                if (detectedModels.isNotEmpty()) {
                                    "Active Model (${detectedModels.size} found — tap arrow to pick)"
                                } else {
                                    "Ollama Model Name"
                                }
                            )
                        },
                        placeholder = { Text("llama3.2") },
                        singleLine = true,
                        trailingIcon = {
                            if (detectedModels.isNotEmpty()) {
                                IconButton(onClick = { showModelDropdown = !showModelDropdown }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select detected model",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    DropdownMenu(
                        expanded = showModelDropdown && detectedModels.isNotEmpty(),
                        onDismissRequest = { showModelDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.78f)
                    ) {
                        detectedModels.forEach { modelName ->
                            val isSelected = modelName.equals(selectedModel, ignoreCase = true)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = modelName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else null,
                                onClick = {
                                    selectedModel = modelName
                                    showModelDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Step 4: Test Connection & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testResultMsg = null
                            detectedModels = emptyList()
                            scope.launch {
                                val result = aiEngine.testOllamaConnection(testEndpoint)
                                isTesting = false
                                result.onSuccess { models ->
                                    detectedModels = models
                                    if (models.isNotEmpty()) {
                                        // Automatically select first detected model if current selection isn't in list
                                        val match = models.firstOrNull {
                                            it.equals(selectedModel, ignoreCase = true) ||
                                                it.startsWith("$selectedModel:", ignoreCase = true)
                                        }
                                        selectedModel = match ?: models.first()
                                        if (models.size > 1) {
                                            showModelDropdown = true
                                        }
                                    }
                                    testResultMsg = if (models.isNotEmpty()) {
                                        "Connected! Found ${models.size} model(s). Selected: $selectedModel"
                                    } else {
                                        "Connected to Ollama, but no models are installed yet. Run 'ollama pull llama3.2' on your PC."
                                    }
                                }.onFailure { err ->
                                    testResultMsg = err.message
                                }
                            }
                        },
                        enabled = !isTesting && testEndpoint.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Test Connection")
                        }
                    }

                    Button(
                        onClick = {
                            val finalModel = selectedModel.trim().ifBlank { "llama3.2" }
                            onSaveEndpoint(testEndpoint.trim(), finalModel)
                            Toast.makeText(
                                context,
                                "Saved Ollama endpoint & model ($finalModel)!",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        },
                        enabled = testEndpoint.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save & Use")
                    }
                }

                testResultMsg?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (detectedModels.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StepHeader(number: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun CodeBox(code: String, onCopy: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
