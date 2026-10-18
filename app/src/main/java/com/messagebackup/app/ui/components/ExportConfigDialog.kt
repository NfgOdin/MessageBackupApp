package com.messagebackup.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.messagebackup.app.model.DateRangeFilter
import com.messagebackup.app.model.ExportFormat
import com.messagebackup.app.model.ExportOptions
import com.messagebackup.app.model.ExportTheme
import com.messagebackup.app.model.OutputDestination

/**
 * Modal dialog presented when the user taps Export, allowing customized configuration
 * of export format, theme, date range, message pagination per file, and destination.
 */
@Composable
fun ExportConfigDialog(
    selectedCount: Int,
    isAdsRemoved: Boolean,
    initialFormat: ExportFormat = ExportFormat.HTML,
    initialMaxPerFile: Int = 0,
    initialTheme: ExportTheme = ExportTheme.LIGHT,
    initialDestination: OutputDestination = OutputDestination.SHARE_SHEET,
    onConfirmExport: (ExportOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFormat by remember { mutableStateOf(initialFormat) }
    var selectedTheme by remember { mutableStateOf(initialTheme) }
    var selectedDateRange by remember { mutableStateOf(DateRangeFilter.ALL_TIME) }
    var selectedMaxPerFile by remember { mutableStateOf(initialMaxPerFile) }
    var includeMedia by remember { mutableStateOf(true) }
    var destination by remember { mutableStateOf(initialDestination) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Export $selectedCount Conversation${if (selectedCount > 1) "s" else ""}")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // Section: Document Format
                Text(
                    text = "Document Format",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                ExportFormat.values().forEach { format ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFormat = format }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedFormat == format),
                            onClick = { selectedFormat = format }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = format.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Section: Theme (for HTML)
                if (selectedFormat == ExportFormat.HTML) {
                    Text(
                        text = "HTML Color Theme",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ExportTheme.values().forEach { theme ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTheme = theme }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (selectedTheme == theme),
                                    onClick = { selectedTheme = theme }
                                )
                                Text(text = theme.displayName.split(" ").first(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Section: Message Pagination
                Text(
                    text = "Pagination: Max Messages Per File",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Splits large conversations into sequential parts (Part 1 = oldest).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val splitOptions = listOf(0 to "No Split (Single File)", 50 to "50 messages", 100 to "100 messages", 250 to "250 messages")
                splitOptions.forEach { (limit, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMaxPerFile = limit }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedMaxPerFile == limit),
                            onClick = { selectedMaxPerFile = limit }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Section: Date Range Filter
                Text(
                    text = "Date Range Filter",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                DateRangeFilter.values().forEach { range ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDateRange = range }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDateRange == range),
                            onClick = { selectedDateRange = range }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = range.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Section: Media Attachments
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeMedia = !includeMedia }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeMedia,
                        onCheckedChange = { includeMedia = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Include MMS Media", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Bundle images & audio in the archive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isAdsRemoved) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ℹ️ A short video ad will play before export begins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmExport(
                        ExportOptions(
                            format = selectedFormat,
                            includeMedia = includeMedia,
                            maxMessagesPerFile = selectedMaxPerFile,
                            theme = selectedTheme,
                            dateRange = selectedDateRange,
                            outputType = destination
                        )
                    )
                }
            ) {
                Text(text = if (isAdsRemoved) "Export Now" else "Watch Ad & Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}
