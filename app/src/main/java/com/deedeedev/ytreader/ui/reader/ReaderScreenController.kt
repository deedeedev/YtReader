package com.deedeedev.ytreader.ui.reader

import android.content.Context
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.domain.SubtitleSegment

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
