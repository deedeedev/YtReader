package com.deedeedev.ytreader.data

import android.content.Context
import java.io.File
import java.util.Locale

private const val VIDEO_THUMBNAIL_DIRECTORY = "video_thumbnails"

object VideoThumbnailStore {
    fun directory(context: Context): File {
        return File(context.filesDir, VIDEO_THUMBNAIL_DIRECTORY).apply { mkdirs() }
    }

    fun resolve(context: Context, relativePath: String?): File? {
        val normalizedPath = relativePath?.trim().orEmpty()
        if (normalizedPath.isBlank()) {
            return null
        }
        val file = File(directory(context), normalizedPath)
        return file.takeIf { it.exists() }
    }

    fun listFiles(context: Context): List<File> {
        return directory(context).listFiles()?.sortedBy { it.name } ?: emptyList()
    }

    fun save(context: Context, videoId: String, sourceUrl: String, bytes: ByteArray): String {
        val thumbnailsDir = directory(context)
        deleteForVideo(context, videoId)
        val fileName = buildFileName(videoId, extensionForUrl(sourceUrl))
        val targetFile = File(thumbnailsDir, fileName)
        targetFile.outputStream().use { output ->
            output.write(bytes)
        }
        return fileName
    }

    fun delete(context: Context, relativePath: String?) {
        val file = resolve(context, relativePath) ?: return
        file.delete()
    }

    fun deleteForVideo(context: Context, videoId: String) {
        val prefix = safeVideoId(videoId)
        listFiles(context)
            .filter { it.name == prefix || it.name.startsWith("$prefix.") }
            .forEach { it.delete() }
    }

    internal fun buildFileName(videoId: String, extension: String): String {
        return "${safeVideoId(videoId)}.${normalizeExtension(extension)}"
    }

    internal fun safeVideoId(videoId: String): String {
        return videoId.trim().ifBlank { "video" }
            .map { char ->
                if (char.isLetterOrDigit() || char == '-' || char == '_') {
                    char
                } else {
                    '_'
                }
            }
            .joinToString(separator = "")
    }

    internal fun extensionForUrl(url: String): String {
        val sanitizedUrl = url.substringBefore('?').substringBefore('#').trim()
        val candidate = sanitizedUrl.substringAfterLast('.', "")
        return normalizeExtension(candidate)
    }

    private fun normalizeExtension(extension: String): String {
        return when (extension.lowercase(Locale.US)) {
            "jpg", "jpeg", "png", "webp" -> extension.lowercase(Locale.US)
            else -> "img"
        }
    }
}
