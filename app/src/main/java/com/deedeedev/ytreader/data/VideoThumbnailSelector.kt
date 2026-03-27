package com.deedeedev.ytreader.data

import org.schabi.newpipe.extractor.Image

fun preferredThumbnailUrl(thumbnails: List<Image>): String? {
    return thumbnails.asSequence()
        .filter { it.url.isNotBlank() }
        .sortedWith(
            compareByDescending<Image> { resolutionPriority(it.estimatedResolutionLevel) }
                .thenByDescending { imageArea(it) }
        )
        .map { it.url }
        .firstOrNull()
}

private fun resolutionPriority(level: Image.ResolutionLevel): Int {
    return when (level) {
        Image.ResolutionLevel.MEDIUM -> 4
        Image.ResolutionLevel.HIGH -> 3
        Image.ResolutionLevel.LOW -> 2
        Image.ResolutionLevel.UNKNOWN -> 1
    }
}

private fun imageArea(image: Image): Long {
    val width = image.width.takeIf { it > 0 } ?: 0
    val height = image.height.takeIf { it > 0 } ?: 0
    return width.toLong() * height.toLong()
}
