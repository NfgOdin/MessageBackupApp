package com.messagebackup.app.model

import java.io.File

/**
 * Supported document export formats.
 */
enum class ExportFormat(val extension: String, val displayName: String, val mimeType: String) {
    PDF("pdf", "PDF Document (.pdf)", "application/pdf"),
    HTML("html", "HTML Web Page (.html)", "text/html"),
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
    LAST_YEAR("Past Year")
}

/**
 * Where the final export archive or document should be saved.
 */
enum class OutputDestination {
    SHARE_SHEET,
    PERSISTENT_DIRECTORY
}

/**
 * Configuration options selected by user before starting an export.
 *
 * @property format Selected document format.
 * @property includeMedia Whether to extract and bundle images/audio from MMS messages.
 * @property maxMessagesPerFile Maximum messages per document chunk (0 = unlimited).
 * @property theme Light, Dark, or System theme for HTML styling.
 * @property dateRange Date filter restriction.
 * @property outputType Whether to output to SAF custom folder or directly to Share Sheet.
 */
data class ExportOptions(
    val format: ExportFormat = ExportFormat.HTML,
    val includeMedia: Boolean = true,
    val maxMessagesPerFile: Int = 0,
    val theme: ExportTheme = ExportTheme.LIGHT,
    val dateRange: DateRangeFilter = DateRangeFilter.ALL_TIME,
    val outputType: OutputDestination = OutputDestination.SHARE_SHEET
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
