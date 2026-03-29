package com.deedeedev.ytreader.ui.reader

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.deedeedev.ytreader.R
import com.google.gson.GsonBuilder
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
    context.startActivity(
        Intent.createChooser(shareIntent, context.getString(R.string.video_notes_export_annotations))
    )
}

internal fun shareVideoNotesPdf(
    context: Context,
    title: String,
    channelName: String,
    uploadDate: Long,
    videoId: String,
    selectedTypes: Set<VideoAnnotationType>,
    items: List<VideoAnnotationItem>
) {
    val pdfFile = createVideoNotesPdfFile(
        context = context,
        title = title,
        channelName = channelName,
        uploadDate = uploadDate,
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
    context.startActivity(
        Intent.createChooser(shareIntent, context.getString(R.string.video_notes_export_annotations))
    )
}

internal fun shareVideoNotesJson(
    context: Context,
    title: String,
    videoId: String,
    selectedTypes: Set<VideoAnnotationType>,
    items: List<VideoAnnotationItem>
) {
    val json = buildVideoNotesJson(context, title, videoId, selectedTypes, items)
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val outputFile = File(exportDir, buildVideoNotesExportFileName(title, videoId, "json"))
    outputFile.writeText(json)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        outputFile
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(outputFile.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(shareIntent, context.getString(R.string.video_notes_export_annotations))
    )
}

internal fun shareVideoNotesCsv(
    context: Context,
    title: String,
    videoId: String,
    selectedTypes: Set<VideoAnnotationType>,
    items: List<VideoAnnotationItem>
) {
    val csv = buildVideoNotesCsv(context, title, videoId, selectedTypes, items)
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val outputFile = File(exportDir, buildVideoNotesExportFileName(title, videoId, "csv"))
    outputFile.writeText(csv)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        outputFile
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(outputFile.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(shareIntent, context.getString(R.string.video_notes_export_annotations))
    )
}

internal fun buildVideoNotesMarkdown(
    context: Context,
    title: String,
    videoId: String,
    selectedTypes: Set<VideoAnnotationType>,
    items: List<VideoAnnotationItem>
): String {
    val exportTitle = title.ifBlank { videoId }
    val resources = context.resources
    return buildString {
        append("# ")
        append(exportTitle)
        append("\n\n")
        append("- ")
        append(resources.getString(R.string.video_notes_export_video_id_label, videoId))
        append("\n")
        append("- ")
        append(resources.getString(R.string.video_notes_export_filter_label, annotationFilterLabel(resources, selectedTypes)))
        append("\n")
        append("- ")
        append(resources.getString(R.string.video_notes_export_items_label, items.size))
        append("\n\n")

        items.forEachIndexed { index, item ->
            append("## ")
            append(resources.getString(R.string.video_notes_export_item_type_format, index + 1, annotationTypeLabel(resources, item.type)))
            append("\n\n")
            append("- ")
            append(resources.getString(R.string.video_notes_export_title_label, item.title))
            append("\n")
            append("- ")
            append(resources.getString(R.string.video_notes_export_updated_label, formatVideoAnnotationUpdatedAt(item.updatedAt)))
            append("\n")
            append("- ")
            append(resources.getString(R.string.video_notes_export_position_label, item.progressPercent))
            append("\n")
            item.note?.let { note ->
                append("- ")
                append(resources.getString(R.string.video_notes_export_note_label, note.replace("\n", " ")))
                append("\n")
            }
            append("\n")
        }
    }
}

internal fun buildVideoNotesJson(
    context: Context,
    title: String,
    videoId: String,
    selectedTypes: Set<VideoAnnotationType>,
    items: List<VideoAnnotationItem>
): String {
    val resources = context.resources
    val exportTitle = title.ifBlank { videoId }
    val gson = GsonBuilder().setPrettyPrinting().create()
    val root = linkedMapOf<String, Any?>(
        "title" to exportTitle,
        "videoId" to videoId,
        "filter" to annotationFilterLabel(resources, selectedTypes),
        "exportedItems" to items.size,
        "items" to items.map { item ->
            linkedMapOf<String, Any?>(
                "type" to annotationTypeLabel(resources, item.type),
                "title" to item.title,
                "note" to item.note,
                "color" to item.color?.name,
                "updatedAt" to formatVideoAnnotationUpdatedAt(item.updatedAt),
                "positionPercent" to item.progressPercent
            )
        }
    )
    return gson.toJson(root)
}

internal fun buildVideoNotesCsv(
    context: Context,
    title: String,
    videoId: String,
    selectedTypes: Set<VideoAnnotationType>,
    items: List<VideoAnnotationItem>
): String {
    val resources = context.resources
    val sb = StringBuilder()
    sb.appendLine("Type,Title,Note,Color,Updated,Position %")
    items.forEach { item ->
        sb.appendLine(
            listOf(
                escapeCsvField(annotationTypeLabel(resources, item.type)),
                escapeCsvField(item.title),
                escapeCsvField(item.note.orEmpty()),
                escapeCsvField(item.color?.name.orEmpty()),
                escapeCsvField(formatVideoAnnotationUpdatedAt(item.updatedAt)),
                escapeCsvField(item.progressPercent.toString())
            ).joinToString(",")
        )
    }
    return sb.toString()
}

private fun escapeCsvField(value: String): String {
    return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }
}

internal fun buildVideoNotesExportFileName(title: String, videoId: String, extension: String): String {
    val baseName = sanitizeVideoNotesFileName(title.ifBlank { videoId }, fallbackName = videoId)
    val normalizedExtension = extension.removePrefix(".").ifBlank { "txt" }
    return "$baseName.$normalizedExtension"
}

fun formatVideoAnnotationUpdatedAt(updatedAt: Long): String {
    if (updatedAt <= 0L) return ""
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(updatedAt))
}

internal fun annotationTypeLabel(resources: Resources, type: VideoAnnotationType): String = when (type) {
    VideoAnnotationType.BOOKMARK -> resources.getString(R.string.video_notes_type_bookmark)
    VideoAnnotationType.NOTE -> resources.getString(R.string.video_notes_type_note)
    VideoAnnotationType.HIGHLIGHT -> resources.getString(R.string.video_notes_type_highlight)
}

internal fun annotationFilterLabel(resources: Resources, selectedTypes: Set<VideoAnnotationType>): String {
    if (selectedTypes.isEmpty()) return resources.getString(R.string.video_notes_filter_all)
    return buildList {
        if (VideoAnnotationType.BOOKMARK in selectedTypes) {
            add(resources.getString(R.string.video_notes_filter_bookmarks))
        }
        if (VideoAnnotationType.HIGHLIGHT in selectedTypes) {
            add(resources.getString(R.string.video_notes_filter_highlights))
        }
        if (VideoAnnotationType.NOTE in selectedTypes) {
            add(resources.getString(R.string.video_notes_filter_notes))
        }
    }.joinToString(", ")
}

private fun createVideoNotesPdfFile(
    context: Context,
    title: String,
    channelName: String,
    uploadDate: Long,
    videoId: String,
    selectedTypes: Set<VideoAnnotationType>,
    items: List<VideoAnnotationItem>
): File {
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val outputFile = File(exportDir, buildVideoNotesExportFileName(title, videoId, "pdf"))
    createVideoNotesPdf(
        resources = context.resources,
        outputFile = outputFile,
        title = title,
        channelName = channelName,
        uploadDate = uploadDate,
        videoId = videoId,
        selectedTypes = selectedTypes,
        items = items
    )
    return outputFile
}

private fun createVideoNotesPdf(
    resources: Resources,
    outputFile: File,
    title: String,
    channelName: String,
    uploadDate: Long,
    videoId: String,
    selectedTypes: Set<VideoAnnotationType>,
    items: List<VideoAnnotationItem>
) {
    val filteredItems = items.filter { it.type != VideoAnnotationType.BOOKMARK }
    val document = PdfDocument()
    try {
        val renderer = VideoNotesPdfRenderer(document)
        val exportTitle = title.ifBlank { videoId }
        val uploadDateStr = formatUploadDate(uploadDate)
        val itemCount = filteredItems.size

        renderer.drawHeader(exportTitle, channelName, uploadDateStr, itemCount)
        renderer.drawDivider()

        filteredItems.forEachIndexed { index, item ->
            renderer.drawAnnotationBlock(
                index = index + 1,
                total = itemCount,
                quote = item.title,
                note = item.note,
                highlightColor = item.color,
                progressPercent = item.progressPercent,
                updatedAt = item.updatedAt
            )
        }

        renderer.finish()
        FileOutputStream(outputFile).use(document::writeTo)
    } finally {
        document.close()
    }
}

private fun formatUploadDate(uploadDate: Long): String {
    if (uploadDate <= 0L) return ""
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(uploadDate))
}

private fun sanitizeVideoNotesFileName(value: String, fallbackName: String): String {
    val sanitized = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .take(60)
    return sanitized.ifBlank { fallbackName }
}

private class VideoNotesPdfRenderer(private val document: PdfDocument) {
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f
    private val quoteIndent = 16f
    private val quoteContentStart = margin + quoteIndent + 8f
    private val contentWidth = pageWidth - margin - quoteContentStart
    private val bottomLimit = pageHeight - margin
    private val accentBarWidth = 4f

    private val colorRed = Color.parseColor("#E53935")
    private val colorBlue = Color.parseColor("#1E88E5")
    private val colorGreen = Color.parseColor("#43A047")
    private val colorYellow = Color.parseColor("#FDD835")

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        isFakeBoldText = true
        color = Color.parseColor("#212121")
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f
        color = Color.parseColor("#757575")
    }
    private val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f
        textSkewX = -0.25f
        color = Color.parseColor("#424242")
    }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        color = Color.parseColor("#616161")
    }
    private val metadataPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        color = Color.parseColor("#9E9E9E")
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f
        color = Color.parseColor("#E0E0E0")
    }

    private var pageNumber = 0
    private var currentPage: PdfDocument.Page? = null
    private var currentY = margin

    fun drawHeader(title: String, channelName: String, uploadDate: String, itemCount: Int) {
        drawParagraph(title, titlePaint, 4f)
        
        val channelAndDate = buildString {
            if (channelName.isNotBlank()) {
                append(channelName)
                if (uploadDate.isNotBlank()) {
                    append(" · ")
                    append(uploadDate)
                }
            } else if (uploadDate.isNotBlank()) {
                append(uploadDate)
            }
        }
        if (channelAndDate.isNotBlank()) {
            drawParagraph(channelAndDate, subtitlePaint, 16f)
        }

        drawParagraph("$itemCount items", subtitlePaint, 8f)
    }

    fun drawDivider() {
        ensureSpace(4f)
        currentPage!!.canvas.drawLine(margin, currentY, pageWidth - margin, currentY, dividerPaint)
        currentY += 16f
    }

    fun drawAnnotationBlock(
        index: Int,
        total: Int,
        quote: String,
        note: String?,
        highlightColor: HighlightColor?,
        progressPercent: Int,
        updatedAt: Long
    ) {
        val accentColor = when (highlightColor) {
            HighlightColor.RED -> colorRed
            HighlightColor.BLUE -> colorBlue
            HighlightColor.GREEN -> colorGreen
            HighlightColor.YELLOW -> colorYellow
            null -> Color.parseColor("#9E9E9E")
        }

        ensureSpace(24f)
        
        val accentBarLeft = quoteContentStart - quoteIndent - 4f
        currentPage!!.canvas.drawRect(
            accentBarLeft,
            currentY - quotePaint.textSize,
            accentBarLeft + accentBarWidth,
            currentY + (quotePaint.textSize * 2),
            Paint().apply { this.color = accentColor }
        )

        val wrappedQuote = wrapText(quote, quotePaint)
        val lineHeight = quotePaint.textSize + 4f
        wrappedQuote.forEach { line ->
            ensureSpace(lineHeight)
            currentPage!!.canvas.drawText(line, quoteContentStart, currentY, quotePaint)
            currentY += lineHeight
        }

        if (!note.isNullOrBlank()) {
            currentY += 8f
            ensureSpace(notePaint.textSize + 4f)
            currentPage!!.canvas.drawText("Note: ", quoteContentStart, currentY, Paint(notePaint).apply { isFakeBoldText = true })
            val noteWidth = Paint(notePaint).measureText("Note: ")
            val wrappedNote = wrapText(note, notePaint, contentWidth - noteWidth)
            wrappedNote.forEach { line ->
                ensureSpace(notePaint.textSize + 4f)
                currentPage!!.canvas.drawText(line, quoteContentStart + noteWidth, currentY, notePaint)
                currentY += (notePaint.textSize + 4f)
            }
        }

        currentY += 8f
        ensureSpace(metadataPaint.textSize + 4f)
        val dateStr = formatVideoAnnotationUpdatedAt(updatedAt)
        val metadata = "$index/$total · $progressPercent%${if (dateStr.isNotBlank()) " · $dateStr" else ""}"
        currentPage!!.canvas.drawText(metadata, quoteContentStart, currentY, metadataPaint)
        currentY += 40f
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

    private fun wrapText(text: String, paint: Paint, maxWidth: Float = contentWidth): List<String> {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return listOf("")

        val lines = mutableListOf<String>()
        var remaining = normalized
        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, maxWidth, null)
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
        return lines
    }

    private fun ensureSpace(requiredHeight: Float) {
        if (currentPage == null || currentY + requiredHeight > bottomLimit) {
            currentPage?.let {
                drawPageNumber()
                document.finishPage(it)
            }
            pageNumber += 1
            currentPage = document.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            currentY = margin
        }
    }

    private fun drawPageNumber() {
        currentPage?.let { page ->
            val pageNumPaint = Paint(metadataPaint).apply { textAlign = Paint.Align.RIGHT }
            page.canvas.drawText("Page $pageNumber", pageWidth - margin, bottomLimit + 16f, pageNumPaint)
        }
    }
}
