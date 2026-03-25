package com.deedeedev.ytreader.ui.reader

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun shareVideoNotesMarkdown(context: Context, markdown: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_TEXT, markdown)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Export annotations"))
}

internal fun shareVideoNotesPdf(
    context: Context,
    title: String,
    videoId: String,
    selectedTypes: Set<VideoAnnotationType>,
    items: List<VideoAnnotationItem>
) {
    val pdfFile = createVideoNotesPdfFile(
        context = context,
        title = title,
        videoId = videoId,
        selectedTypes = selectedTypes,
        items = items
    )
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        pdfFile
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(pdfFile.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Export annotations"))
}

internal fun buildVideoNotesMarkdown(
    title: String,
    videoId: String,
    selectedTypes: Set<VideoAnnotationType>,
    items: List<VideoAnnotationItem>
): String {
    val exportTitle = title.ifBlank { videoId }
    return buildString {
        append("# ")
        append(exportTitle)
        append("\n\n")
        append("- Video ID: ")
        append(videoId)
        append("\n")
        append("- Filter: ")
        append(annotationFilterLabel(selectedTypes))
        append("\n")
        append("- Exported items: ")
        append(items.size)
        append("\n\n")

        items.forEachIndexed { index, item ->
            append("## ")
            append(index + 1)
            append(". ")
            append(annotationTypeLabel(item.type))
            append("\n\n")
            append("- Title: ")
            append(item.title)
            append("\n")
            append("- Updated: ")
            append(formatVideoAnnotationUpdatedAt(item.updatedAt))
            append("\n")
            append("- Position: ")
            append(item.progressPercent)
            append("%\n")
            item.note?.let { note ->
                append("- Note: ")
                append(note.replace("\n", " "))
                append("\n")
            }
            append("\n")
        }
    }
}

internal fun buildVideoNotesExportFileName(title: String, videoId: String, extension: String): String {
    val baseName = sanitizeVideoNotesFileName(title.ifBlank { videoId })
    val normalizedExtension = extension.removePrefix(".").ifBlank { "txt" }
    return "$baseName.$normalizedExtension"
}

internal fun formatVideoAnnotationUpdatedAt(updatedAt: Long): String {
    if (updatedAt <= 0L) return ""
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(updatedAt))
}

internal fun annotationTypeLabel(type: VideoAnnotationType): String = when (type) {
    VideoAnnotationType.BOOKMARK -> "Bookmark"
    VideoAnnotationType.NOTE -> "Note"
    VideoAnnotationType.HIGHLIGHT -> "Highlight"
}

internal fun annotationFilterLabel(selectedTypes: Set<VideoAnnotationType>): String {
    if (selectedTypes.isEmpty()) return "All"
    return buildList {
        if (VideoAnnotationType.BOOKMARK in selectedTypes) add("Bookmarks")
        if (VideoAnnotationType.HIGHLIGHT in selectedTypes) add("Highlights")
        if (VideoAnnotationType.NOTE in selectedTypes) add("Notes")
    }.joinToString(", ")
}

private fun createVideoNotesPdfFile(
    context: Context,
    title: String,
    videoId: String,
    selectedTypes: Set<VideoAnnotationType>,
    items: List<VideoAnnotationItem>
): File {
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val outputFile = File(exportDir, buildVideoNotesExportFileName(title, videoId, "pdf"))
    createVideoNotesPdf(
        outputFile = outputFile,
        title = title,
        videoId = videoId,
        selectedTypes = selectedTypes,
        items = items
    )
    return outputFile
}

private fun createVideoNotesPdf(
    outputFile: File,
    title: String,
    videoId: String,
    selectedTypes: Set<VideoAnnotationType>,
    items: List<VideoAnnotationItem>
) {
    val document = PdfDocument()
    try {
        val renderer = VideoNotesPdfRenderer(document)
        renderer.drawTitle(title.ifBlank { videoId })
        renderer.drawBodyLine("Video ID: $videoId")
        renderer.drawBodyLine("Filter: ${annotationFilterLabel(selectedTypes)}")
        renderer.drawBodyLine("Exported items: ${items.size}")
        renderer.drawSpacer()

        items.forEachIndexed { index, item ->
            renderer.drawSectionHeading("${index + 1}. ${annotationTypeLabel(item.type)}")
            renderer.drawBodyLine("Title: ${item.title}")
            val updatedLabel = formatVideoAnnotationUpdatedAt(item.updatedAt).ifBlank { "Unknown" }
            renderer.drawBodyLine("Updated: $updatedLabel")
            renderer.drawBodyLine("Position: ${item.progressPercent}%")
            item.note?.let { note ->
                renderer.drawBodyLine("Note: ${note.replace(Regex("\\s+"), " ").trim()}")
            }
            renderer.drawSpacer()
        }

        renderer.finish()
        FileOutputStream(outputFile).use(document::writeTo)
    } finally {
        document.close()
    }
}

private fun sanitizeVideoNotesFileName(value: String): String {
    val sanitized = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .take(60)
    return sanitized.ifBlank { "annotations" }
}

private class VideoNotesPdfRenderer(private val document: PdfDocument) {
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f
    private val contentWidth = pageWidth - (margin * 2f)
    private val bottomLimit = pageHeight - margin

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        isFakeBoldText = true
    }
    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f
        isFakeBoldText = true
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f
    }

    private var pageNumber = 0
    private var currentPage: PdfDocument.Page? = null
    private var currentY = margin

    fun drawTitle(text: String) {
        drawParagraph(text, titlePaint, 10f)
    }

    fun drawSectionHeading(text: String) {
        drawParagraph(text, headingPaint, 8f)
    }

    fun drawBodyLine(text: String) {
        drawParagraph(text, bodyPaint, 4f)
    }

    fun drawSpacer(height: Float = 8f) {
        ensureSpace(height)
        currentY += height
    }

    fun finish() {
        currentPage?.let(document::finishPage)
        currentPage = null
    }

    private fun drawParagraph(text: String, paint: Paint, paragraphSpacing: Float) {
        val lines = wrapText(text, paint)
        val lineHeight = paint.textSize + 4f
        lines.forEach { line ->
            ensureSpace(lineHeight)
            currentPage!!.canvas.drawText(line, margin, currentY, paint)
            currentY += lineHeight
        }
        ensureSpace(paragraphSpacing)
        currentY += paragraphSpacing
    }

    private fun wrapText(text: String, paint: Paint): List<String> {
        val paragraphs = text.split('\n')
        val lines = mutableListOf<String>()
        for (paragraph in paragraphs) {
            val normalized = paragraph.trim()
            if (normalized.isEmpty()) {
                lines += ""
                continue
            }

            var remaining = normalized
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, contentWidth, null)
                if (count >= remaining.length) {
                    lines += remaining
                    remaining = ""
                } else {
                    val breakIndex = remaining.lastIndexOf(' ', startIndex = count - 1)
                        .takeIf { it > 0 } ?: count
                    lines += remaining.substring(0, breakIndex).trimEnd()
                    remaining = remaining.substring(breakIndex).trimStart()
                }
            }
        }
        return if (lines.isEmpty()) listOf("") else lines
    }

    private fun ensureSpace(requiredHeight: Float) {
        if (currentPage == null || currentY + requiredHeight > bottomLimit) {
            currentPage?.let(document::finishPage)
            pageNumber += 1
            currentPage = document.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            currentY = margin
        }
    }
}
