package com.deedeedev.ytreader.domain

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class CanonicalVideoRef(
    val videoId: String,
    val videoUrl: String
)

object YouTubeVideoIdNormalizer {
    private const val UNKNOWN_VIDEO_ID = "unknown"

    fun canonicalize(input: String?, fallbackUrl: String? = null): CanonicalVideoRef {
        val extracted = extractVideoId(input)
            ?: extractVideoId(fallbackUrl)

        if (extracted != null) {
            return CanonicalVideoRef(
                videoId = extracted,
                videoUrl = canonicalWatchUrl(extracted)
            )
        }

        val fallback = when {
            !input.isNullOrBlank() -> input.trim()
            !fallbackUrl.isNullOrBlank() -> fallbackUrl.trim()
            else -> ""
        }

        return CanonicalVideoRef(
            videoId = fallback.ifBlank { UNKNOWN_VIDEO_ID },
            videoUrl = fallback
        )
    }

    fun canonicalWatchUrl(videoId: String): String {
        return "https://www.youtube.com/watch?v=${videoId.trim()}"
    }

    fun extractVideoId(input: String?): String? {
        val raw = input?.trim().orEmpty()
        if (raw.isBlank()) {
            return null
        }

        if (looksLikeVideoId(raw)) {
            return raw
        }

        val url = ensureScheme(raw)
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        val segments = uri.path
            ?.split('/')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val queryParams = parseQuery(uri.rawQuery)

        val watchId = queryParams["v"].orEmpty().trim()
        if (looksLikeVideoId(watchId)) {
            return watchId
        }

        if (host == "youtu.be" || host.endsWith(".youtu.be")) {
            val shortId = segments.firstOrNull().orEmpty().trim()
            if (looksLikeVideoId(shortId)) {
                return shortId
            }
        }

        if (host.contains("youtube.com")) {
            if (segments.size >= 2 && segments[0].equals("shorts", ignoreCase = true)) {
                val shortsId = segments[1].trim()
                if (looksLikeVideoId(shortsId)) {
                    return shortsId
                }
            }
            if (segments.size >= 2 && segments[0].equals("embed", ignoreCase = true)) {
                val embedId = segments[1].trim()
                if (looksLikeVideoId(embedId)) {
                    return embedId
                }
            }
            if (segments.size >= 2 && segments[0].equals("live", ignoreCase = true)) {
                val liveId = segments[1].trim()
                if (looksLikeVideoId(liveId)) {
                    return liveId
                }
            }
        }

        return null
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) {
            return emptyMap()
        }
        return rawQuery.split('&')
            .mapNotNull { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.isEmpty()) {
                    return@mapNotNull null
                }
                val key = urlDecode(parts[0])
                if (key.isBlank()) {
                    return@mapNotNull null
                }
                val value = if (parts.size == 2) urlDecode(parts[1]) else ""
                key to value
            }
            .toMap()
    }

    private fun urlDecode(value: String): String {
        return runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
            .getOrDefault(value)
    }

    private fun ensureScheme(value: String): String {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value
        }
        return "https://${value}"
    }

    private fun looksLikeVideoId(value: String): Boolean {
        if (value.length != 11) {
            return false
        }
        return value.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}
