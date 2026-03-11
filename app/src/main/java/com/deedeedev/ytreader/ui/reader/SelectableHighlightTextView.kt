package com.deedeedev.ytreader.ui.reader

import android.content.Context
import android.graphics.Typeface
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
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
    internal var onTextTapListener: ((TextTapOutcome) -> Unit)? = null
    private var highlightsForHitTest: List<TextHighlight> = emptyList()
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

    fun setContentWithHighlights(
        content: String,
        highlights: List<TextHighlight>,
        redColor: Int,
        blueColor: Int,
        greenColor: Int
    ) {
        val spannable = SpannableString(content)
        val contentLength = content.length
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
            }
            spannable.setSpan(
                BackgroundColorSpan(spanColor),
                highlight.start,
                highlight.end,
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

    fun hasActiveSelection(): Boolean {
        return selectionStart >= 0 && selectionEnd > selectionStart
    }

    private fun dispatchTextTap(event: MotionEvent) {
        val tappedHighlight = findHighlightAtTouch(event)
        if (tappedHighlight != null) {
            onHighlightTappedListener?.invoke(tappedHighlight)
            return
        }

        val tapOutcome = if (hadActiveSelectionOnDown && !hasActiveSelection()) {
            TextTapOutcome.DISMISSED_SELECTION
        } else {
            TextTapOutcome.PLAIN_TEXT
        }
        onTextTapListener?.invoke(tapOutcome)
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
}
