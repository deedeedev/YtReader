package com.deedeedev.ytreader.ui.reader

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY
import android.text.Layout.HYPHENATION_FREQUENCY_NORMAL
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.TextView

internal enum class TextTapOutcome {
    DISMISSED_SELECTION,
    PLAIN_TEXT
}

class SelectableHighlightTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextView(context, attrs, defStyleAttr) {

    var onSelectionChangedListener: ((start: Int, end: Int) -> Unit)? = null
    var onHighlightTappedListener: ((TextHighlight?) -> Unit)? = null
    internal var onTextTapListener: ((TextTapOutcome, ReaderTapPosition) -> Unit)? = null
    private var highlightsForHitTest: List<TextHighlight> = emptyList()
    private var searchResultRange: SelectionRange? = null
    private var hadActiveSelectionOnDown = false
    private var shouldDispatchSingleTap = false
    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                shouldDispatchSingleTap = true
                return true
            }
        }
    )

    init {
        setTextIsSelectable(true)
        isClickable = true
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedListener?.invoke(selStart, selEnd)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            hadActiveSelectionOnDown = hasActiveSelection()
        }

        gestureDetector.onTouchEvent(event)
        val handled = super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                if (shouldDispatchSingleTap) {
                    dispatchTextTap(event)
                }
                resetTapTracking()
            }
            MotionEvent.ACTION_CANCEL -> resetTapTracking()
        }

        return handled
    }

    internal fun setContentWithHighlights(
        content: String,
        highlights: List<TextHighlight>,
        searchResultRange: SelectionRange?,
        redColor: Int,
        blueColor: Int,
        greenColor: Int,
        yellowColor: Int,
        searchResultColor: Int
    ) {
        val spannable = SpannableString(content)
        val contentLength = content.length
        this.searchResultRange = searchResultRange?.let { range ->
            val start = range.start.coerceIn(0, contentLength)
            val end = range.end.coerceIn(0, contentLength)
            if (end <= start) null else SelectionRange(start = start, end = end)
        }
        highlightsForHitTest = highlights
            .mapNotNull { highlight ->
                val start = highlight.start.coerceIn(0, contentLength)
                val end = highlight.end.coerceIn(0, contentLength)
                if (end <= start) {
                    null
                } else {
                    highlight.copy(start = start, end = end)
                }
            }

        highlightsForHitTest.forEach { highlight ->
            if (contentLength == 0) return@forEach

            val spanColor = when (highlight.color) {
                HighlightColor.RED -> redColor
                HighlightColor.BLUE -> blueColor
                HighlightColor.GREEN -> greenColor
                HighlightColor.YELLOW -> yellowColor
            }
            spannable.setSpan(
                BackgroundColorSpan(spanColor),
                highlight.start,
                highlight.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        this.searchResultRange?.let { range ->
            spannable.setSpan(
                BackgroundColorSpan(searchResultColor),
                range.start,
                range.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        text = spannable
    }

    fun clearSelection() {
        (text as? Spannable)?.let { spannable ->
            Selection.removeSelection(spannable)
        }
        onSelectionChangedListener?.invoke(0, 0)
    }

    fun setSelectionRange(start: Int, end: Int) {
        val spannable = text as? Spannable ?: return
        val textLength = spannable.length
        val normalizedStart = start.coerceIn(0, textLength)
        val normalizedEnd = end.coerceIn(0, textLength)
        Selection.setSelection(spannable, normalizedStart, normalizedEnd)
        onSelectionChangedListener?.invoke(normalizedStart, normalizedEnd)
    }

    fun verticalOffsetForSelection(start: Int): Int {
        val textLayout = layout ?: return 0
        val textLength = text.length
        if (textLength == 0) return 0
        val safeStart = start.coerceIn(0, textLength - 1)
        val line = textLayout.getLineForOffset(safeStart)
        return totalPaddingTop + textLayout.getLineTop(line)
    }

    fun selectedText(): String? {
        val spannable = text as? Spannable ?: return null
        val start = selectionStart
        val end = selectionEnd
        if (start < 0 || end <= start) return null
        return spannable.subSequence(start, end).toString()
    }

    fun applyTypeface(fontFamilyName: String) {
        typeface = when (fontFamilyName) {
            "Serif" -> Typeface.SERIF
            "SansSerif" -> Typeface.SANS_SERIF
            "Monospace" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
    }

    fun setReadableColors(textColor: Int, backgroundColor: Int) {
        setTextColor(textColor)
        setBackgroundColor(backgroundColor)
    }

    fun setJustificationEnabled(enabled: Boolean) {
        justificationMode = if (enabled) {
            Layout.JUSTIFICATION_MODE_INTER_WORD
        } else {
            Layout.JUSTIFICATION_MODE_NONE
        }
        breakStrategy = if (enabled) BREAK_STRATEGY_HIGH_QUALITY else breakStrategy
        hyphenationFrequency = if (enabled) HYPHENATION_FREQUENCY_NORMAL else hyphenationFrequency
        gravity = if (enabled) Gravity.START else Gravity.NO_GRAVITY
        textAlignment = TEXT_ALIGNMENT_VIEW_START
    }

    fun hasActiveSelection(): Boolean {
        return selectionStart >= 0 && selectionEnd > selectionStart
    }

    private fun dispatchTextTap(event: MotionEvent) {
        val tappedHighlight = findHighlightAtTouch(event)
        if (tappedHighlight != null) {
            onHighlightTappedListener?.invoke(tappedHighlight)
            return
        }

        val tapPosition = tapPosition(event)

        val tapOutcome = if (hadActiveSelectionOnDown && !hasActiveSelection()) {
            TextTapOutcome.DISMISSED_SELECTION
        } else {
            TextTapOutcome.PLAIN_TEXT
        }
        onTextTapListener?.invoke(tapOutcome, tapPosition)
        if (tapOutcome == TextTapOutcome.PLAIN_TEXT) {
            onHighlightTappedListener?.invoke(null)
        }
    }

    private fun resetTapTracking() {
        hadActiveSelectionOnDown = false
        shouldDispatchSingleTap = false
    }

    private fun findHighlightAtTouch(event: MotionEvent): TextHighlight? {
        val textLayout = layout ?: return null
        val localX = (event.x - totalPaddingLeft + scrollX)
            .coerceIn(0f, (textLayout.width - 1).coerceAtLeast(0).toFloat())
        val localY = (event.y - totalPaddingTop + scrollY)
            .coerceIn(0f, (textLayout.height - 1).coerceAtLeast(0).toFloat())
        val line = textLayout.getLineForVertical(localY.toInt())
        val offset = textLayout.getOffsetForHorizontal(line, localX)
        return findHighlightAtOffset(highlightsForHitTest, offset)
    }

    private fun tapPosition(event: MotionEvent): ReaderTapPosition {
        val visibleRect = Rect()
        val hasVisibleRect = getGlobalVisibleRect(visibleRect)
        if (hasVisibleRect && visibleRect.width() > 0 && visibleRect.height() > 0) {
            return ReaderTapPosition(
                xFraction = ((event.rawX - visibleRect.left) / visibleRect.width().toFloat())
                    .coerceIn(0f, 1f),
                yFraction = ((event.rawY - visibleRect.top) / visibleRect.height().toFloat())
                    .coerceIn(0f, 1f)
            )
        }

        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        return ReaderTapPosition(
            xFraction = (event.x / safeWidth.toFloat()).coerceIn(0f, 1f),
            yFraction = (event.y / safeHeight.toFloat()).coerceIn(0f, 1f)
        )
    }
}
