package com.deedeedev.ytreader.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

class JustifiedStudyTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onSelectionChangedListener: ((start: Int, end: Int) -> Unit)? = null
    var onHighlightTappedListener: ((TextHighlight?) -> Unit)? = null
    internal var onTextTapListener: ((TextTapOutcome, ReaderTapPosition) -> Unit)? = null

    private val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = spToPx(16f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(51, 181, 229)
        style = Paint.Style.FILL
    }

    private var content: String = ""
    private var highlights: List<TextHighlight> = emptyList()
    private var redColor: Int = 0
    private var blueColor: Int = 0
    private var greenColor: Int = 0
    private var yellowColor: Int = 0
    private var backgroundColorInt: Int = Color.TRANSPARENT
    private var lineSpacingMultiplier: Float = 1f
    private var layout: StaticLayout? = null
    private var displayText: CharSequence = ""
    private var selectionRange: TextRange? = null
    private var selectionAnchor: TextRange? = null
    private var isSelecting = false
    private var activeHandle: SelectionHandle? = null
    private var hadActiveSelectionOnDown = false
    private val selectionColor = Color.argb(90, 51, 181, 229)
    private val handleRadius = dpToPx(8f)
    private val handleStemWidth = dpToPx(2f)
    private val handleStemHeight = dpToPx(14f)
    private val handleHitRadius = dpToPx(22f)

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                dispatchTap(e)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                beginSelection(e)
            }
        }
    )

    init {
        isClickable = true
        isLongClickable = true
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val resolvedWidth = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        rebuildLayout((resolvedWidth - paddingLeft - paddingRight).coerceAtLeast(0))
        val contentHeight = (layout?.height ?: 0) + paddingTop + paddingBottom + extraHandleBottomInset()
        val resolvedHeight = resolveSize(contentHeight.toInt(), heightMeasureSpec)
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (backgroundColorInt != Color.TRANSPARENT) {
            canvas.drawColor(backgroundColorInt)
        }
        val textLayout = layout ?: return
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        textLayout.draw(canvas)
        selectionHandleVisuals(textLayout).forEach { handle ->
            drawHandle(canvas, handle)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            hadActiveSelectionOnDown = hasActiveSelection()
            hitHandleAt(event)?.let { handle ->
                activeHandle = handle
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
        }

        if (activeHandle == null) {
            gestureDetector.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                when {
                    activeHandle != null -> {
                        updateSelectionFromHandle(event)
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    isSelecting -> {
                        updateSelection(event)
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                when {
                    activeHandle != null -> {
                        updateSelectionFromHandle(event)
                        finishHandleDrag()
                    }
                    isSelecting -> {
                        updateSelection(event)
                        finishSelection()
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (activeHandle != null) {
                    finishHandleDrag()
                }
                if (isSelecting) {
                    finishSelection()
                }
            }
        }

        return true
    }

    fun setContentWithHighlights(
        content: String,
        highlights: List<TextHighlight>,
        redColor: Int,
        blueColor: Int,
        greenColor: Int,
        yellowColor: Int
    ) {
        this.content = content
        this.highlights = highlights
            .mapNotNull { highlight ->
                val start = highlight.start.coerceIn(0, content.length)
                val end = highlight.end.coerceIn(0, content.length)
                if (end <= start) null else highlight.copy(start = start, end = end)
            }
        this.redColor = redColor
        this.blueColor = blueColor
        this.greenColor = greenColor
        this.yellowColor = yellowColor
        selectionRange = selectionRange?.let { range ->
            val start = range.start.coerceIn(0, content.length)
            val end = range.end.coerceIn(0, content.length)
            if (end > start) TextRange(start, end) else null
        }
        rebuildDisplayText()
    }

    fun clearSelection() {
        selectionRange = null
        selectionAnchor = null
        activeHandle = null
        rebuildDisplayText()
        onSelectionChangedListener?.invoke(0, 0)
    }

    fun setSelectionRange(start: Int, end: Int) {
        if (content.isEmpty()) {
            clearSelection()
            return
        }
        val boundedStart = start.coerceIn(0, content.length)
        val boundedEnd = end.coerceIn(0, content.length)
        val normalizedStart = minOf(boundedStart, boundedEnd)
        val normalizedEnd = maxOf(boundedStart, boundedEnd)
        if (normalizedEnd <= normalizedStart) {
            clearSelection()
            return
        }
        selectionAnchor = null
        activeHandle = null
        updateSelectionRange(TextRange(start = normalizedStart, end = normalizedEnd))
    }

    fun verticalOffsetForSelection(start: Int): Int {
        val textLayout = layout ?: return 0
        if (content.isEmpty()) return 0
        val safeStart = start.coerceIn(0, content.length - 1)
        val line = textLayout.getLineForOffset(safeStart)
        return paddingTop + textLayout.getLineTop(line)
    }

    fun selectedText(): String? {
        val range = selectionRange ?: return null
        if (range.end <= range.start) return null
        return content.substring(range.start, range.end)
    }

    fun applyTypeface(fontFamilyName: String) {
        textPaint.typeface = when (fontFamilyName) {
            "Serif" -> Typeface.SERIF
            "SansSerif" -> Typeface.SANS_SERIF
            "Monospace" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
        rebuildDisplayText()
    }

    fun setLineHeightMultiplier(multiplier: Float) {
        lineSpacingMultiplier = multiplier
        requestLayout()
        invalidate()
    }

    fun setTextSizeSp(size: Float) {
        textPaint.textSize = spToPx(size)
        rebuildDisplayText()
    }

    fun setReadableColors(textColor: Int, backgroundColor: Int) {
        textPaint.color = textColor
        backgroundColorInt = backgroundColor
        invalidate()
    }

    fun hasActiveSelection(): Boolean {
        val range = selectionRange
        return range != null && range.end > range.start
    }

    private fun beginSelection(event: MotionEvent) {
        val offset = offsetAt(event) ?: return
        val anchorRange = findTokenRangeAtOffset(content, offset) ?: return
        isSelecting = true
        activeHandle = null
        selectionAnchor = anchorRange
        updateSelectionRange(anchorRange)
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun updateSelection(event: MotionEvent) {
        val anchor = selectionAnchor ?: return
        val offset = offsetAt(event) ?: return
        val targetRange = findTokenRangeAtOffset(content, offset) ?: return
        updateSelectionRange(mergeSelectionRange(anchor, targetRange))
    }

    private fun updateSelectionFromHandle(event: MotionEvent) {
        val handle = activeHandle ?: return
        val currentRange = selectionRange ?: return
        val offset = offsetAt(event) ?: return
        val targetRange = findTokenRangeAtOffset(content, offset) ?: return
        val updated = updateSelectionForHandleDrag(currentRange, targetRange, handle)
        activeHandle = updated.activeHandle
        updateSelectionRange(updated.range)
    }

    private fun finishSelection() {
        isSelecting = false
        selectionAnchor = null
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun finishHandleDrag() {
        activeHandle = null
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun dispatchTap(event: MotionEvent) {
        val offset = offsetAt(event)
        val tappedHighlight = offset?.let { findHighlightAtOffset(highlights, it) }
        if (tappedHighlight != null) {
            onHighlightTappedListener?.invoke(tappedHighlight)
            return
        }

        val tapPosition = tapPosition(event)

        val tapOutcome = if (hadActiveSelectionOnDown && hasActiveSelection()) {
            clearSelection()
            TextTapOutcome.DISMISSED_SELECTION
        } else {
            TextTapOutcome.PLAIN_TEXT
        }
        onTextTapListener?.invoke(tapOutcome, tapPosition)
        if (tapOutcome == TextTapOutcome.PLAIN_TEXT) {
            onHighlightTappedListener?.invoke(null)
        }
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

    private fun updateSelectionRange(range: TextRange) {
        if (range.end <= range.start) {
            clearSelection()
            return
        }
        selectionRange = range
        rebuildDisplayText()
        onSelectionChangedListener?.invoke(range.start, range.end)
    }

    private fun rebuildDisplayText() {
        val spannable = SpannableString(content)
        highlights.forEach { highlight ->
            spannable.setSpan(
                BackgroundColorSpan(
                    when (highlight.color) {
                        HighlightColor.RED -> redColor
                        HighlightColor.BLUE -> blueColor
                        HighlightColor.GREEN -> greenColor
                        HighlightColor.YELLOW -> yellowColor
                    }
                ),
                highlight.start,
                highlight.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        selectionRange?.let { range ->
            spannable.setSpan(
                BackgroundColorSpan(selectionColor),
                range.start,
                range.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            Selection.setSelection(spannable, range.start, range.end)
        }
        displayText = spannable
        requestLayout()
        invalidate()
    }

    private fun rebuildLayout(layoutWidth: Int = (measuredWidth - paddingLeft - paddingRight).coerceAtLeast(0)) {
        val width = layoutWidth.coerceAtLeast(1)
        layout = StaticLayout.Builder
            .obtain(displayText, 0, displayText.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
            .build()
    }

    private fun drawHandle(canvas: Canvas, handle: SelectionHandleVisual) {
        canvas.drawRect(
            handle.centerX - (handleStemWidth / 2f),
            handle.anchorY,
            handle.centerX + (handleStemWidth / 2f),
            handle.anchorY + handleStemHeight,
            handlePaint
        )
        canvas.drawCircle(
            handle.centerX,
            handle.anchorY + handleStemHeight + handleRadius,
            handleRadius,
            handlePaint
        )
    }

    private fun hitHandleAt(event: MotionEvent): SelectionHandle? {
        val textLayout = layout ?: return null
        val localX = event.x - paddingLeft
        val localY = event.y - paddingTop
        return selectionHandleVisuals(textLayout)
            .firstOrNull { handle ->
                val circleCenterY = handle.anchorY + handleStemHeight + handleRadius
                val dx = localX - handle.centerX
                val dy = localY - circleCenterY
                val stemHit = localX in (handle.centerX - handleHitRadius)..(handle.centerX + handleHitRadius) &&
                    localY in handle.anchorY..(circleCenterY + handleHitRadius)
                stemHit || (dx * dx + dy * dy) <= (handleHitRadius * handleHitRadius)
            }
            ?.handle
    }

    private fun selectionHandleVisuals(textLayout: StaticLayout): List<SelectionHandleVisual> {
        val range = selectionRange ?: return emptyList()
        if (content.isEmpty()) return emptyList()
        return listOfNotNull(
            handleVisualForOffset(range.start, SelectionHandle.START, isRangeEnd = false, textLayout = textLayout),
            handleVisualForOffset(range.end, SelectionHandle.END, isRangeEnd = true, textLayout = textLayout)
        )
    }

    private fun handleVisualForOffset(
        offset: Int,
        handle: SelectionHandle,
        isRangeEnd: Boolean,
        textLayout: StaticLayout
    ): SelectionHandleVisual? {
        if (content.isEmpty()) return null
        val lineIndex = when {
            content.length == 1 -> 0
            isRangeEnd && offset >= content.length -> textLayout.getLineForOffset(content.length - 1)
            else -> textLayout.getLineForOffset(offset.coerceIn(0, content.length - 1))
        }
        val horizontal = textLayout.getPrimaryHorizontal(offset.coerceIn(0, content.length))
        return SelectionHandleVisual(
            handle = handle,
            centerX = horizontal,
            anchorY = textLayout.getLineBottom(lineIndex).toFloat()
        )
    }

    private fun offsetAt(event: MotionEvent): Int? {
        val textLayout = layout ?: return null
        if (content.isEmpty()) return null
        val localX = (event.x - paddingLeft)
            .coerceIn(0f, (textLayout.width - 1).coerceAtLeast(0).toFloat())
        val localY = (event.y - paddingTop)
            .coerceIn(0f, (textLayout.height - 1).coerceAtLeast(0).toFloat())
        val line = textLayout.getLineForVertical(localY.toInt())
        return textLayout.getOffsetForHorizontal(line, localX)
            .coerceIn(0, content.length - 1)
    }

    private fun extraHandleBottomInset(): Float {
        return if (selectionRange == null) 0f else handleStemHeight + (handleRadius * 2f)
    }

    private fun dpToPx(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )
    }

    private fun spToPx(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value,
            resources.displayMetrics
        )
    }

    private data class SelectionHandleVisual(
        val handle: SelectionHandle,
        val centerX: Float,
        val anchorY: Float
    )
}
