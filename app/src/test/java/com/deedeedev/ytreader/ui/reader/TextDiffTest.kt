package com.deedeedev.ytreader.ui.reader

import org.junit.Assert.*
import org.junit.Test

class TextDiffTest {

    @Test
    fun emptyTexts() {
        val hunks = diff("", "")
        assertTrue(hunks.isEmpty())
    }

    @Test
    fun noChanges() {
        val text = "hello world"
        val hunks = diff(text, text)
        assertEquals(1, hunks.size)
        assertEquals(DiffType.EQUAL, hunks[0].type)
        assertEquals(0, hunks[0].oldStart)
        assertEquals(text.length, hunks[0].oldEnd)
        assertEquals(0, hunks[0].newStart)
        assertEquals(text.length, hunks[0].newEnd)

        assertEquals(0, remapPoint(hunks, 0, text.length))
        assertEquals(5, remapPoint(hunks, 5, text.length))
        assertEquals(10, remapPoint(hunks, 10, text.length))
    }

    @Test
    fun insertBefore() {
        val oldText = "hello"
        val newText = "xxhello"
        val hunks = diff(oldText, newText)

        assertTrue(hunks.isNotEmpty())
        assertEquals(DiffType.INSERT, hunks.first().type)

        assertTrue(remapPoint(hunks, 0, oldText.length)!! > 0)
        assertTrue(remapPoint(hunks, 4, oldText.length)!! > 4)
    }

    @Test
    fun insertAfter() {
        val oldText = "hello"
        val newText = "helloxy"
        val hunks = diff(oldText, newText)

        assertTrue(hunks.isNotEmpty())
        assertEquals(DiffType.EQUAL, hunks.first().type)

        assertEquals(0, remapPoint(hunks, 0, oldText.length))
        assertEquals(4, remapPoint(hunks, 4, oldText.length))
    }

    @Test
    fun deleteAll() {
        val oldText = "hello world"
        val newText = ""
        val hunks = diff(oldText, newText)

        assertEquals(1, hunks.size)
        assertEquals(DiffType.DELETE, hunks[0].type)

        assertEquals(0, remapPoint(hunks, 0, oldText.length))
        assertEquals(0, remapPoint(hunks, 5, oldText.length))
    }

    @Test
    fun replaceAll() {
        val oldText = "hello"
        val newText = "world"
        val hunks = diff(oldText, newText)

        assertTrue(hunks.isNotEmpty())
        assertTrue(hunks.any { it.type == DiffType.DELETE })
        assertTrue(hunks.any { it.type == DiffType.INSERT })
    }

    @Test
    fun emptyOldText() {
        val oldText = ""
        val newText = "hello"
        val hunks = diff(oldText, newText)

        assertEquals(1, hunks.size)
        assertEquals(DiffType.INSERT, hunks[0].type)
    }

    @Test
    fun remapRangeSameText() {
        val oldText = "hello world"
        val newText = "hello world"
        val hunks = diff(oldText, newText)

        val range = remapRange(hunks, 0, 5, oldText, newText)
        assertEquals(0 until 5, range)

        val range2 = remapRange(hunks, 6, 11, oldText, newText)
        assertEquals(6 until 11, range2)
    }
}
