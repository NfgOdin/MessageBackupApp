package com.messagebackup.app.domain.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Native Android PDF Generator that renders structured conversation documents
 * with clear timestamped bubbles, metadata headers, and pagination.
 */
class PdfFormatter(private val context: Context) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
        .withZone(ZoneId.systemDefault())

    fun generatePdf(
        thread: ConversationThread,
        messages: List<MessageItem>,
        currentPart: Int = 1,
        totalParts: Int = 1
    ): ByteArray {
        val pdfDocument = PdfDocument()

        val pageWidth = 595 // Standard A4 width in points (72 dpi)
        val pageHeight = 842 // Standard A4 height in points
        val margin = 36f

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(28, 30, 33)
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.rgb(101, 103, 107)
            textSize = 10f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.rgb(5, 5, 5)
            textSize = 10.5f
            isAntiAlias = true
        }

        val senderPaint = Paint().apply {
            color = Color.rgb(101, 103, 107)
            textSize = 8.5f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val bubbleInPaint = Paint().apply {
            color = Color.rgb(240, 242, 245)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val bubbleOutPaint = Paint().apply {
            color = Color.rgb(220, 237, 255)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        var y = margin + 10f

        // Document Header
        val partHeader = if (totalParts > 1) " (Part $currentPart of $totalParts)" else ""
        canvas.drawText("Conversation with ${thread.displayName}$partHeader", margin, y, titlePaint)
        y += 18f
        canvas.drawText("Address: ${thread.address}  •  Messages: ${messages.size}  •  Exported: ${dateFormatter.format(Instant.now())}", margin, y, metaPaint)
        y += 14f

        canvas.drawLine(margin, y, pageWidth - margin, y, metaPaint)
        y += 20f

        for (msg in messages) {
            val isOut = (msg.direction == MessageDirection.OUTGOING)
            val senderLabel = if (isOut) "Me" else msg.sender
            val timeStr = timeFormatter.format(Instant.ofEpochMilli(msg.timestamp))

            val maxBubbleWidth = 360f
            val lines = wrapText(msg.body.ifBlank { "(Media or empty message)" }, textPaint, maxBubbleWidth - 20f)
            val bubbleHeight = (lines.size * 14f) + 24f

            // Start new page if overflowing
            if (y + bubbleHeight + 20f > pageHeight - margin) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = margin + 10f
            }

            val bubbleX = if (isOut) pageWidth - margin - maxBubbleWidth else margin

            // Draw sender label
            canvas.drawText(senderLabel, bubbleX + 4f, y - 3f, senderPaint)

            // Draw bubble container
            val bubbleRect = android.graphics.RectF(bubbleX, y, bubbleX + maxBubbleWidth, y + bubbleHeight)
            canvas.drawRoundRect(bubbleRect, 10f, 10f, if (isOut) bubbleOutPaint else bubbleInPaint)

            // Draw text content
            var textY = y + 16f
            for (line in lines) {
                canvas.drawText(line, bubbleX + 10f, textY, textPaint)
                textY += 14f
            }

            // Draw timestamp
            canvas.drawText(timeStr, bubbleX + maxBubbleWidth - 48f, y + bubbleHeight - 6f, metaPaint)

            y += bubbleHeight + 14f
        }

        pdfDocument.finishPage(page)

        val outputStream = ByteArrayOutputStream()
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        return outputStream.toByteArray()
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val paragraphs = text.split("\n")
        for (p in paragraphs) {
            if (p.isBlank()) {
                result.add("")
                continue
            }
            val words = p.split(" ")
            var currentLine = ""
            for (word in words) {
                val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    currentLine = candidate
                } else {
                    if (currentLine.isNotEmpty()) result.add(currentLine)
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty()) result.add(currentLine)
        }
        return if (result.isEmpty()) listOf("") else result
    }
}
