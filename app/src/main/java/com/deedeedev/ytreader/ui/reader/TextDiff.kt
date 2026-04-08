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

private const val DIFF_SIZE_LIMIT = 500_000

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
    if (n > DIFF_SIZE_LIMIT && m > DIFF_SIZE_LIMIT) {
        return listOf(
            DiffHunk(
                type = DiffType.DELETE,
                oldStart = 0,
                oldEnd = n,
                newStart = 0,
                newEnd = 0
            ),
            DiffHunk(
                type = DiffType.INSERT,
                oldStart = n,
                oldEnd = n,
                newStart = 0,
                newEnd = m
            )
        )
    }

    val edits = findEditScript(oldText, newText)
    return editsToHunks(edits, oldText, newText)
}

private data class Edit(val type: DiffType, val char: Char?)

private fun findEditScript(oldText: String, newText: String): List<Edit> {
    val result = mutableListOf<Edit>()
    hirschberg(oldText, newText, 0, oldText.length, 0, newText.length, result)
    return result
}

private fun hirschberg(
    a: String, b: String,
    aStart: Int, aEnd: Int,
    bStart: Int, bEnd: Int,
    result: MutableList<Edit>
) {
    val n = aEnd - aStart
    val m = bEnd - bStart

    if (n == 0) {
        for (j in bStart until bEnd) result.add(Edit(DiffType.INSERT, b[j]))
        return
    }
    if (m == 0) {
        for (i in aStart until aEnd) result.add(Edit(DiffType.DELETE, a[i]))
        return
    }
    if (n <= 1 || m <= 1) {
        result.addAll(smallEditScript(a, aStart, aEnd, b, bStart, bEnd))
        return
    }

    val mid = aStart + n / 2

    val l = lcsRowForward(a, aStart, mid, b, bStart, bEnd)
    val r = lcsRowReverse(a, mid, aEnd, b, bStart, bEnd)

    var maxSum = -1
    var bestJ = 0
    for (j in 0..m) {
        val sum = l[j] + r[j]
        if (sum > maxSum) {
            maxSum = sum
            bestJ = j
        }
    }

    val splitJ = bStart + bestJ
    hirschberg(a, b, aStart, mid, bStart, splitJ, result)
    hirschberg(a, b, mid, aEnd, splitJ, bEnd, result)
}

private fun smallEditScript(
    a: String, aStart: Int, aEnd: Int,
    b: String, bStart: Int, bEnd: Int
): List<Edit> {
    val n = aEnd - aStart
    val m = bEnd - bStart
    val dp = Array(n + 1) { IntArray(m + 1) }

    for (i in 1..n) {
        for (j in 1..m) {
            if (a[aStart + i - 1] == b[bStart + j - 1]) {
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
        if (i > 0 && j > 0 && a[aStart + i - 1] == b[bStart + j - 1]) {
            edits.add(Edit(DiffType.EQUAL, a[aStart + i - 1]))
            i--
            j--
        } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
            edits.add(Edit(DiffType.INSERT, b[bStart + j - 1]))
            j--
        } else if (i > 0) {
            edits.add(Edit(DiffType.DELETE, a[aStart + i - 1]))
            i--
        }
    }

    return edits.reversed()
}

private fun lcsRowForward(
    a: String, aStart: Int, aEnd: Int,
    b: String, bStart: Int, bEnd: Int
): IntArray {
    val n = aEnd - aStart
    val m = bEnd - bStart
    var prev = IntArray(m + 1)
    var curr = IntArray(m + 1)

    for (i in 0 until n) {
        for (j in 0 until m) {
            curr[j + 1] = if (a[aStart + i] == b[bStart + j]) {
                prev[j] + 1
            } else {
                maxOf(prev[j + 1], curr[j])
            }
        }
        val tmp = prev
        prev = curr
        curr = tmp
        curr.fill(0)
    }

    return prev
}

private fun lcsRowReverse(
    a: String, aStart: Int, aEnd: Int,
    b: String, bStart: Int, bEnd: Int
): IntArray {
    val n = aEnd - aStart
    val m = bEnd - bStart
    var prev = IntArray(m + 1)
    var curr = IntArray(m + 1)

    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            curr[j] = if (a[aStart + i] == b[bStart + j]) {
                prev[j + 1] + 1
            } else {
                maxOf(prev[j], curr[j + 1])
            }
        }
        val tmp = prev
        prev = curr
        curr = tmp
        curr.fill(0)
    }

    return prev
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
