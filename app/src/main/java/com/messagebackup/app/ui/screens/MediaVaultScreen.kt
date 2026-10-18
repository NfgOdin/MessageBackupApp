package com.messagebackup.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.messagebackup.app.MessageBackupApp
import com.messagebackup.app.ads.AdManager
import com.messagebackup.app.model.MediaCategory
import com.messagebackup.app.model.VaultMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Screen displaying the Media Vault & MMS Attachment Gallery.
 * Allows filtering photos, videos, and audio clips across all conversations,
 * full preview, single-item sharing/saving, and bulk ZIP export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaVaultScreen(
    mediaItems: List<VaultMediaItem>,
    isLoading: Boolean,
    progressPct: Int = 0,
    progressMessage: String = "",
    adManager: AdManager,
    isAdsRemoved: Boolean,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf(MediaCategory.ALL) }
    var selectedContactFilter by remember { mutableStateOf<String?>(null) }
    var previewItem by remember { mutableStateOf<VaultMediaItem?>(null) }
    var isBulkExporting by remember { mutableStateOf(false) }
    var bulkExportPct by remember { mutableStateOf(0) }
    var bulkExportMessage by remember { mutableStateOf("") }

    BackHandler(onBack = onNavigateBack)

    val distinctContacts = remember(mediaItems) {
        mediaItems.map { it.contactName }.distinct().sorted()
    }

    val filteredItems = remember(mediaItems, selectedCategory, selectedContactFilter) {
        mediaItems.filter { item ->
            val matchesCategory = when (selectedCategory) {
                MediaCategory.ALL -> true
                else -> item.category == selectedCategory
            }
            val matchesContact = selectedContactFilter == null || item.contactName == selectedContactFilter
            matchesCategory && matchesContact
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Media Vault & Gallery",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${filteredItems.size} items • ${mediaItems.size} total attachments",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    if (filteredItems.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                adManager.showRewardedAd(activity, isAdsRemoved) {
                                    scope.launch {
                                        isBulkExporting = true
                                        try {
                                            bulkExportPct = 0
                                            bulkExportMessage = "Preparing media package..."
                                            val zipFile = createBulkMediaZip(context, filteredItems) { processed, total ->
                                                bulkExportPct = if (total > 0) (processed * 100) / total else 0
                                                bulkExportMessage = "Packaging media: $processed of $total files ($bulkExportPct%)..."
                                            }
                                            isBulkExporting = false
                                            if (zipFile != null && zipFile.exists()) {
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    zipFile
                                                )
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/zip"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    putExtra(Intent.EXTRA_SUBJECT, "MMS Media Vault Export (${filteredItems.size} items)")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Media Vault ZIP"))
                                            } else {
                                                Toast.makeText(context, "Failed to create media zip archive", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            isBulkExporting = false
                                            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderZip,
                                contentDescription = "Export Filtered as ZIP",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MediaCategory.values()) { cat ->
                    val count = if (cat == MediaCategory.ALL) {
                        mediaItems.size
                    } else {
                        mediaItems.count { it.category == cat }
                    }
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text("${cat.label} ($count)") },
                        leadingIcon = {
                            when (cat) {
                                MediaCategory.ALL -> Icon(Icons.Default.PermMedia, contentDescription = null, modifier = Modifier.size(16.dp))
                                MediaCategory.IMAGE -> Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                MediaCategory.VIDEO -> Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                                MediaCategory.AUDIO -> Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(16.dp))
                                MediaCategory.OTHER -> Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
            }

            // Contact Filter Chips if more than 1 contact
            if (distinctContacts.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedContactFilter == null,
                            onClick = { selectedContactFilter = null },
                            label = { Text("All Contacts") }
                        )
                    }
                    items(distinctContacts) { contact ->
                        FilterChip(
                            selected = selectedContactFilter == contact,
                            onClick = { selectedContactFilter = if (selectedContactFilter == contact) null else contact },
                            label = { Text(contact) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isLoading || isBulkExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        if (isBulkExporting) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { bulkExportPct.toFloat() / 100f },
                                    modifier = Modifier.size(72.dp),
                                    strokeWidth = 6.dp,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(
                                    text = "$bulkExportPct%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (bulkExportMessage.isNotBlank()) bulkExportMessage else "Packaging media files into ZIP...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                LinearProgressIndicator(
                                    progress = { bulkExportPct.toFloat() / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth(0.75f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { progressPct.toFloat() / 100f },
                                    modifier = Modifier.size(72.dp),
                                    strokeWidth = 6.dp,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(
                                    text = "$progressPct%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (progressMessage.isNotBlank()) progressMessage else "Scanning MMS attachments...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                LinearProgressIndicator(
                                    progress = { progressPct.toFloat() / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth(0.75f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PermMedia,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No attachments found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "No MMS media matches the selected filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(filteredItems, key = { it.partId }) { item ->
                        MediaThumbnailCard(
                            item = item,
                            onClick = { previewItem = item }
                        )
                    }
                }
            }
        }
    }

    // Full Preview & Action Dialog
    previewItem?.let { item ->
        MediaPreviewDialog(
            item = item,
            onDismiss = { previewItem = null },
            onShare = {
                shareMediaFile(context, item)
            }
        )
    }
}

@Composable
private fun MediaThumbnailCard(
    item: VaultMediaItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(item.uriString) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(item.uriString) {
        if (item.category == MediaCategory.IMAGE) {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(item.contentUri)?.use { stream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 4 // downsample thumbnail for fluid scrolling performance
                        }
                        bitmap = BitmapFactory.decodeStream(stream, null, options)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = item.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (item.category) {
                            MediaCategory.IMAGE -> Icons.Default.Image
                            MediaCategory.VIDEO -> Icons.Default.Movie
                            MediaCategory.AUDIO -> Icons.Default.Audiotrack
                            else -> Icons.Default.PermMedia
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Bottom metadata gradient strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Column {
                    Text(
                        text = item.contactName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaPreviewDialog(
    item: VaultMediaItem,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    var fullBitmap by remember(item.uriString) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val dateStr = remember(item.timestamp) {
        SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault()).format(Date(item.timestamp))
    }

    LaunchedEffect(item.uriString) {
        if (item.category == MediaCategory.IMAGE) {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(item.contentUri)?.use { stream ->
                        fullBitmap = BitmapFactory.decodeStream(stream)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = item.contactName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (fullBitmap != null) {
                    Image(
                        bitmap = fullBitmap!!.asImageBitmap(),
                        contentDescription = item.fileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = when (item.category) {
                                    MediaCategory.VIDEO -> Icons.Default.Movie
                                    MediaCategory.AUDIO -> Icons.Default.Audiotrack
                                    else -> Icons.Default.PermMedia
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = item.contentType,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Filename: ${item.fileName}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "MIME Type: ${item.contentType}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (item.contactAddress.isNotBlank()) {
                            Text(
                                text = "Number: ${item.contactAddress}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onShare
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share / Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun shareMediaFile(context: android.content.Context, item: VaultMediaItem) {
    try {
        val cacheFile = File(context.cacheDir, item.fileName)
        context.contentResolver.openInputStream(item.contentUri)?.use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = item.contentType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${item.fileName}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing media: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun createBulkMediaZip(
    context: android.content.Context,
    items: List<VaultMediaItem>,
    onProgress: ((processed: Int, total: Int) -> Unit)? = null
): File? = withContext(Dispatchers.IO) {
    try {
        val exportsDir = File(context.cacheDir, "vault_exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val zipFile = File(exportsDir, "Media_Vault_$timeStamp.zip")

        val total = items.size
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val usedNames = mutableSetOf<String>()
            for ((index, item) in items.withIndex()) {
                onProgress?.invoke(index + 1, total)
                try {
                    var entryName = "${item.contactName.replace(Regex("[^a-zA-Z0-9_]"), "_")}/${item.fileName}"
                    if (usedNames.contains(entryName)) {
                        entryName = "${item.partId}_${item.fileName}"
                    }
                    usedNames.add(entryName)

                    zos.putNextEntry(ZipEntry(entryName))
                    context.contentResolver.openInputStream(item.contentUri)?.use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                } catch (_: Exception) {}
            }
        }
        zipFile
    } catch (e: Exception) {
        null
    }
}
