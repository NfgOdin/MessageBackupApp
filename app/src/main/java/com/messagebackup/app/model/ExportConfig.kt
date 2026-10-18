package com.messagebackup.app.model

import java.io.File

/**
 * Supported document export formats.
 */
enum class ExportFormat(val extension: String, val displayName: String, val mimeType: String) {
    PDF("pdf", "PDF Document (.pdf)", "application/pdf"),
    HTML("html", "HTML Web Page (.html)", "text/html"),
    JSON("json", "JSON Backup (.json) [Required for Restore]", "application/json"),
    XML("xml", "XML Backup (.xml) [Required for Restore]", "application/xml"),
    CSV("csv", "Excel / CSV Spreadsheet (.csv)", "text/csv"),
    MARKDOWN("md", "Markdown (.md)", "text/markdown"),
    PLAIN_TEXT("txt", "Plain Text (.txt)", "text/plain")
}

/**
 * Visual color scheme options for exported HTML documents and the app.
 */
enum class ExportTheme(val displayName: String) {
    LIGHT("Light Mode"),
    DARK("Dark Mode"),
    SYSTEM("Auto (System Match)")
}

/**
 * Filter to constrain exported messages to specific date intervals.
 */
enum class DateRangeFilter(val displayName: String) {
    ALL_TIME("All Time"),
    LAST_30_DAYS("Past 30 Days"),
    LAST_90_DAYS("Past 90 Days"),
    LAST_YEAR("Past Year"),
    CUSTOM("Custom Date Range")
}

/**
 * Where the final export archive or document should be saved.
 */
enum class OutputDestination {
    SHARE_SHEET,
    PERSISTENT_DIRECTORY,
    GOOGLE_DRIVE,
    ONEDRIVE,
    DROPBOX,
    WEBDAV_NAS
}

/**
 * Configuration options selected by user before starting an export.
 */
data class ExportOptions(
    val format: ExportFormat = ExportFormat.HTML,
    val includeMedia: Boolean = true,
    val maxMessagesPerFile: Int = 0,
    val theme: ExportTheme = ExportTheme.LIGHT,
    val dateRange: DateRangeFilter = DateRangeFilter.ALL_TIME,
    val customStartDate: Long? = null,
    val customEndDate: Long? = null,
    val excludedMessageIds: Set<Long> = emptySet(),
    val outputType: OutputDestination = OutputDestination.SHARE_SHEET,
    val zipPassword: String? = null,
    val legalEvidenceMode: Boolean = false
)

/**
 * Result of an export operation.
 *
 * @property success True if generation completed with no fatal errors.
 * @property outputFile The created file (or zip archive) in cache or target directory.
 * @property exportedThreadCount How many threads were bundled.
 * @property totalMessageCount Total messages processed.
 * @property totalPartsCount Total paginated document files produced.
 * @property errorMessage Error description if success is false.
 */
data class ExportResult(
    val success: Boolean,
    val outputFile: File? = null,
    val exportedThreadCount: Int = 0,
    val totalMessageCount: Int = 0,
    val totalPartsCount: Int = 0,
    val errorMessage: String? = null
)
