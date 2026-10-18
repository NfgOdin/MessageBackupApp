package com.messagebackup.app.ui.viewer

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Built-in in-app document viewer capable of unzipping archives in memory
 * and displaying HTML web pages, Markdown, CSV, and plain text files with live progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    zipFile: File,
    onNavigateBack: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.activity.compose.BackHandler(onBack = onNavigateBack)

    var isUnzipping by remember { mutableStateOf(true) }
    var unzipProgress by remember { mutableFloatStateOf(0f) }
    var primaryDocumentFile by remember { mutableStateOf<File?>(null) }
    var documentContent by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(zipFile) {
        withContext(Dispatchers.IO) {
            val unzippedDir = File(zipFile.parentFile, "unzipped_${zipFile.nameWithoutExtension}")
            if (!unzippedDir.exists()) {
                unzippedDir.mkdirs()
            }
            val canonicalDirPrefix = unzippedDir.canonicalPath + File.separator

            // Estimate total entries for progress calculation
            var totalEntries = 0
            ZipInputStream(FileInputStream(zipFile)).use { zin ->
                while (zin.nextEntry != null) totalEntries++
            }
            if (totalEntries == 0) totalEntries = 1

            var processed = 0
            var foundMainDoc: File? = null

            ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val outFile = File(unzippedDir, entry.name)
                    // Zip Slip Path Traversal Security Check (Google Play App Security Requirement)
                    if (!outFile.canonicalPath.startsWith(canonicalDirPrefix)) {
                        throw SecurityException("Zip Entry is outside of target directory: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            zipIn.copyTo(out)
                        }
                        val lower = entry.name.lowercase()
                        if (foundMainDoc == null && (lower.endsWith(".html") || lower.endsWith(".pdf") || lower.endsWith(".csv") || lower.endsWith(".md") || lower.endsWith(".txt"))) {
                            foundMainDoc = outFile
                        }
                    }
                    processed++
                    unzipProgress = (processed.toFloat() / totalEntries).coerceIn(0f, 1f)
                    entry = zipIn.nextEntry
                }
            }

            primaryDocumentFile = foundMainDoc
            if (foundMainDoc != null && !foundMainDoc!!.name.endsWith(".pdf")) {
                documentContent = foundMainDoc!!.readText(Charsets.UTF_8)
            }
            isUnzipping = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = primaryDocumentFile?.name ?: "Document Viewer",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = zipFile.name,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isUnzipping) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Text(text = "Preparing Document...", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { unzipProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(unzipProgress * 100).toInt()}% unzipped",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (primaryDocumentFile != null) {
                val file = primaryDocumentFile!!
                val lowerName = file.name.lowercase()

                when {
                    lowerName.endsWith(".html") -> {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = false
                                    settings.allowFileAccess = true
                                    webViewClient = WebViewClient()
                                    loadUrl("file://${file.absolutePath}")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    lowerName.endsWith(".pdf") -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                        ) {
                            Text(text = "📄 PDF Generated", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "File: ${file.name} (${String.format("%.2f KB", file.length() / 1024.0)})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.Button(onClick = onShare) {
                                Text(text = "Share / Open in PDF Viewer")
                            }
                        }
                    }
                    else -> {
                        // Markdown, CSV, or Text
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(
                                text = documentContent ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
