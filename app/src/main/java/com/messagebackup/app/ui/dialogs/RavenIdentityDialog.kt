package com.messagebackup.app.ui.dialogs

import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.messagebackup.app.data.local.RavenDatabaseHelper
import com.messagebackup.app.domain.mesh.RavenCryptoEngine
import com.messagebackup.app.domain.mesh.RavenPeer
import com.messagebackup.app.domain.mesh.RavenQrManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modal dialog for displaying your personal Raven Mesh Call-Sign QR code,
 * scanning peers with zero-permission Google Code Scanner, and managing trusted peers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RavenIdentityDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val cryptoEngine = remember { RavenCryptoEngine(context) }
    val qrManager = remember { RavenQrManager(context) }
    val dbHelper = remember { RavenDatabaseHelper(context) }
    val socketTransport = remember { com.messagebackup.app.domain.mesh.RavenSocketTransport(context, scope) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var myCallSign by remember { mutableStateOf(cryptoEngine.getRavenCallSign()) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var peersList by remember { mutableStateOf<List<RavenPeer>>(emptyList()) }
    var isLoadingQr by remember { mutableStateOf(true) }
    var peerForDirectPing by remember { mutableStateOf<RavenPeer?>(null) }
    var pingMessageText by remember { mutableStateOf("⚡ Raven Whisper: Greetings from Muninn!") }
    var isSendingPing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        socketTransport.startServer()
    }

    fun refreshPeers() {
        scope.launch(Dispatchers.IO) {
            val list = dbHelper.getAllPeers()
            withContext(Dispatchers.Main) {
                peersList = list
            }
        }
    }

    LaunchedEffect(Unit) {
        socketTransport.incomingMessages.collect { incoming ->
            Toast.makeText(
                context,
                "🪶 New Raven from ${incoming.senderDisplayName}:\n${incoming.messageText}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        refreshPeers()
        val bmp = qrManager.generateHandshakeQrBitmap(
            displayName = "Me",
            localIp = com.messagebackup.app.domain.migration.DirectLocalMigrationManager.getLocalIpAddress()
        )
        qrBitmap = bmp
        isLoadingQr = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Raven Mesh Identity",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Decentralized E2EE Peer Network",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation Tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("My Sigil")
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Trusted Peers (${peersList.size})")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Content View
                if (selectedTab == 0) {
                    // My QR Sigil
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = myCallSign,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { showEditNameDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Call-Sign Name",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "Hardware-backed EC P-256 Key",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier.size(260.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingQr) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                } else if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap!!.asImageBitmap(),
                                        contentDescription = "Raven Mesh QR Sigil",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Have a peer scan this QR code to establish a verified, end-to-end encrypted direct mesh channel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }

                    // Action: Launch Google Code Scanner
                    Button(
                        onClick = {
                            if (activity != null) {
                                qrManager.startPeerScanner(
                                    activity = activity,
                                    onPeerScanned = { handshake ->
                                        scope.launch(Dispatchers.IO) {
                                            val newPeer = RavenPeer(
                                                peerId = handshake.callsign,
                                                displayName = handshake.displayName,
                                                publicKey = handshake.publicKeyBase64,
                                                lastKnownIp = handshake.localIp,
                                                lastSeenTimestamp = System.currentTimeMillis(),
                                                isTrusted = true
                                            )
                                            dbHelper.upsertPeer(newPeer)
                                            withContext(Dispatchers.Main) {
                                                refreshPeers()
                                                selectedTab = 1
                                                Toast.makeText(
                                                    context,
                                                    "✅ Linked with ${handshake.callsign}!",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Peer QR Sigil")
                    }
                } else {
                    // Peers List
                    if (peersList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = "No Linked Peers Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Scan a peer's Raven Sigil QR code to add them to your encrypted mesh.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(peersList) { peer ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(42.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = peer.peerId.takeLast(2),
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = peer.displayName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = peer.peerId,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Fingerprint: ${peer.fingerprint()}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    peerForDirectPing = peer
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Send,
                                                    contentDescription = "Send Direct Message",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    scope.launch(Dispatchers.IO) {
                                                        dbHelper.deletePeer(peer.peerId)
                                                        refreshPeers()
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Remove Peer",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (activity != null) {
                                qrManager.startPeerScanner(
                                    activity = activity,
                                    onPeerScanned = { handshake ->
                                        scope.launch(Dispatchers.IO) {
                                            val newPeer = RavenPeer(
                                                peerId = handshake.callsign,
                                                displayName = handshake.displayName,
                                                publicKey = handshake.publicKeyBase64,
                                                lastKnownIp = handshake.localIp,
                                                lastSeenTimestamp = System.currentTimeMillis(),
                                                isTrusted = true
                                            )
                                            dbHelper.upsertPeer(newPeer)
                                            withContext(Dispatchers.Main) {
                                                refreshPeers()
                                                Toast.makeText(
                                                    context,
                                                    "✅ Linked with ${handshake.callsign}!",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan New Peer")
                    }
                }
            }
        }
    }

    // Direct Encrypted Ping Dialog
    peerForDirectPing?.let { targetPeer ->
        var targetIpInput by remember { mutableStateOf(targetPeer.lastKnownIp ?: "") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { peerForDirectPing = null },
            title = {
                Text("Transmit Raven Whisper")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Sending authenticated AES-256-GCM encrypted packet to ${targetPeer.displayName} (${targetPeer.peerId})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    androidx.compose.material3.OutlinedTextField(
                        value = targetIpInput,
                        onValueChange = { targetIpInput = it },
                        label = { Text("Peer Local IP (Wi-Fi)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    androidx.compose.material3.OutlinedTextField(
                        value = pingMessageText,
                        onValueChange = { pingMessageText = it },
                        label = { Text("Encrypted Message") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSendingPing = true
                        scope.launch(Dispatchers.IO) {
                            val res = socketTransport.sendMessageToPeer(
                                peer = targetPeer,
                                text = pingMessageText,
                                targetIp = targetIpInput.trim()
                            )
                            withContext(Dispatchers.Main) {
                                isSendingPing = false
                                when (res) {
                                    is com.messagebackup.app.domain.mesh.RavenSendResult.Success -> {
                                        Toast.makeText(context, "🪶 Raven packet delivered!", Toast.LENGTH_SHORT).show()
                                        peerForDirectPing = null
                                    }
                                    is com.messagebackup.app.domain.mesh.RavenSendResult.Error -> {
                                        Toast.makeText(context, "⚠️ " + res.errorMessage, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isSendingPing && targetIpInput.isNotBlank() && pingMessageText.isNotBlank()
                ) {
                    if (isSendingPing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Send E2EE Packet")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { peerForDirectPing = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Call-Sign / Custom Name Dialog
    if (showEditNameDialog) {
        val currentPrefix = myCallSign.removePrefix("raven://").substringBeforeLast("-")
        var customNameInput by remember { mutableStateOf(currentPrefix) }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Customize Raven Call-Sign") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose your moniker or Norse call-sign. Your hardware-backed cryptographic suffix will be automatically appended to preserve global peer uniqueness.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    androidx.compose.material3.OutlinedTextField(
                        value = customNameInput,
                        onValueChange = { customNameInput = it.take(20) },
                        label = { Text("Call-Sign / Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customNameInput.trim()
                        if (trimmed.isNotBlank()) {
                            cryptoEngine.setRavenCallSignAlias(trimmed)
                            myCallSign = cryptoEngine.getRavenCallSign()
                            showEditNameDialog = false
                            // Regenerate QR with updated name
                            scope.launch(Dispatchers.IO) {
                                val bmp = qrManager.generateHandshakeQrBitmap(
                                    displayName = trimmed,
                                    localIp = com.messagebackup.app.domain.migration.DirectLocalMigrationManager.getLocalIpAddress()
                                )
                                withContext(Dispatchers.Main) {
                                    qrBitmap = bmp
                                }
                            }
                        }
                    },
                    enabled = customNameInput.isNotBlank()
                ) {
                    Text("Save Identity")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
