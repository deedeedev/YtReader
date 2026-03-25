package com.deedeedev.ytreader.ui.reader

import android.app.Activity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.deedeedev.ytreader.domain.SubtitleSegment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun ReaderCoreEffects(
    subtitleId: Long,
    subtitleLastStudyScroll: Int,
    subtitleLastTimestamp: Long,
    hasInitialNavigationTarget: Boolean,
    uiContent: String,
    pendingAiCleanedText: String?,
    aiCleaningErrorLog: String?,
    isEditing: Boolean,
    readerMode: ReaderMode,
    studyScrollState: ScrollState,
    originalFallbackScrollState: ScrollState,
    originalListState: LazyListState,
    originalSegments: List<SubtitleSegment>,
    studyTextView: JustifiedStudyTextView?,
    pendingFindSelection: PendingFindSelection?,
    onEditTextSync: (String) -> Unit,
    clearSelectionState: () -> Unit,
    onResetAiDialogs: () -> Unit,
    setLastKnownStudyScroll: (Int) -> Unit,
    setHasRestoredStudyScroll: (Boolean) -> Unit,
    hasRestoredStudyScroll: Boolean,
    persistReadingProgress: () -> Unit,
    activity: Activity?,
    onEnterEditing: () -> Unit,
    onShowAiPreviewDialog: () -> Unit,
    onShowAiErrorDialog: () -> Unit,
    onReaderModeChangedToOriginal: () -> Unit,
    onReaderModeChangedToStudy: () -> Unit,
    clearPendingFindSelection: () -> Unit,
    onOriginalTimestampVisible: (Long) -> Unit,
    onSelectStudyFindMatch: suspend (PendingFindSelection.Study) -> Unit,
    onSelectOriginalFallbackFindMatch: suspend (PendingFindSelection.OriginalFallback) -> Unit,
    onSelectOriginalSegmentFindMatch: suspend (PendingFindSelection.OriginalSegment) -> Unit
) {
    LaunchedEffect(uiContent, isEditing) {
        if (!isEditing) {
            onEditTextSync(uiContent)
        }
    }

    LaunchedEffect(uiContent) {
        clearSelectionState()
    }

    LaunchedEffect(subtitleId) {
        onResetAiDialogs()
    }

    LaunchedEffect(subtitleId) {
        setLastKnownStudyScroll(subtitleLastStudyScroll)
        setHasRestoredStudyScroll(false)
    }

    LaunchedEffect(subtitleId, studyScrollState.maxValue, hasRestoredStudyScroll) {
        if (hasRestoredStudyScroll) return@LaunchedEffect
        if (hasInitialNavigationTarget) {
            setHasRestoredStudyScroll(true)
            return@LaunchedEffect
        }
        val targetScroll = subtitleLastStudyScroll.coerceAtLeast(0)
        val maxValue = studyScrollState.maxValue
        if (targetScroll == 0 || maxValue > 0) {
            studyScrollState.scrollTo(targetScroll.coerceIn(0, maxValue))
            setHasRestoredStudyScroll(true)
        }
    }

    LaunchedEffect(studyScrollState, subtitleId) {
        snapshotFlow { studyScrollState.value }
            .distinctUntilChanged()
            .collectLatest { scroll ->
                setLastKnownStudyScroll(scroll)
            }
    }

    DisposableEffect(activity, subtitleId) {
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle
        if (lifecycle == null) {
            onDispose { persistReadingProgress() }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    persistReadingProgress()
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                persistReadingProgress()
            }
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            onEnterEditing()
        }
    }

    LaunchedEffect(pendingAiCleanedText) {
        if (!pendingAiCleanedText.isNullOrBlank()) {
            onShowAiPreviewDialog()
        }
    }

    LaunchedEffect(aiCleaningErrorLog) {
        if (!aiCleaningErrorLog.isNullOrBlank()) {
            onShowAiErrorDialog()
        }
    }

    LaunchedEffect(readerMode) {
        if (readerMode == ReaderMode.ORIGINAL) {
            onReaderModeChangedToOriginal()
        } else {
            onReaderModeChangedToStudy()
        }
        clearPendingFindSelection()
    }

    LaunchedEffect(subtitleId, originalSegments) {
        if (subtitleLastTimestamp > 0 && originalSegments.isNotEmpty()) {
            val index = originalSegments.indexOfFirst { it.startTime >= subtitleLastTimestamp }
            if (index >= 0) {
                originalListState.scrollToItem(index)
            }
        }
    }

    LaunchedEffect(originalListState, originalSegments) {
        snapshotFlow { originalListState.firstVisibleItemIndex }
            .collectLatest { index ->
                if (readerMode == ReaderMode.ORIGINAL &&
                    originalSegments.isNotEmpty() &&
                    index < originalSegments.size
                ) {
                    onOriginalTimestampVisible(originalSegments[index].startTime)
                }
            }
    }

    LaunchedEffect(pendingFindSelection, studyTextView, studyScrollState.maxValue) {
        val selection = pendingFindSelection as? PendingFindSelection.Study ?: return@LaunchedEffect
        onSelectStudyFindMatch(selection)
    }

    LaunchedEffect(
        pendingFindSelection,
        originalSegments,
        originalFallbackScrollState.maxValue
    ) {
        when (val selection = pendingFindSelection) {
            is PendingFindSelection.OriginalFallback -> onSelectOriginalFallbackFindMatch(selection)
            is PendingFindSelection.OriginalSegment -> onSelectOriginalSegmentFindMatch(selection)
            is PendingFindSelection.Study,
            null -> Unit
        }
    }
}

@Composable
internal fun ReaderSystemBarsEffect(
    activity: Activity?,
    view: android.view.View,
    isUiVisible: Boolean,
    isEditing: Boolean
) {
    val window = activity?.window
    val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }

    SideEffect {
        if (insetsController != null) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isUiVisible || isEditing) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(activity, view) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        if (insetsController != null) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
