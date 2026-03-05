package com.deedeedev.ytreader.ui.reader

import android.content.Context
import android.graphics.Typeface
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.widget.TextView

class SelectableHighlightTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextView(context, attrs, defStyleAttr) {

    var onSelectionChangedListener: ((start: Int, end: Int) -> Unit)? = null

    init {
        setTextIsSelectable(true)
        isClickable = true
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedListener?.invoke(selStart, selEnd)
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

        highlights.forEach { highlight ->
            if (contentLength == 0) return@forEach
            val start = highlight.start.coerceIn(0, contentLength)
            val end = highlight.end.coerceIn(0, contentLength)
            if (end <= start) return@forEach

            val spanColor = when (highlight.color) {
                HighlightColor.RED -> redColor
                HighlightColor.BLUE -> blueColor
                HighlightColor.GREEN -> greenColor
            }
            spannable.setSpan(
                BackgroundColorSpan(spanColor),
                start,
                end,
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
}
