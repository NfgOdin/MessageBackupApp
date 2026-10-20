package com.messagebackup.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
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
    var customSplitInput by remember(initialMaxPerFile) {
        mutableStateOf(if (initialMaxPerFile > 0) initialMaxPerFile.toString() else "")
    }
    var includeMedia by remember { mutableStateOf(true) }
    var destination by remember { mutableStateOf(initialDestination) }
    var useZipPassword by remember { mutableStateOf(false) }
    var zipPassword by remember { mutableStateOf("") }
    var legalEvidenceMode by remember { mutableStateOf(false) }
    var enableWatermark by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL RECORD") }

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
                // 1. Document Format (Categorized into Visual Archive, Data & Text, and Backup)
                Text(
                    text = "Document Format",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Visual / Reading Formats
                Text(
                    text = "VISUAL ARCHIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                listOf(ExportFormat.HTML, ExportFormat.DESKTOP_VIEWER, ExportFormat.PDF).forEach { format ->
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
                            if (format == ExportFormat.DESKTOP_VIEWER) {
                                Text(
                                    text = "💻 Includes offline index.html viewer for PC/Mac with search & media",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "DATA & SPREADSHEET",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                listOf(ExportFormat.CSV, ExportFormat.MARKDOWN, ExportFormat.PLAIN_TEXT).forEach { format ->
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

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "PHONE RESTORATION",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                listOf(ExportFormat.JSON, ExportFormat.XML).forEach { format ->
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
                            Text(
                                text = "⚠️ Standard schema to restore messages back to phone",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "No Split" option
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedMaxPerFile = 0 }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedMaxPerFile == 0),
                            onClick = { selectedMaxPerFile = 0 }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "No Split", style = MaterialTheme.typography.bodyMedium)
                    }

                    // "Split" option
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val targetLimit = customSplitInput.toIntOrNull()?.takeIf { it > 0 } ?: 50
                                selectedMaxPerFile = targetLimit
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedMaxPerFile > 0),
                            onClick = {
                                val targetLimit = customSplitInput.toIntOrNull()?.takeIf { it > 0 } ?: 50
                                selectedMaxPerFile = targetLimit
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Split", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Input box appears under Split option when selected
                if (selectedMaxPerFile > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 2.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.OutlinedTextField(
                            value = customSplitInput,
                            onValueChange = { newValue ->
                                val filtered = newValue.filter { it.isDigit() }.take(5)
                                customSplitInput = filtered
                                val parsed = filtered.toIntOrNull()
                                selectedMaxPerFile = if (parsed != null && parsed > 0) parsed else 50
                            },
                            placeholder = { Text("50") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier.width(90.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "messages per file",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 4. Date Range Filter (Streamlined All Time vs Filter by Date)
                Text(
                    text = "Date Range Filter",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "All Time" option
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedDateRange = DateRangeFilter.ALL_TIME }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDateRange == DateRangeFilter.ALL_TIME),
                            onClick = { selectedDateRange = DateRangeFilter.ALL_TIME }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "All Time", style = MaterialTheme.typography.bodyMedium)
                    }

                    // "Filter by Date" option
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (selectedDateRange == DateRangeFilter.ALL_TIME) {
                                    selectedDateRange = DateRangeFilter.LAST_30_DAYS
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDateRange != DateRangeFilter.ALL_TIME),
                            onClick = {
                                if (selectedDateRange == DateRangeFilter.ALL_TIME) {
                                    selectedDateRange = DateRangeFilter.LAST_30_DAYS
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Filter by Date", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // If Filter by Date is selected, show quick chips for 30D, 90D, 1Y, and Custom
                if (selectedDateRange != DateRangeFilter.ALL_TIME) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 2.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val chips = listOf(
                            DateRangeFilter.LAST_30_DAYS to "30 Days",
                            DateRangeFilter.LAST_90_DAYS to "90 Days",
                            DateRangeFilter.LAST_YEAR to "1 Year",
                            DateRangeFilter.CUSTOM to "Custom..."
                        )
                        chips.forEach { (range, label) ->
                            val isSelected = (selectedDateRange == range)
                            androidx.compose.material3.Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    selectedDateRange = range
                                    if (range == DateRangeFilter.CUSTOM) showDatePicker = true
                                }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                if (selectedDateRange == DateRangeFilter.CUSTOM && customStartDate != null && customEndDate != null) {
                    val startStr = dateFormatter.format(Instant.ofEpochMilli(customStartDate!!))
                    val endStr = dateFormatter.format(Instant.ofEpochMilli(customEndDate!!))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 2.dp),
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

                // 5. Output Destination
                Text(
                    text = "Save Location / Destination",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                val destinationChoices = listOf(
                    OutputDestination.SHARE_SHEET to "Android Share / Save Sheet",
                    OutputDestination.GOOGLE_DRIVE to "Google Drive",
                    OutputDestination.ONEDRIVE to "Microsoft OneDrive",
                    OutputDestination.DROPBOX to "Dropbox",
                    OutputDestination.WEBDAV_NAS to "WebDAV / Local NAS / Nextcloud"
                )
                destinationChoices.forEach { (destOption, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { destination = destOption }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (destination == destOption),
                            onClick = { destination = destOption }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
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

                // 6. Court-Ready Legal & Forensic Evidentiary Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            legalEvidenceMode = !legalEvidenceMode
                            if (legalEvidenceMode) {
                                selectedFormat = ExportFormat.PDF
                            }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = legalEvidenceMode,
                        onCheckedChange = {
                            legalEvidenceMode = it
                            if (it) {
                                selectedFormat = ExportFormat.PDF
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚖️ Court-Ready Legal & Forensic Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isAdsRemoved) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "FREE with Ad",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Dual UTC+Local timestamps, certified Bates numbering, participant headers, and SHA-256 tamper-evident digital fingerprint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Progressive Disclosure: Watermark Sub-option inside Legal Mode
                if (legalEvidenceMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { enableWatermark = !enableWatermark },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = enableWatermark,
                                onCheckedChange = { enableWatermark = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Apply Custom Document Watermark",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (enableWatermark) {
                            Spacer(modifier = Modifier.height(4.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = watermarkText,
                                onValueChange = { watermarkText = it },
                                label = { Text("Watermark Text") },
                                placeholder = { Text("e.g. EXHIBIT A, CONFIDENTIAL, CASE #") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Printed as a subtle, certified diagonal overlay across each evidentiary page.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 7. Optional AES-256 Archive Password Encryption
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
                            legalEvidenceMode = legalEvidenceMode,
                            customWatermarkText = if (legalEvidenceMode && enableWatermark) watermarkText.trim() else null
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
