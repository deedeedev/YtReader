package com.deedeedev.ytreader.ui.reader

import android.content.Context
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.domain.SubtitleSegment

internal enum class ReaderTapZone {
    PREVIOUS_PAGE,
    TOGGLE_UI,
    NEXT_PAGE
}

private const val CENTER_ZONE_START = 0.33f
private const val CENTER_ZONE_END = 0.67f
private const val BOOKMARK_CORNER_X_START = 0.88f
private const val BOOKMARK_CORNER_Y_END = 0.14f

internal fun isBookmarkCornerTap(tapPosition: ReaderTapPosition): Boolean {
    val x = tapPosition.xFraction.coerceIn(0f, 1f)
    val y = tapPosition.yFraction.coerceIn(0f, 1f)
    return x >= BOOKMARK_CORNER_X_START && y <= BOOKMARK_CORNER_Y_END
}

internal fun currentReaderText(
    readerMode: ReaderMode,
    isEditing: Boolean,
    editText: String,
    studyContent: String,
    originalModeText: String
): String {
    return when (readerMode) {
        ReaderMode.ORIGINAL -> originalModeText
        ReaderMode.STUDY -> if (isEditing) editText else studyContent
    }
}

internal fun applyReaderTextUpdate(
    updated: String,
    isEditing: Boolean,
    setEditText: (String) -> Unit,
    updateContent: (String) -> Unit
) {
    if (isEditing) {
        setEditText(updated)
    } else {
        updateContent(updated)
    }
}

internal fun executeReaderPendingAction(
    action: PendingAction,
    uiContent: String,
    persistReadingProgress: () -> Unit,
    onBack: () -> Unit,
    setIsEditing: (Boolean) -> Unit,
    setEditText: (String) -> Unit,
    clearStudySelection: () -> Unit,
    setReaderMode: (ReaderMode) -> Unit
) {
    when (action) {
        PendingAction.ExitScreen -> {
            persistReadingProgress()
            onBack()
        }

        PendingAction.ExitEditing -> {
            setIsEditing(false)
            setEditText(uiContent)
            clearStudySelection()
        }

        is PendingAction.SwitchMode -> {
            if (action.targetMode == ReaderMode.ORIGINAL) {
                setIsEditing(false)
                setEditText(uiContent)
            }
            setReaderMode(action.targetMode)
        }
    }
}

internal fun requestReaderAction(
    action: PendingAction,
    hasUnsavedChanges: Boolean,
    showUnsavedDialog: () -> Unit,
    runPendingAction: (PendingAction) -> Unit,
    setPendingAction: (PendingAction?) -> Unit
) {
    if (hasUnsavedChanges) {
        setPendingAction(action)
        showUnsavedDialog()
    } else {
        runPendingAction(action)
    }
}

internal fun executeReaderFindSearch(
    query: String,
    isCaseSensitive: Boolean,
    readerMode: ReaderMode,
    sourceText: String,
    originalSegments: List<SubtitleSegment>,
    originalFallbackText: String,
    setHasSearched: (Boolean) -> Unit,
    setErrorMessage: (String?) -> Unit,
    setFindResults: (List<ReaderFindResult>) -> Unit,
    setOriginalSegmentResults: (List<OriginalSegmentFindResult>) -> Unit
) {
    setHasSearched(true)
    val outcome = executeReaderFindSearch(
        query = query,
        isCaseSensitive = isCaseSensitive,
        isOriginalMode = readerMode == ReaderMode.ORIGINAL,
        sourceText = sourceText,
        originalSegments = originalSegments,
        originalFallbackText = originalFallbackText
    )
    setErrorMessage(outcome.errorMessage)
    setFindResults(outcome.findResults)
    setOriginalSegmentResults(outcome.originalSegmentFindResults)
}

internal suspend fun startReaderAiCleaningJob(
    viewModel: ReaderViewModel,
    sourceText: String,
    context: Context,
    showSnackbar: suspend (String) -> Unit
) {
    val result = viewModel.enqueueAiCleaning(sourceText)
    result.onSuccess {
        showSnackbar(context.getString(R.string.ai_cleaning_started_notification_hint))
    }.onFailure { error ->
        val message = error.message
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.ai_cleaning_failed)
        showSnackbar(message)
    }
}

internal fun formatOriginalModeCopyText(
    segments: List<SubtitleSegment>,
    showTimestamps: Boolean,
    fallbackText: String
): String {
    if (segments.isEmpty()) {
        return fallbackText
    }
    return segments.joinToString("\n\n") { segment ->
        if (showTimestamps) {
            "[${formatTime(segment.startTime)}] ${segment.text}"
        } else {
            segment.text
        }
    }
}

internal fun classifyReaderTapZone(tapPosition: ReaderTapPosition): ReaderTapZone {
    val x = tapPosition.xFraction.coerceIn(0f, 1f)
    val y = tapPosition.yFraction.coerceIn(0f, 1f)
    val isInHorizontalCenter = x in CENTER_ZONE_START..CENTER_ZONE_END
    val isInVerticalCenter = y in CENTER_ZONE_START..CENTER_ZONE_END
    if (isInHorizontalCenter && isInVerticalCenter) {
        return ReaderTapZone.TOGGLE_UI
    }

    return when {
        x < CENTER_ZONE_START -> ReaderTapZone.PREVIOUS_PAGE
        x > CENTER_ZONE_END -> ReaderTapZone.NEXT_PAGE
        y < CENTER_ZONE_START -> ReaderTapZone.PREVIOUS_PAGE
        else -> ReaderTapZone.NEXT_PAGE
    }
}

internal fun targetScrollForPageStep(
    currentValue: Int,
    maxValue: Int,
    viewportHeightPx: Int,
    isForward: Boolean
): Int {
    val clampedCurrent = currentValue.coerceIn(0, maxValue.coerceAtLeast(0))
    if (viewportHeightPx <= 0) {
        return clampedCurrent
    }
    val delta = if (isForward) viewportHeightPx else -viewportHeightPx
    return (clampedCurrent + delta).coerceIn(0, maxValue.coerceAtLeast(0))
}

internal fun targetListIndexForPageStep(
    currentFirstVisibleItemIndex: Int,
    totalItems: Int,
    visibleItemsCount: Int,
    isForward: Boolean
): Int {
    if (totalItems <= 0) {
        return 0
    }

    val maxIndex = totalItems - 1
    val clampedCurrent = currentFirstVisibleItemIndex.coerceIn(0, maxIndex)
    val step = visibleItemsCount.coerceAtLeast(1)
    val target = if (isForward) {
        clampedCurrent + step
    } else {
        clampedCurrent - step
    }
    return target.coerceIn(0, maxIndex)
}
