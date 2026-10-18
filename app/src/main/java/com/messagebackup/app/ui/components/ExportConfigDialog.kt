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
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Modal dialog presented when the user taps Export, allowing customized configuration
 * of export format (PDF, HTML, CSV, Markdown, TXT), themes, calendar date ranges,
 * message pagination limits, and destinations.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    var selectedMaxPerFile by remember { mutableStateOf(initialMaxPerFile) }
    var includeMedia by remember { mutableStateOf(true) }
    var destination by remember { mutableStateOf(initialDestination) }
    var useZipPassword by remember { mutableStateOf(false) }
    var zipPassword by remember { mutableStateOf("") }
    var legalEvidenceMode by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())

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
                // 1. Document Format
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
                        Column {
                            Text(text = format.displayName, style = MaterialTheme.typography.bodyMedium)
                            if (format == ExportFormat.JSON || format == ExportFormat.XML) {
                                Text(
                                    text = "⚠️ Required format to restore conversations back to an Android phone",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 2. Legal / Evidentiary Mode for PDF
                if (selectedFormat == ExportFormat.PDF) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { legalEvidenceMode = !legalEvidenceMode }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = legalEvidenceMode,
                            onCheckedChange = { legalEvidenceMode = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "⚖️ Certified Legal / Court Evidentiary Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Text(
                                text = "Includes UTC & local timestamps, E.164 phone headers, Page X of Y numbering, and SHA-256 tamper-evident verification checksum.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // 2. HTML Theme
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

                // 3. Message Pagination
                Text(
                    text = "Pagination: Max Messages Per File",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                val splitOptions = listOf(0 to "No Split (Single File - Default)", 50 to "50 messages", 100 to "100 messages", 250 to "250 messages")
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

                // 4. Date Range Filter & Custom Picker
                Text(
                    text = "Date Range Filter",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                DateRangeFilter.values().forEach { range ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedDateRange = range
                                if (range == DateRangeFilter.CUSTOM) showDatePicker = true
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDateRange == range),
                            onClick = {
                                selectedDateRange = range
                                if (range == DateRangeFilter.CUSTOM) showDatePicker = true
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = range.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (selectedDateRange == DateRangeFilter.CUSTOM && customStartDate != null && customEndDate != null) {
                    val startStr = dateFormatter.format(Instant.ofEpochMilli(customStartDate!!))
                    val endStr = dateFormatter.format(Instant.ofEpochMilli(customEndDate!!))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Range: $startStr - $endStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { showDatePicker = true }) {
                            Text(text = "Change", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 5. Media Attachments
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 6. Optional AES-256 Archive Password Encryption
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { useZipPassword = !useZipPassword }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useZipPassword,
                        onCheckedChange = { useZipPassword = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Password-Protect Archive (AES-256)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Encrypt the .zip file with a custom password",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (useZipPassword) {
                    androidx.compose.material3.OutlinedTextField(
                        value = zipPassword,
                        onValueChange = { zipPassword = it },
                        label = { Text("Archive Password") },
                        placeholder = { Text("Enter strong password") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
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
                enabled = !useZipPassword || zipPassword.isNotBlank(),
                onClick = {
                    onConfirmExport(
                        ExportOptions(
                            format = selectedFormat,
                            includeMedia = includeMedia,
                            maxMessagesPerFile = selectedMaxPerFile,
                            theme = selectedTheme,
                            dateRange = selectedDateRange,
                            customStartDate = customStartDate,
                            customEndDate = customEndDate,
                            outputType = destination,
                            zipPassword = if (useZipPassword) zipPassword.trim() else null,
                            legalEvidenceMode = legalEvidenceMode
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

    // Material 3 Date Range Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    customStartDate = datePickerState.selectedStartDateMillis
                    customEndDate = datePickerState.selectedEndDateMillis
                    showDatePicker = false
                }) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = datePickerState,
                title = { Text("Select Date Range", modifier = Modifier.padding(16.dp)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
