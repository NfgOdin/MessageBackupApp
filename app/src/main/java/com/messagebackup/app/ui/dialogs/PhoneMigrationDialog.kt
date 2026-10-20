package com.messagebackup.app.ui.dialogs

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.messagebackup.app.domain.migration.DirectLocalMigrationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * QR Phone-to-Phone Direct Local Migration Dialog.
 * Enables zero-cable, direct Wi-Fi/Hotspot migration between two phones.
 *
 * Sender Mode:
 * - Packages selected or latest backup file.
 * - Displays a live QR Code with the local IP address and connection string.
 * - Shows real-time transfer percentage while sending.
 *
 * Receiver Mode:
 * - Enter sender IP or host string (or scanned from QR).
 * - Downloads backup archive at full local Wi-Fi speed.
 * - Automatically hands off to Restore workflow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneMigrationDialog(
    latestExportFile: File?,
    onPrepareExport: (onReady: (File) -> Unit) -> Unit,
    onBackupReceivedForRestore: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Send (Old Phone), 1: Receive (New Phone)

    // Sender State
    var senderIp by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isServing by remember { mutableStateOf(false) }
    var senderProgressPct by remember { mutableFloatStateOf(0f) }
    var senderStatusMessage by remember { mutableStateOf("Preparing migration...") }
    var transferComplete by remember { mutableStateOf(false) }

    // Receiver State
    var targetAddressInput by remember { mutableStateOf("") }
    var isReceiving by remember { mutableStateOf(false) }
    var receiveProgressPct by remember { mutableFloatStateOf(0f) }
    var receiveStatusMessage by remember { mutableStateOf("") }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            val ip = DirectLocalMigrationManager.getLocalIpAddress()
            senderIp = ip
            if (ip != null) {
                val connectionString = "${DirectLocalMigrationManager.PROTOCOL_PREFIX}$ip:${DirectLocalMigrationManager.DEFAULT_PORT}"
                qrBitmap = DirectLocalMigrationManager.generateMigrationQrBitmap(connectionString)

                onPrepareExport { readyFile ->
                    isServing = true
                    senderStatusMessage = "Waiting for New Phone to connect..."
                    scope.launch {
                        val result = DirectLocalMigrationManager.serveBackupFile(
                            file = readyFile,
                            port = DirectLocalMigrationManager.DEFAULT_PORT,
                            onClientConnected = { clientIp ->
                                senderStatusMessage = "Transferring to $clientIp..."
                            },
                            onProgress = { sent, total ->
                                if (total > 0) {
                                    senderProgressPct = sent.toFloat() / total.toFloat()
                                    senderStatusMessage = "Sending: ${(sent / 1024)} KB / ${(total / 1024)} KB (${(senderProgressPct * 100).toInt()}%)"
                                }
                            }
                        )
                        if (result.isSuccess) {
                            transferComplete = true
                            senderStatusMessage = "Transfer successfully completed!"
                        } else {
                            senderStatusMessage = "Transfer failed or timed out: ${result.exceptionOrNull()?.message}"
                        }
                        isServing = false
                    }
                }
            } else {
                senderStatusMessage = "Please connect to a Wi-Fi network or enable your Mobile Hotspot."
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler {
            onDismiss()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Direct Phone Migration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Old Phone (Send)")
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Phone (Receive)")
                            }
                        }
                    )
                }

                if (selectedTab == 0) {
                    // Sender UI (Old Phone)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = "Send Messages to New Phone",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "1. Connect both phones to the same Wi-Fi (or turn on Hotspot on this phone).\n2. Open Message Backup App on your new phone and switch to 'Receive'.\n3. Enter the IP shown below or scan the QR Code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (qrBitmap != null) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Image(
                                    bitmap = qrBitmap!!.asImageBitmap(),
                                    contentDescription = "Migration QR Code",
                                    modifier = Modifier
                                        .size(240.dp)
                                        .padding(12.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Direct Migration IP Address",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${senderIp ?: "Unknown"}:${DirectLocalMigrationManager.DEFAULT_PORT}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (senderProgressPct > 0f) {
                            LinearProgressIndicator(
                                progress = { senderProgressPct },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }

                        Text(
                            text = senderStatusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (transferComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    // Receiver UI (New Phone)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = "Receive Messages on New Phone",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Enter the IP address shown on your old phone to stream and import your conversation archives directly at high Wi-Fi speeds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        OutlinedTextField(
                            value = targetAddressInput,
                            onValueChange = { targetAddressInput = it },
                            label = { Text("Old Phone IP Address") },
                            placeholder = { Text("e.g. 192.168.1.50 or 192.168.1.50:8998") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                val clean = targetAddressInput.trim()
                                    .removePrefix(DirectLocalMigrationManager.PROTOCOL_PREFIX)
                                val parts = clean.split(":")
                                val host = parts.getOrNull(0)?.trim()
                                val port = parts.getOrNull(1)?.toIntOrNull() ?: DirectLocalMigrationManager.DEFAULT_PORT

                                if (host.isNullOrBlank()) {
                                    Toast.makeText(context, "Please enter a valid IP address", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isReceiving = true
                                receiveStatusMessage = "Connecting to $host:$port..."
                                scope.launch {
                                    val destinationFile = File(context.cacheDir, "migrated_backup_${System.currentTimeMillis()}.zip")
                                    val result = DirectLocalMigrationManager.receiveBackupFile(
                                        host = host,
                                        port = port,
                                        outputDestinationFile = destinationFile,
                                        onProgress = { received, total ->
                                            if (total > 0) {
                                                receiveProgressPct = received.toFloat() / total.toFloat()
                                                receiveStatusMessage = "Receiving: ${(received / 1024)} KB / ${(total / 1024)} KB (${(receiveProgressPct * 100).toInt()}%)"
                                            } else {
                                                receiveStatusMessage = "Receiving: ${(received / 1024)} KB..."
                                            }
                                        }
                                    )

                                    isReceiving = false
                                    if (result.isSuccess && destinationFile.exists()) {
                                        receiveStatusMessage = "Received successfully! Opening restore..."
                                        Toast.makeText(context, "Backup downloaded! Starting restore...", Toast.LENGTH_SHORT).show()
                                        onBackupReceivedForRestore(destinationFile)
                                    } else {
                                        receiveStatusMessage = "Failed to connect or download: ${result.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            enabled = targetAddressInput.isNotBlank() && !isReceiving,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isReceiving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Receiving Archive...")
                            } else {
                                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect & Receive Backup")
                            }
                        }

                        if (receiveProgressPct > 0f) {
                            LinearProgressIndicator(
                                progress = { receiveProgressPct },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }

                        if (receiveStatusMessage.isNotBlank()) {
                            Text(
                                text = receiveStatusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
