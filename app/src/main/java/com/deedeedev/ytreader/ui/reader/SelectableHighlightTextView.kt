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

class SelectableHighlightTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextView(context, attrs, defStyleAttr) {

    var onSelectionChangedListener: ((start: Int, end: Int) -> Unit)? = null
    var onHighlightTappedListener: ((TextHighlight?) -> Unit)? = null
    var onSingleTapListener: (() -> Unit)? = null
    private var highlightsForHitTest: List<TextHighlight> = emptyList()
    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onSingleTapListener?.invoke()
                onHighlightTappedListener?.invoke(findHighlightAtTouch(e))
                return false
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
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
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
