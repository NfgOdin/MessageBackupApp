package com.messagebackup.app.domain.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.messagebackup.app.data.repository.TelephonyRepository
import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.DateRangeFilter
import com.messagebackup.app.model.ExportFormat
import com.messagebackup.app.model.ExportOptions
import com.messagebackup.app.model.ExportResult
import com.messagebackup.app.model.MessageItem
import com.messagebackup.app.model.OutputDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Core export engine that processes selected conversation threads, applies date filters,
 * extracts MMS attachments, paginates messages into parts (Part 1 = oldest, Part N = newest),
 * writes styled documents (HTML/MD/TXT), and outputs to either the app cache for sharing
 * or directly to a user-selected SAF folder.
 */
class ExportEngine(
    private val context: Context,
    private val telephonyRepository: TelephonyRepository,
    private val markdownFormatter: MarkdownFormatter = MarkdownFormatter(),
    private val htmlFormatter: HtmlFormatter = HtmlFormatter(),
    private val plainTextFormatter: PlainTextFormatter = PlainTextFormatter(),
    private val pdfFormatter: PdfFormatter = PdfFormatter(context),
    private val csvFormatter: CsvFormatter = CsvFormatter()
) {

    suspend fun exportThreads(
        threads: List<ConversationThread>,
        options: ExportOptions,
        resolveContacts: Boolean,
        customSafTreeUri: Uri? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        if (threads.isEmpty()) {
            return@withContext ExportResult(
                success = false,
                errorMessage = "No conversation threads were selected for export."
            )
        }

        try {
            val exportsDir = File(context.cacheDir, "exports")
            if (!exportsDir.exists()) {
                exportsDir.mkdirs()
            }

            val timestampStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val zipFileName = if (threads.size == 1) {
                val sanitizedName = sanitizeFilename(threads.first().displayName)
                "SMS_${sanitizedName}_$timestampStr.zip"
            } else {
                "SMS_Export_${threads.size}_Threads_$timestampStr.zip"
            }

            val zipFile = File(exportsDir, zipFileName)
            var totalProcessedMessages = 0
            var totalGeneratedParts = 0

            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                for (thread in threads) {
                    var messages = telephonyRepository.getMessagesForThread(thread.threadId, resolveContacts)

                    // Apply Exclusion Filter
                    if (options.excludedMessageIds.isNotEmpty()) {
                        messages = messages.filter { !options.excludedMessageIds.contains(it.messageId) }
                    }

                    // Apply Date Range Filter (including Custom Date Range)
                    messages = filterMessagesByDate(
                        messages = messages,
                        filter = options.dateRange,
                        customStart = options.customStartDate,
                        customEnd = options.customEndDate
                    )
                    totalProcessedMessages += messages.size

                    val threadDirName = sanitizeFilename("${thread.displayName}_${thread.threadId}")
                    val mediaDirEntry = "$threadDirName/media/"

                    // Extract and bundle media attachments
                    if (options.includeMedia) {
                        val addedEntryPaths = mutableSetOf<String>()
                        for (msg in messages) {
                            for (att in msg.attachments) {
                                val entryPath = "$mediaDirEntry${att.fileName}"
                                if (addedEntryPaths.add(entryPath)) {
                                    val bytes = telephonyRepository.getAttachmentBytes(att.contentUri)
                                    if (bytes != null && bytes.isNotEmpty()) {
                                        zipOut.putNextEntry(ZipEntry(entryPath))
                                        zipOut.write(bytes)
                                        zipOut.closeEntry()
                                    }
                                }
                            }
                        }
                    }

                    // Paginate messages into parts
                    val chunks = chunkMessages(messages, options.maxMessagesPerFile)
                    val totalParts = chunks.size
                    totalGeneratedParts += totalParts

                    for ((index, chunk) in chunks.withIndex()) {
                        val partNum = index + 1 // 1-based: Part 1 is oldest
                        val ext = options.format.extension

                        val currentFileName = if (totalParts == 1) {
                            "conversation.$ext"
                        } else {
                            "conversation_part_%02d.$ext".format(partNum)
                        }

                        val prevFileName = if (partNum > 1) {
                            "conversation_part_%02d.$ext".format(partNum - 1)
                        } else null

                        val nextFileName = if (partNum < totalParts) {
                            "conversation_part_%02d.$ext".format(partNum + 1)
                        } else null

                        val zipEntryPath = "$threadDirName/$currentFileName"
                        zipOut.putNextEntry(ZipEntry(zipEntryPath))

                        if (options.format == ExportFormat.PDF) {
                            val pdfBytes = pdfFormatter.generatePdf(
                                thread = thread,
                                messages = chunk,
                                currentPart = partNum,
                                totalParts = totalParts
                            )
                            zipOut.write(pdfBytes)
                        } else {
                            val documentContent = when (options.format) {
                                ExportFormat.HTML -> htmlFormatter.formatConversationPart(
                                    thread = thread,
                                    messages = chunk,
                                    currentPart = partNum,
                                    totalParts = totalParts,
                                    prevFileName = prevFileName,
                                    nextFileName = nextFileName,
                                    theme = options.theme,
                                    mediaSubdir = "media/"
                                )
                                ExportFormat.CSV -> csvFormatter.formatConversation(
                                    thread = thread,
                                    messages = chunk
                                )
                                ExportFormat.MARKDOWN -> markdownFormatter.formatConversationPart(
                                    thread = thread,
                                    messages = chunk,
                                    currentPart = partNum,
                                    totalParts = totalParts,
                                    prevFileName = prevFileName,
                                    nextFileName = nextFileName,
                                    mediaSubdir = "media/"
                                )
                                ExportFormat.PLAIN_TEXT -> plainTextFormatter.formatConversation(
                                    thread = thread,
                                    messages = chunk
                                )
                                ExportFormat.PDF -> ""
                            }
                            zipOut.write(documentContent.toByteArray(Charsets.UTF_8))
                        }
                        zipOut.closeEntry()
                    }
                }
            }

            // If user selected persistent SAF destination, copy archive to SAF folder
            if (options.outputType == OutputDestination.PERSISTENT_DIRECTORY && customSafTreeUri != null) {
                saveToSafDirectory(zipFile, customSafTreeUri)
            }

            ExportResult(
                success = true,
                outputFile = zipFile,
                exportedThreadCount = threads.size,
                totalMessageCount = totalProcessedMessages,
                totalPartsCount = totalGeneratedParts
            )
        } catch (e: Exception) {
            ExportResult(
                success = false,
                errorMessage = "Export failed: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    private fun chunkMessages(messages: List<MessageItem>, maxPerFile: Int): List<List<MessageItem>> {
        if (messages.isEmpty()) return listOf(emptyList())
        if (maxPerFile <= 0 || messages.size <= maxPerFile) {
            return listOf(messages)
        }
        return messages.chunked(maxPerFile)
    }

    private fun filterMessagesByDate(
        messages: List<MessageItem>,
        filter: DateRangeFilter,
        customStart: Long? = null,
        customEnd: Long? = null
    ): List<MessageItem> {
        val now = System.currentTimeMillis()
        return when (filter) {
            DateRangeFilter.ALL_TIME -> messages
            DateRangeFilter.LAST_30_DAYS -> {
                val cutoff = now - (30L * 24 * 60 * 60 * 1000)
                messages.filter { it.timestamp >= cutoff }
            }
            DateRangeFilter.LAST_90_DAYS -> {
                val cutoff = now - (90L * 24 * 60 * 60 * 1000)
                messages.filter { it.timestamp >= cutoff }
            }
            DateRangeFilter.LAST_YEAR -> {
                val cutoff = now - (365L * 24 * 60 * 60 * 1000)
                messages.filter { it.timestamp >= cutoff }
            }
            DateRangeFilter.CUSTOM -> {
                messages.filter { msg ->
                    val afterStart = customStart == null || msg.timestamp >= customStart
                    val beforeEnd = customEnd == null || msg.timestamp <= customEnd
                    afterStart && beforeEnd
                }
            }
        }
    }

    private fun saveToSafDirectory(file: File, safTreeUri: Uri) {
        val rootDoc = DocumentFile.fromTreeUri(context, safTreeUri) ?: return
        val newDocFile = rootDoc.createFile("application/zip", file.name) ?: return

        context.contentResolver.openOutputStream(newDocFile.uri)?.use { outStream ->
            FileInputStream(file).use { inStream ->
                inStream.copyTo(outStream)
            }
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(50)
            .ifBlank { "thread" }
    }
}
