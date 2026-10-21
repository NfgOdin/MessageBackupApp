package com.messagebackup.app.ui.dialogs

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.sp
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
    prefs: com.messagebackup.app.data.preferences.AppPreferences,
    adManager: com.messagebackup.app.ads.AdManager,
    isAdsRemoved: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val cryptoEngine = remember { RavenCryptoEngine(context) }
    val qrManager = remember { RavenQrManager(context) }
    val dbHelper = remember { RavenDatabaseHelper(context) }
    val socketTransport = remember { com.messagebackup.app.domain.mesh.RavenSocketTransport.getInstance(context, scope) }
    val pttEngine = remember { com.messagebackup.app.domain.mesh.RavenVoicePttEngine(context) }
    val bleEngine = remember { com.messagebackup.app.domain.mesh.RavenBleMeshEngine(context, scope) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var myCallSign by remember { mutableStateOf(cryptoEngine.getRavenCallSign()) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEnterCodeDialog by remember { mutableStateOf(false) }
    var inputFriendCodeText by remember { mutableStateOf("") }
    var peerToShareBackWith by remember { mutableStateOf<RavenPeer?>(null) }
    var peerToDelete by remember { mutableStateOf<RavenPeer?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var peersList by remember { mutableStateOf<List<RavenPeer>>(emptyList()) }
    var isLoadingQr by remember { mutableStateOf(true) }
    var peerForDirectPing by remember { mutableStateOf<RavenPeer?>(null) }
    var pingMessageText by remember { mutableStateOf("⚡ Raven Whisper: Greetings from Muninn!") }
    var isSendingPing by remember { mutableStateOf(false) }
    var isRavenFlying by remember { mutableStateOf(false) }

    // Multi-Transport State
    val transportMode by prefs.ravenTransportMode.collectAsState()
    var isBleBroadcasting by remember { mutableStateOf(false) }
    var isBleScanning by remember { mutableStateOf(false) }
    var discoveredBlePeers by remember { mutableStateOf<List<com.messagebackup.app.domain.mesh.BleRavenNode>>(emptyList()) }

    // PTT Voice State
    var isPttRecording by remember { mutableStateOf(false) }

    // Runtime Permission Request States
    var permissionDialogTitle by remember { mutableStateOf<String?>(null) }
    var permissionDialogMessage by remember { mutableStateOf<String?>(null) }
    var pendingBleAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingAudioAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) {
            val action = pendingBleAction
            pendingBleAction = null
            action?.invoke()
        } else {
            pendingBleAction = null
            permissionDialogTitle = "Bluetooth Permissions Required"
            permissionDialogMessage = "Raven Mesh off-grid radio beacons require Nearby Devices / Bluetooth permissions to discover and broadcast to peers. Please grant them in App Settings."
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val action = pendingAudioAction
            pendingAudioAction = null
            action?.invoke()
        } else {
            pendingAudioAction = null
            permissionDialogTitle = "Microphone Permission Required"
            permissionDialogMessage = "Mind-to-Mind PTT Walkie-Talkie requires Microphone permission to capture encrypted voice packets. Please grant it in App Settings."
        }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val action = pendingBleAction
            pendingBleAction = null
            action?.invoke()
        } else {
            pendingBleAction = null
            Toast.makeText(context, "Bluetooth was not turned on", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkAndRequestBlePermissions(action: () -> Unit) {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            pendingBleAction = action
            blePermissionLauncher.launch(missing.toTypedArray())
            return
        }

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val adapter = btManager?.adapter
        if (adapter == null) {
            Toast.makeText(context, "Bluetooth hardware is not available on this device", Toast.LENGTH_LONG).show()
            return
        }

        if (!adapter.isEnabled) {
            pendingBleAction = action
            try {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (e: Exception) {
                action()
            }
            return
        }

        action()
    }

    fun checkAndRequestAudioPermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingAudioAction = action
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        socketTransport.startServer()
    }

    fun refreshPeers() {
        socketTransport.pollRelayImmediately()
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
        socketTransport.incomingVoiceStreams.collect { voice ->
            Toast.makeText(
                context,
                "🎙️ Incoming PTT from ${voice.senderDisplayName} (${voice.durationMs}ms)!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        socketTransport.peerLinkedEvents.collect { linkedPeer ->
            refreshPeers()
            selectedTab = 1
            Toast.makeText(
                context,
                "🪶 Instant Link Established with ${linkedPeer.displayName}!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        bleEngine.discoveredBleNodes.collect { bleNode ->
            if (discoveredBlePeers.none { it.macAddress == bleNode.macAddress }) {
                discoveredBlePeers = discoveredBlePeers + bleNode
            }
        }
    }

    LaunchedEffect(myCallSign) {
        refreshPeers()
        val bmp = qrManager.generateHandshakeQrBitmap(
            displayName = myCallSign.removePrefix("raven://"),
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
                                Text("Peers (${peersList.size})")
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(transportMode.icon, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Radios")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (selectedTab == 0) {
                    // My QR Sigil
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
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

                        Spacer(modifier = Modifier.height(18.dp))

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

                                                // Instantly establish 2-way mutual link with the scanned phone over socket and cellular relay!
                                                var mutualConfirmed = false
                                                val res = socketTransport.sendMutualHandshake(
                                                    targetIp = handshake.localIp ?: "",
                                                    targetPeerId = handshake.callsign,
                                                    targetPublicKey = handshake.publicKeyBase64
                                                )
                                                if (res.isSuccess) {
                                                    mutualConfirmed = true
                                                }

                                                withContext(Dispatchers.Main) {
                                                    refreshPeers()
                                                    selectedTab = 1
                                                    if (mutualConfirmed) {
                                                        Toast.makeText(
                                                            context,
                                                            "🪶 Instant 2-Way Link Established with ${handshake.displayName}! Both phones are paired.",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            "Linked with ${handshake.callsign}!",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
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

                        Spacer(modifier = Modifier.height(10.dp))

                        // Remote Link Options (Text / Share Code or Input Code)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val myIp = com.messagebackup.app.domain.migration.DirectLocalMigrationManager.getLocalIpAddress()
                                    val textCode = cryptoEngine.buildTextableConnectionCode(
                                        displayName = myCallSign.removePrefix("raven://"),
                                        localIp = myIp
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Connect with me on MUNINN Raven Mesh")
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Let's link on MUNINN Raven Mesh for encrypted off-grid chat & walkie-talkie! Copy this connection code into your app:\n\n$textCode"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Raven Code via..."))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Text / Share Code", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    inputFriendCodeText = ""
                                    showEnterCodeDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enter Code", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else if (selectedTab == 1) {
                    // Peers List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
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
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
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
                                                        modifier = Modifier.size(44.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = peer.displayName.take(2).uppercase(),
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 16.sp,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = peer.displayName,
                                                                style = MaterialTheme.typography.titleMedium,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = when (peer.status) {
                                                                    com.messagebackup.app.domain.mesh.RavenPeerStatus.CONNECTED -> {
                                                                        if (!peer.lastKnownIp.isNullOrBlank()) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                                                    }
                                                                    com.messagebackup.app.domain.mesh.RavenPeerStatus.PENDING_ACCEPTANCE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                                                                    com.messagebackup.app.domain.mesh.RavenPeerStatus.PENDING_APPROVAL -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                                                                }
                                                            ) {
                                                                Text(
                                                                    text = when (peer.status) {
                                                                        com.messagebackup.app.domain.mesh.RavenPeerStatus.CONNECTED -> if (!peer.lastKnownIp.isNullOrBlank()) "🟢 CONNECTED" else "🔵 MESH"
                                                                        com.messagebackup.app.domain.mesh.RavenPeerStatus.PENDING_ACCEPTANCE -> "🟡 NEEDS ACCEPTANCE"
                                                                        com.messagebackup.app.domain.mesh.RavenPeerStatus.PENDING_APPROVAL -> "⏳ PENDING"
                                                                    },
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
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

                                                IconButton(
                                                    onClick = { peerToDelete = peer }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Remove Peer",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            if (peer.status == com.messagebackup.app.domain.mesh.RavenPeerStatus.PENDING_ACCEPTANCE) {
                                                // Peer requested connection: Local user can Accept or Deny
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            scope.launch(Dispatchers.IO) {
                                                                val updated = peer.copy(status = com.messagebackup.app.domain.mesh.RavenPeerStatus.CONNECTED)
                                                                dbHelper.upsertPeer(updated)
                                                                socketTransport.sendAcceptanceConfirmation(peer)
                                                                withContext(Dispatchers.Main) {
                                                                    refreshPeers()
                                                                    Toast.makeText(context, "✅ Accepted ${peer.displayName}! You are now connected.", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Accept")
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            scope.launch(Dispatchers.IO) {
                                                                dbHelper.deletePeer(peer.peerId)
                                                                withContext(Dispatchers.Main) {
                                                                    refreshPeers()
                                                                    Toast.makeText(context, "Denied connection request.", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Deny")
                                                    }
                                                }
                                            } else if (peer.status == com.messagebackup.app.domain.mesh.RavenPeerStatus.PENDING_APPROVAL) {
                                                // User entered code or sent request to friend, awaiting acceptance/reciprocal code
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = "⏳ Awaiting friend's acceptance. If you are not on the same Wi-Fi, text them your code so they can add you back!",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.padding(8.dp)
                                                        )
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            val myIp = com.messagebackup.app.domain.migration.DirectLocalMigrationManager.getLocalIpAddress()
                                                            val myCode = cryptoEngine.buildTextableConnectionCode(
                                                                displayName = myCallSign.removePrefix("raven://"),
                                                                localIp = myIp
                                                            )
                                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                type = "text/plain"
                                                                putExtra(Intent.EXTRA_SUBJECT, "MUNINN Raven Connection Reply")
                                                                putExtra(
                                                                    Intent.EXTRA_TEXT,
                                                                    "I added you on MUNINN! Copy & paste my code back into MUNINN to complete our connection:\n\n$myCode"
                                                                )
                                                            }
                                                            context.startActivity(Intent.createChooser(shareIntent, "Text Reply Code to ${peer.displayName}..."))
                                                        },
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Text Reply Code to ${peer.displayName}", fontSize = 12.sp)
                                                    }
                                                }
                                            } else {
                                                // Direct Instant Actions: Walkie-Talkie Talk & Message
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            checkAndRequestAudioPermission {
                                                                isPttRecording = true
                                                                scope.launch(Dispatchers.IO) {
                                                                    val started = pttEngine.startRecording()
                                                                    if (!started) {
                                                                        withContext(Dispatchers.Main) {
                                                                            isPttRecording = false
                                                                            Toast.makeText(context, "Microphone unavailable. Check permissions.", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                        return@launch
                                                                    }
                                                                    kotlinx.coroutines.delay(2000)
                                                                    val snippet = pttEngine.stopRecording()
                                                                    withContext(Dispatchers.Main) {
                                                                        isPttRecording = false
                                                                    }
                                                                    if (snippet != null) {
                                                                        val res = socketTransport.sendVoiceSnippetToPeer(
                                                                            peer = peer,
                                                                            voiceSnippet = snippet
                                                                        )
                                                                        withContext(Dispatchers.Main) {
                                                                            when (res) {
                                                                                is com.messagebackup.app.domain.mesh.RavenSendResult.Success -> {
                                                                                    isRavenFlying = true
                                                                                    Toast.makeText(context, "🎙️ Walkie-Talkie voice delivered to ${peer.displayName}!", Toast.LENGTH_SHORT).show()
                                                                                }
                                                                                is com.messagebackup.app.domain.mesh.RavenSendResult.Error -> {
                                                                                    Toast.makeText(context, "⚠️ " + res.errorMessage, Toast.LENGTH_LONG).show()
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.weight(1f),
                                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                            containerColor = if (isPttRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                        )
                                                    ) {
                                                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(if (isPttRecording) "Speaking..." else "Talk (PTT)")
                                                    }

                                                    OutlinedButton(
                                                        onClick = { peerForDirectPing = peer },
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Message")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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

                                                    var mutualConfirmed = false
                                                    val res = socketTransport.sendMutualHandshake(
                                                        targetIp = handshake.localIp ?: "",
                                                        targetPeerId = handshake.callsign,
                                                        targetPublicKey = handshake.publicKeyBase64
                                                    )
                                                    if (res.isSuccess) {
                                                        mutualConfirmed = true
                                                    }

                                                    withContext(Dispatchers.Main) {
                                                        refreshPeers()
                                                        if (mutualConfirmed) {
                                                            Toast.makeText(
                                                                context,
                                                                "🪶 Instant 2-Way Link Established with ${handshake.displayName}! Both phones are paired.",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        } else {
                                                            Toast.makeText(
                                                                context,
                                                                "Linked with ${handshake.callsign}!",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                    }
                                                }
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan QR", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    inputFriendCodeText = ""
                                    showEnterCodeDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enter Code", fontSize = 13.sp)
                            }
                        }
                    }
                } else if (selectedTab == 2) {
                    // Tab 2: Multi-Transport Radio Mesh Manager
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Raven Multi-Transport Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dynamically orchestrate data transmission across off-grid Bluetooth Low Energy (BLE), local Wi-Fi Hotspot sockets, and global cellular WebRTC data channels.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Transport Mode Cards
                        com.messagebackup.app.domain.mesh.RavenTransportMode.entries.forEach { mode ->
                            val isSelected = transportMode == mode
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { prefs.setRavenTransportMode(mode) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(mode.icon, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = mode.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // BLE Controls Section
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Off-Grid BLE Radio Beacon",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            checkAndRequestBlePermissions {
                                                if (isBleBroadcasting) {
                                                    bleEngine.stopBroadcasting()
                                                    isBleBroadcasting = false
                                                } else {
                                                    isBleBroadcasting = bleEngine.startBroadcasting(myCallSign)
                                                    if (!isBleBroadcasting) {
                                                        Toast.makeText(context, "Bluetooth disabled or advertising failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = if (isBleBroadcasting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text(if (isBleBroadcasting) "Stop Beacon" else "Broadcast Sigil")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            checkAndRequestBlePermissions {
                                                if (isBleScanning) {
                                                    bleEngine.stopScanning()
                                                    isBleScanning = false
                                                } else {
                                                    isBleScanning = bleEngine.startScanning()
                                                    if (!isBleScanning) {
                                                        Toast.makeText(context, "Bluetooth disabled or scanning failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (isBleScanning) "Stop Scan" else "Scan Radios")
                                    }
                                }

                                if (discoveredBlePeers.isNotEmpty()) {
                                    Text(
                                        text = "Discovered BLE Nodes: ${discoveredBlePeers.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    discoveredBlePeers.forEach { node ->
                                        Text(
                                            text = "${node.callSign} (RSSI: ${node.rssi} dBm)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Direct Encrypted Ping Dialog
    peerForDirectPing?.let { targetPeer ->
        val resolvedIp = targetPeer.lastKnownIp ?: ""
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { peerForDirectPing = null },
            title = {
                Text("Transmit Raven Whisper")
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Sending authenticated AES-256-GCM encrypted packet to ${targetPeer.displayName} (${targetPeer.peerId})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (resolvedIp.isNotBlank()) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (resolvedIp.isNotBlank()) "🟢 Linked: $resolvedIp:8744" else "🔵 Transport: Local Radio Mesh Relay",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    androidx.compose.material3.OutlinedTextField(
                        value = pingMessageText,
                        onValueChange = { pingMessageText = it },
                        label = { Text("Encrypted Message") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // "Mind-to-Mind" PTT Walkie-Talkie Voice Transmission Section
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isPttRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Mind-to-Mind PTT (Walkie-Talkie)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = if (isPttRecording) "🔴 Recording voice... Release to transmit!" else "Tap to talk, transmits encrypted voice packet directly to peer's speaker.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = if (isPttRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Walkie-Talkie PTT Transmit Button
                            Button(
                                onClick = {
                                    if (!isPttRecording) {
                                        checkAndRequestAudioPermission {
                                            isPttRecording = true
                                            scope.launch(Dispatchers.IO) {
                                                val started = pttEngine.startRecording()
                                                if (!started) {
                                                    withContext(Dispatchers.Main) {
                                                        isPttRecording = false
                                                        Toast.makeText(context, "Microphone unavailable. Check permissions.", Toast.LENGTH_SHORT).show()
                                                    }
                                                    return@launch
                                                }
                                                // Capture 2-second voice blast
                                                kotlinx.coroutines.delay(2000)
                                                val snippet = pttEngine.stopRecording()
                                                withContext(Dispatchers.Main) {
                                                    isPttRecording = false
                                                }
                                                if (snippet != null) {
                                                    val res = socketTransport.sendVoiceSnippetToPeer(
                                                        peer = targetPeer,
                                                        voiceSnippet = snippet,
                                                        targetIp = resolvedIp
                                                    )
                                                    withContext(Dispatchers.Main) {
                                                        when (res) {
                                                            is com.messagebackup.app.domain.mesh.RavenSendResult.Success -> {
                                                                isRavenFlying = true
                                                                Toast.makeText(context, "🎙️ Walkie-Talkie voice packet delivered!", Toast.LENGTH_SHORT).show()
                                                            }
                                                            is com.messagebackup.app.domain.mesh.RavenSendResult.Error -> {
                                                                Toast.makeText(context, "⚠️ " + res.errorMessage, Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = if (isPttRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                ),
                                enabled = !isSendingPing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "PTT Microphone",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(if (isPttRecording) "🔴 Recording 2s Voice..." else "🎙️ Transmit 2s Voice Packet")
                                }
                            }
                        }
                    }
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
                                targetIp = resolvedIp
                            )
                            withContext(Dispatchers.Main) {
                                isSendingPing = false
                                when (res) {
                                    is com.messagebackup.app.domain.mesh.RavenSendResult.Success -> {
                                        isRavenFlying = true
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
                    enabled = !isSendingPing && pingMessageText.isNotBlank()
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
        var showRenameConfirmDialog by remember { mutableStateOf(false) }
        val hasUsedFree = remember { cryptoEngine.hasUsedFreeRename() }
        val aiCredits = prefs.aiCredits.value

        val executeRename: () -> Unit = {
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
                        Toast.makeText(context, "🪶 Identity updated to $myCallSign", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Customize Raven Call-Sign") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Choose your moniker or Norse call-sign. Your hardware-backed cryptographic suffix will be automatically appended to preserve global peer uniqueness.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!hasUsedFree) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🎁 First identity customization is 100% FREE!",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else if (!isAdsRemoved) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ᚱ Subsequent renames require 1 Rune credit or watching a short video.",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

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
                if (!hasUsedFree || isAdsRemoved) {
                    Button(
                        onClick = executeRename,
                        enabled = customNameInput.isNotBlank()
                    ) {
                        Text(if (!hasUsedFree) "Claim Free Rename" else "Save Identity (PRO)")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Option 1: Use 1 credit
                        Button(
                            onClick = {
                                if (aiCredits > 0) {
                                    if (prefs.confirmRuneSpend.value) {
                                        showRenameConfirmDialog = true
                                    } else {
                                        prefs.consumeAiCredit()
                                        executeRename()
                                    }
                                } else {
                                    Toast.makeText(context, "No Runes left! Watch an ad to rename.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = customNameInput.isNotBlank() && aiCredits > 0
                        ) {
                            Text("Use 1 ᚱ ($aiCredits)")
                        }

                        // Option 2: Watch an ad
                        OutlinedButton(
                            onClick = {
                                if (activity != null) {
                                    adManager.showRewardedAd(
                                        activity = activity,
                                        isAdsRemoved = false,
                                        onUserEarnedReward = {
                                            executeRename()
                                        }
                                    )
                                }
                            },
                            enabled = customNameInput.isNotBlank()
                        ) {
                            Text("Watch Ad 🎬")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )

        if (showRenameConfirmDialog) {
            RuneSpendConfirmationDialog(
                runeName = "Othala (Identity Engraving)",
                runeDrawableId = com.messagebackup.app.R.drawable.ic_rune_othala,
                runeSymbol = "ᛟ",
                costAmount = 1,
                currentBalance = aiCredits,
                actionDescription = "Spend 1 Othala Rune to engrave and propagate your custom Call-Sign moniker into the Raven mesh.",
                prefs = prefs,
                onConfirm = {
                    showRenameConfirmDialog = false
                    prefs.consumeAiCredit()
                    executeRename()
                },
                onDismiss = {
                    showRenameConfirmDialog = false
                }
            )
        }
    }

    // Permission Explanation & Direct Settings Launcher Dialog
    if (permissionDialogTitle != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { permissionDialogTitle = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = permissionDialogTitle!!,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = permissionDialogMessage ?: "Please grant the necessary permission in Android Settings to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        permissionDialogTitle = null
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Please open Android Settings > Apps > MUNINN > Permissions", Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { permissionDialogTitle = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Enter Friend's Raven Connection Code
    if (showEnterCodeDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEnterCodeDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Add Raven Contact")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste or enter the connection code or raven:// link your friend texted or shared with you:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    androidx.compose.material3.OutlinedTextField(
                        value = inputFriendCodeText,
                        onValueChange = { inputFriendCodeText = it },
                        label = { Text("Raven Connection Code") },
                        placeholder = { Text("raven://... or pasted code") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    inputFriendCodeText = clip.trim()
                                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste from Clipboard")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val verified = cryptoEngine.parseAndVerifyHandshake(inputFriendCodeText.trim())
                        if (verified != null) {
                            scope.launch(Dispatchers.IO) {
                                val existing = dbHelper.getAllPeers().find { it.peerId == verified.callsign }
                                val newStatus = if (existing != null) {
                                    // If they were already in our contacts (e.g., they sent code, we paste back), mark CONNECTED!
                                    com.messagebackup.app.domain.mesh.RavenPeerStatus.CONNECTED
                                } else {
                                    // New peer added: if we can reach their socket, they'll become connected, else pending friend's reciprocal code
                                    com.messagebackup.app.domain.mesh.RavenPeerStatus.PENDING_APPROVAL
                                }

                                val newPeer = RavenPeer(
                                    peerId = verified.callsign,
                                    displayName = verified.displayName,
                                    publicKey = verified.publicKeyBase64,
                                    lastKnownIp = verified.localIp,
                                    lastSeenTimestamp = System.currentTimeMillis(),
                                    isTrusted = true,
                                    status = newStatus
                                )
                                dbHelper.upsertPeer(newPeer)

                                var mutualConfirmed = false
                                val res = socketTransport.sendMutualHandshake(
                                    targetIp = verified.localIp ?: "",
                                    targetPeerId = verified.callsign,
                                    targetPublicKey = verified.publicKeyBase64
                                )
                                if (res.isSuccess) {
                                    mutualConfirmed = true
                                }

                                withContext(Dispatchers.Main) {
                                    refreshPeers()
                                    showEnterCodeDialog = false
                                    selectedTab = 1
                                    if (mutualConfirmed) {
                                        Toast.makeText(
                                            context,
                                            "🪶 Verified & Linked with ${verified.displayName}! Both phones are paired.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        // Offer to text reciprocal code back to friend
                                        peerToShareBackWith = newPeer
                                        Toast.makeText(
                                            context,
                                            "Verified ${verified.displayName}! Text them your reply code to complete connection.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(context, "Invalid or unverified Raven Code. Please check the code and try again.", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = inputFriendCodeText.isNotBlank()
                ) {
                    Text("Verify & Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnterCodeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal prompt to text reciprocal code back after adding a remote friend
    peerToShareBackWith?.let { targetPeer ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { peerToShareBackWith = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Text Reply Code")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "You've linked with ${targetPeer.displayName}!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Since you aren't on the same local Wi-Fi, text your reply code back to ${targetPeer.displayName} so they can paste it and complete your connection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentPeer = targetPeer
                        peerToShareBackWith = null
                        val myIp = com.messagebackup.app.domain.migration.DirectLocalMigrationManager.getLocalIpAddress()
                        val myCode = cryptoEngine.buildTextableConnectionCode(
                            displayName = myCallSign.removePrefix("raven://"),
                            localIp = myIp
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "MUNINN Raven Connection Reply")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "I added you on MUNINN! Copy & paste my code back into MUNINN to complete our connection:\n\n$myCode"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Text Reply Code to ${currentPeer.displayName}..."))
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Text Code Back")
                }
            },
            dismissButton = {
                TextButton(onClick = { peerToShareBackWith = null }) {
                    Text("Later")
                }
            }
        )
    }

    // Confirmation Dialog before deleting peer
    peerToDelete?.let { targetPeer ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { peerToDelete = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text("Remove Peer Contact?")
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to remove ${targetPeer.displayName} (${targetPeer.peerId})? You will no longer be able to transmit encrypted whispers or walkie-talkie packets until you re-link.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idToRemove = targetPeer.peerId
                        peerToDelete = null
                        scope.launch(Dispatchers.IO) {
                            dbHelper.deletePeer(idToRemove)
                            withContext(Dispatchers.Main) {
                                refreshPeers()
                                Toast.makeText(context, "Peer removed.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { peerToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Overlay animation: Soaring raven flight upon transmission
    com.messagebackup.app.ui.components.HuginnFlightOverlay(
        isFlying = isRavenFlying,
        onFlightCompleted = { isRavenFlying = false }
    )
}
