package com.deedeedev.ytreader.ui.reader

data class DiffHunk(
    val type: DiffType,
    val oldStart: Int,
    val oldEnd: Int,
    val newStart: Int,
    val newEnd: Int
)

enum class DiffType {
    EQUAL,
    DELETE,
    INSERT
}

fun diff(oldText: String, newText: String): List<DiffHunk> {
    val n = oldText.length
    val m = newText.length

    if (n == 0 && m == 0) return emptyList()
    if (n == 0) {
        return listOf(
            DiffHunk(
                type = DiffType.INSERT,
                oldStart = 0,
                oldEnd = 0,
                newStart = 0,
                newEnd = m
            )
        )
    }
    if (m == 0) {
        return listOf(
            DiffHunk(
                type = DiffType.DELETE,
                oldStart = 0,
                oldEnd = n,
                newStart = 0,
                newEnd = 0
            )
        )
    }

    val edits = findEditScript(oldText, newText)
    return editsToHunks(edits, oldText, newText)
}

private data class Edit(val type: DiffType, val char: Char?)

private fun findEditScript(oldText: String, newText: String): List<Edit> {
    val n = oldText.length
    val m = newText.length
    
    val dp = Array(n + 1) { IntArray(m + 1) }
    
    for (i in 1..n) {
        for (j in 1..m) {
            if (oldText[i - 1] == newText[j - 1]) {
                dp[i][j] = dp[i - 1][j - 1] + 1
            } else {
                dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
    }
    
    val edits = mutableListOf<Edit>()
    var i = n
    var j = m
    
    while (i > 0 || j > 0) {
        if (i > 0 && j > 0 && oldText[i - 1] == newText[j - 1]) {
            edits.add(Edit(DiffType.EQUAL, oldText[i - 1]))
            i--
            j--
        } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
            edits.add(Edit(DiffType.INSERT, newText[j - 1]))
            j--
        } else if (i > 0) {
            edits.add(Edit(DiffType.DELETE, oldText[i - 1]))
            i--
        }
    }
    
    return edits.reversed()
}

private fun editsToHunks(edits: List<Edit>, oldText: String, newText: String): List<DiffHunk> {
    if (edits.isEmpty()) return emptyList()
    
    val hunks = mutableListOf<DiffHunk>()
    
    var oldPos = 0
    var newPos = 0
    var i = 0
    
    while (i < edits.size) {
        val edit = edits[i]
        
        if (edit.type == DiffType.EQUAL) {
            var equalStart = i
            while (i < edits.size && edits[i].type == DiffType.EQUAL) {
                i++
            }
            val equalCount = i - equalStart
            hunks.add(
                DiffHunk(
                    type = DiffType.EQUAL,
                    oldStart = oldPos,
                    oldEnd = oldPos + equalCount,
                    newStart = newPos,
                    newEnd = newPos + equalCount
                )
            )
            oldPos += equalCount
            newPos += equalCount
        } else {
            var deleteCount = 0
            var insertCount = 0
            
            while (i < edits.size && edits[i].type == DiffType.DELETE) {
                deleteCount++
                i++
            }
            while (i < edits.size && edits[i].type == DiffType.INSERT) {
                insertCount++
                i++
            }
            
            if (deleteCount > 0 && insertCount > 0) {
                hunks.add(
                    DiffHunk(
                        type = DiffType.DELETE,
                        oldStart = oldPos,
                        oldEnd = oldPos + deleteCount,
                        newStart = newPos,
                        newEnd = newPos
                    )
                )
                hunks.add(
                    DiffHunk(
                        type = DiffType.INSERT,
                        oldStart = oldPos,
                        oldEnd = oldPos,
                        newStart = newPos,
                        newEnd = newPos + insertCount
                    )
                )
            } else if (deleteCount > 0) {
                hunks.add(
                    DiffHunk(
                        type = DiffType.DELETE,
                        oldStart = oldPos,
                        oldEnd = oldPos + deleteCount,
                        newStart = newPos,
                        newEnd = newPos
                    )
                )
            } else if (insertCount > 0) {
                hunks.add(
                    DiffHunk(
                        type = DiffType.INSERT,
                        oldStart = oldPos,
                        oldEnd = oldPos,
                        newStart = newPos,
                        newEnd = newPos + insertCount
                    )
                )
            }
            
            oldPos += deleteCount
            newPos += insertCount
        }
    }
    
    return hunks
}

fun remapPoint(hunks: List<DiffHunk>, oldOffset: Int, oldTextLength: Int): Int? {
    if (hunks.isEmpty()) return oldOffset
    if (oldOffset < 0 || oldOffset >= oldTextLength) return null

    var currentOldOffset = 0
    var currentNewOffset = 0

    for (hunk in hunks) {
        val hunkOldLength = hunk.oldEnd - hunk.oldStart
        val hunkNewLength = hunk.newEnd - hunk.newStart
        val hunkOldEnd = currentOldOffset + hunkOldLength

        when (hunk.type) {
            DiffType.EQUAL -> {
                if (oldOffset < hunkOldEnd) {
                    val relativeOffset = oldOffset - currentOldOffset
                    return currentNewOffset + relativeOffset
                }
                currentOldOffset = hunkOldEnd
                currentNewOffset += hunkNewLength
            }
            DiffType.DELETE -> {
                if (oldOffset < hunkOldEnd) {
                    return currentNewOffset
                }
                currentOldOffset = hunkOldEnd
            }
            DiffType.INSERT -> {
                if (oldOffset < currentOldOffset) {
                    return currentNewOffset
                }
                currentNewOffset += hunkNewLength
            }
        }
    }

    return currentNewOffset
}

fun remapRange(
    hunks: List<DiffHunk>,
    oldStart: Int,
    oldEnd: Int,
    oldText: String,
    newText: String
): IntRange? {
    if (hunks.isEmpty()) {
        return if (oldStart >= 0 && oldEnd <= oldText.length && oldStart < oldEnd) {
            oldStart until oldEnd
        } else {
            null
        }
    }
    if (oldStart < 0 || oldEnd <= oldStart || oldEnd > oldText.length) {
        return null
    }

    val newStartOffset = remapPoint(hunks, oldStart, oldText.length) ?: return null
    val newEndOffset = remapPoint(hunks, oldEnd - 1, oldText.length)?.let { it + 1 } ?: return null

    if (newStartOffset >= newEndOffset) {
        return null
    }

    if (newEndOffset > newText.length || newStartOffset < 0) {
        return null
    }

    val survivingText = newText.substring(newStartOffset, newEndOffset)
    val originalText = oldText.substring(oldStart, oldEnd)

    return if (survivingText == originalText) {
        newStartOffset until newEndOffset
    } else {
        null
    }
}
