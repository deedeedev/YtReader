package com.deedeedev.ytreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudySelectionUtilsTest {

    @Test
    fun `findTokenRangeAtOffset returns contiguous token`() {
        val range = findTokenRangeAtOffset("one two", 5)

        assertEquals(TextRange(start = 4, end = 7), range)
    }

    @Test
    fun `findTokenRangeAtOffset returns null for whitespace`() {
        val range = findTokenRangeAtOffset("one two", 3)

        assertNull(range)
    }

    @Test
    fun `mergeSelectionRange spans both tokens and intervening whitespace`() {
        val merged = mergeSelectionRange(
            anchor = TextRange(start = 4, end = 7),
            target = TextRange(start = 0, end = 3)
        )

        assertEquals(TextRange(start = 0, end = 7), merged)
    }

    @Test
    fun `updateSelectionForHandleDrag keeps start handle when dragged left`() {
        val updated = updateSelectionForHandleDrag(
            current = TextRange(start = 4, end = 11),
            target = TextRange(start = 0, end = 3),
            handle = SelectionHandle.START
        )

        assertEquals(TextRange(start = 0, end = 11), updated.range)
        assertEquals(SelectionHandle.START, updated.activeHandle)
    }

    @Test
    fun `updateSelectionForHandleDrag swaps to end handle when start crosses end`() {
        val updated = updateSelectionForHandleDrag(
            current = TextRange(start = 0, end = 7),
            target = TextRange(start = 8, end = 13),
            handle = SelectionHandle.START
        )

        assertEquals(TextRange(start = 7, end = 13), updated.range)
        assertEquals(SelectionHandle.END, updated.activeHandle)
    }

    @Test
    fun `updateSelectionForHandleDrag swaps to start handle when end crosses start`() {
        val updated = updateSelectionForHandleDrag(
            current = TextRange(start = 8, end = 13),
            target = TextRange(start = 0, end = 7),
            handle = SelectionHandle.END
        )

        assertEquals(TextRange(start = 0, end = 8), updated.range)
        assertEquals(SelectionHandle.START, updated.activeHandle)
    }
}
