package com.deedeedev.ytreader.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.deedeedev.ytreader.data.local.BookmarkEntity

class JustifiedStudyTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onSelectionChangedListener: ((start: Int, end: Int) -> Unit)? = null
    var onHighlightTappedListener: ((TextHighlight?) -> Unit)? = null
    var onBookmarkTappedListener: ((BookmarkEntity) -> Unit)? = null
    internal var onTextTapListener: ((TextTapOutcome, ReaderTapPosition) -> Unit)? = null

    private val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = spToPx(16f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(51, 181, 229)
        style = Paint.Style.FILL
    }
    private val noteIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 196, 48, 43)
        style = Paint.Style.FILL
    }
    private val noteIndicatorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
    }

    private var content: String = ""
    private var highlights: List<TextHighlight> = emptyList()
    private var bookmarks: List<BookmarkEntity> = emptyList()
    private var redColor: Int = 0
    private var blueColor: Int = 0
    private var greenColor: Int = 0
    private var yellowColor: Int = 0
    private var searchResultColor: Int = 0
    private var backgroundColorInt: Int = Color.TRANSPARENT
    private var lineSpacingMultiplier: Float = 1f
    private var layout: StaticLayout? = null
    private val visibleRect = Rect()
    private var cachedLayoutWidth = -1
    private var layoutDirty = true
    private var searchResultRange: TextRange? = null
    private val selectionColor = Color.argb(90, 51, 181, 229)
    private val handleRadius = dpToPx(8f)
    private val handleStemWidth = dpToPx(2f)
    private val handleStemHeight = dpToPx(14f)
    private val handleHitRadius = dpToPx(22f)

    private val selectionController = StudyTextSelectionController()
    private val renderer = StudyTextRenderer(textPaint)
    private val bookmarkRenderer = StudyTextBookmarkRenderer()

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
        val availableWidth = (resolvedWidth - paddingLeft - paddingRight).coerceAtLeast(0)
        if (availableWidth != cachedLayoutWidth || layoutDirty) {
            rebuildLayout(availableWidth)
            cachedLayoutWidth = availableWidth
            layoutDirty = false
        }
        val contentHeight = (layout?.height ?: 0) + paddingTop + paddingBottom + selectionController.extraHandleBottomInset(handleStemHeight, handleRadius)
        val resolvedHeight = resolveSize(contentHeight.toInt(), heightMeasureSpec)
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (backgroundColorInt != Color.TRANSPARENT) {
            canvas.drawColor(backgroundColorInt)
        }
        val textLayout = layout ?: return
        getLocalVisibleRect(visibleRect)
        val visTop = visibleRect.top - paddingTop
        val visBottom = visibleRect.bottom - paddingTop
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

        renderer.drawHighlightBackgrounds(canvas, textLayout, content, highlights, redColor, blueColor, greenColor, yellowColor, visTop, visBottom)
        renderer.drawSearchResultBackground(canvas, textLayout, content, searchResultRange, searchResultColor, visTop, visBottom)
        renderer.drawSelectionBackground(canvas, textLayout, content, selectionController.selectionRange, selectionColor, visTop, visBottom)

        textLayout.draw(canvas)

        bookmarkRenderer.drawBookmarkIndicators(canvas, textLayout, content, bookmarks, noteIndicatorPaint, noteIndicatorStrokePaint, visTop, visBottom) { dpToPx(it) }
        renderer.drawHighlightNoteIndicators(canvas, textLayout, content, highlights, noteIndicatorPaint, noteIndicatorStrokePaint, visTop, visBottom) { dpToPx(it) }
        selectionController.selectionHandleVisuals(textLayout, content, handleStemHeight, handleRadius).forEach { handle ->
            renderer.drawHandle(canvas, handle, handlePaint, handleStemWidth, handleStemHeight, handleRadius)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            selectionController.hadActiveSelectionOnDown = hasActiveSelection()
            val localX = event.x - paddingLeft
            val localY = event.y - paddingTop
            val textLayout = layout
            if (textLayout != null) {
                val hit = selectionController.hitHandleAt(localX, localY, textLayout, content, handleHitRadius, handleStemHeight, handleRadius) { dpToPx(it) }
                if (hit != null) {
                    selectionController.activeHandle = hit
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }

        if (selectionController.activeHandle == null) {
            gestureDetector.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                when {
                    selectionController.activeHandle != null -> {
                        handleSelectionFromTouch(event)
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    selectionController.isSelecting -> {
                        handleSelectionMoveFromTouch(event)
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                when {
                    selectionController.activeHandle != null -> {
                        handleSelectionFromTouch(event)
                        selectionController.finishHandleDrag { parent?.requestDisallowInterceptTouchEvent(it) }
                    }
                    selectionController.isSelecting -> {
                        handleSelectionMoveFromTouch(event)
                        selectionController.finishSelection { parent?.requestDisallowInterceptTouchEvent(it) }
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (selectionController.activeHandle != null) {
                    selectionController.finishHandleDrag { parent?.requestDisallowInterceptTouchEvent(it) }
                }
                if (selectionController.isSelecting) {
                    selectionController.finishSelection { parent?.requestDisallowInterceptTouchEvent(it) }
                }
            }
        }

        return true
    }

    internal fun setContentWithHighlights(
        content: String,
        highlights: List<TextHighlight>,
        bookmarks: List<BookmarkEntity>,
        searchResultRange: SelectionRange?,
        redColor: Int,
        blueColor: Int,
        greenColor: Int,
        yellowColor: Int,
        searchResultColor: Int
    ) {
        val contentChanged = this.content != content
        val newHighlights = highlights
            .mapNotNull { highlight ->
                val start = highlight.start.coerceIn(0, content.length)
                val end = highlight.end.coerceIn(0, content.length)
                if (end <= start) null else highlight.copy(start = start, end = end)
            }
        val newBookmarks = bookmarks.mapNotNull { bookmark ->
            val boundedAnchor = bookmark.anchorStart.coerceIn(0, content.length)
            if (content.isEmpty() || boundedAnchor >= content.length) {
                null
            } else {
                bookmark.copy(anchorStart = boundedAnchor)
            }
        }
        val newSearchResultRange = searchResultRange?.let { range ->
            val start = range.start.coerceIn(0, content.length)
            val end = range.end.coerceIn(0, content.length)
            if (end <= start) null else TextRange(start, end)
        }
        val highlightsChanged = this.highlights != newHighlights
        val bookmarksChanged = this.bookmarks != newBookmarks
        val colorsChanged = this.redColor != redColor || this.blueColor != blueColor || this.greenColor != greenColor || this.yellowColor != yellowColor || this.searchResultColor != searchResultColor
        val searchChanged = this.searchResultRange != newSearchResultRange
        val needsInvalidate = contentChanged || highlightsChanged || bookmarksChanged || colorsChanged || searchChanged

        this.content = content
        this.highlights = newHighlights
        this.bookmarks = newBookmarks
        this.redColor = redColor
        this.blueColor = blueColor
        this.greenColor = greenColor
        this.yellowColor = yellowColor
        this.searchResultColor = searchResultColor
        this.searchResultRange = newSearchResultRange
        selectionController.adjustForContentChange(content)
        bookmarkRenderer.invalidateBookmarkBoundsCache()
        if (contentChanged) {
            layoutDirty = true
            requestLayout()
        }
        if (needsInvalidate) {
            invalidate()
        }
    }

    fun clearSelection() {
        selectionController.clearSelection { start, end ->
            invalidate()
            onSelectionChangedListener?.invoke(start, end)
        }
    }

    fun setSelectionRange(start: Int, end: Int) {
        selectionController.setSelectionRange(start, end, content) { _, _ ->
            invalidate()
        }
    }

    fun verticalOffsetForSelection(start: Int): Int {
        val textLayout = layout ?: return 0
        if (content.isEmpty()) return 0
        val safeStart = start.coerceIn(0, content.length - 1)
        val line = textLayout.getLineForOffset(safeStart)
        return paddingTop + textLayout.getLineTop(line)
    }

    fun verticalOffsetForBookmark(anchorStart: Int): Int {
        return verticalOffsetForSelection(anchorStart)
    }

    fun topVisibleLineAnchor(scrollOffset: Int): Int? {
        val textLayout = layout ?: return null
        if (content.isEmpty()) return null
        val line = textLayout.getLineForVertical(scrollOffset.coerceAtLeast(0))
        return textLayout.getLineStart(line).coerceIn(0, content.lastIndex)
    }

    fun lineTextForOffset(offset: Int): String {
        val textLayout = layout ?: return ""
        if (content.isEmpty()) return ""
        val safeOffset = offset.coerceIn(0, content.lastIndex)
        val line = textLayout.getLineForOffset(safeOffset)
        val start = textLayout.getLineStart(line).coerceIn(0, content.length)
        val end = textLayout.getLineEnd(line).coerceIn(start, content.length)
        return content.substring(start, end)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun selectedText(): String? {
        return selectionController.selectedText(content)
    }

    fun applyTypeface(fontFamilyName: String) {
        val newTypeface = when (fontFamilyName) {
            "Serif" -> Typeface.SERIF
            "SansSerif" -> Typeface.SANS_SERIF
            "Monospace" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
        if (textPaint.typeface == newTypeface) return
        textPaint.typeface = newTypeface
        layoutDirty = true
        requestLayout()
        invalidate()
    }

    fun setLineHeightMultiplier(multiplier: Float) {
        if (lineSpacingMultiplier == multiplier) return
        lineSpacingMultiplier = multiplier
        layoutDirty = true
        requestLayout()
        invalidate()
    }

    fun setTextSizeSp(size: Float) {
        val newSize = spToPx(size)
        if (textPaint.textSize == newSize) return
        textPaint.textSize = newSize
        layoutDirty = true
        requestLayout()
        invalidate()
    }

    fun setReadableColors(textColor: Int, backgroundColor: Int) {
        textPaint.color = textColor
        backgroundColorInt = backgroundColor
        invalidate()
    }

    fun setScrolling(isScrolling: Boolean) {
        setLayerType(
            if (isScrolling) LAYER_TYPE_HARDWARE else LAYER_TYPE_NONE,
            null
        )
    }

    fun hasActiveSelection(): Boolean {
        return selectionController.hasActiveSelection()
    }

    private fun beginSelection(event: MotionEvent) {
        val offset = offsetAt(event) ?: return
        selectionController.beginSelection(content, offset, { parent?.requestDisallowInterceptTouchEvent(it) }, { start, end ->
            invalidate()
            onSelectionChangedListener?.invoke(start, end)
        })
    }

    private fun handleSelectionMoveFromTouch(event: MotionEvent) {
        val offset = offsetAt(event) ?: return
        selectionController.updateSelection(content, offset, { parent?.requestDisallowInterceptTouchEvent(it) }, { start, end ->
            invalidate()
            onSelectionChangedListener?.invoke(start, end)
        })
    }

    private fun handleSelectionFromTouch(event: MotionEvent) {
        val offset = offsetAt(event) ?: return
        selectionController.updateSelectionFromHandle(content, offset) { start, end ->
            invalidate()
            onSelectionChangedListener?.invoke(start, end)
        }
    }

    private fun dispatchTap(event: MotionEvent) {
        val textLayout = layout ?: return
        val localX = event.x - paddingLeft
        val localY = event.y - paddingTop
        val tappedBookmark = bookmarkRenderer.findBookmarkAt(localX, localY, textLayout, content, bookmarks) { dpToPx(it) }
        if (tappedBookmark != null) {
            onBookmarkTappedListener?.invoke(tappedBookmark)
            return
        }

        val offset = offsetAt(event)
        val tappedHighlight = offset?.let { findHighlightAtOffset(highlights, it) }
        if (tappedHighlight != null) {
            onHighlightTappedListener?.invoke(tappedHighlight)
            return
        }

        val tapPosition = tapPosition(event)

        val tapOutcome = if (selectionController.hadActiveSelectionOnDown && hasActiveSelection()) {
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

    private fun rebuildLayout(layoutWidth: Int = (measuredWidth - paddingLeft - paddingRight).coerceAtLeast(0)) {
        val width = layoutWidth.coerceAtLeast(1)
        layout = StaticLayout.Builder
            .obtain(content, 0, content.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
            .build()
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
}
