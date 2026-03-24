package com.deedeedev.ytreader.ui.reader

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.domain.SubtitleParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.core.app.ActivityCompat

internal class OriginalSelectionCoordinator {
    val textViews = mutableMapOf<Int, SelectableHighlightTextView>()
    var activeOwner: Int? = null

    fun clearAllSelections() {
        textViews.values.forEach { it.clearSelection() }
        activeOwner = null
    }
}

private val DEFAULT_NOTE_HIGHLIGHT_COLOR = HighlightColor.YELLOW

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    subtitleId: Long,
    subtitleDao: SubtitleDao,
    highlightNoteDao: HighlightNoteDao,
    userPreferencesRepository: UserPreferencesRepository,
    onChromeReady: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    var isUiVisible by rememberSaveable(subtitleId) { mutableStateOf(false) }
    var isEditing by rememberSaveable(subtitleId) { mutableStateOf(false) }
    val viewModel: ReaderViewModel = viewModel(
        key = "Reader_$subtitleId",
        factory = ReaderViewModel.provideFactory(
            context.applicationContext,
            subtitleDao,
            highlightNoteDao,
            userPreferencesRepository,
            subtitleId
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val subtitle = uiState.subtitle

    ReaderSystemBarsEffect(
        activity = activity,
        view = view,
        isUiVisible = isUiVisible,
        isEditing = isEditing
    )

    if (uiState.isLoading || subtitle == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    LaunchedEffect(subtitle.id) {
        onChromeReady()
    }

    val fontSize = uiState.fontSize
    val fontFamily = when (uiState.fontFamily) {
        "Serif" -> FontFamily.Serif
        "SansSerif" -> FontFamily.SansSerif
        "Monospace" -> FontFamily.Monospace
        "Cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val originalListState = rememberLazyListState()
    val studyScrollState = rememberScrollState()
    val originalFallbackScrollState = rememberScrollState()
    val lineHeightSp = fontSize * uiState.lineHeightMultiplier
    val readerTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val readerBackgroundColor = Color.Transparent.toArgb()
    val appBrightnessPreference by userPreferencesRepository.appBrightness.collectAsStateWithLifecycle()

    var readerMode by rememberSaveable { mutableStateOf(ReaderMode.STUDY) }
    var showTimestamps by rememberSaveable { mutableStateOf(false) }
    // Large transcripts can exceed the saved-instance-state Binder limit if persisted via rememberSaveable.
    var editText by remember(subtitle.id) { mutableStateOf(uiState.content) }
    var showEmptyDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var showFindDialog by remember { mutableStateOf(false) }
    var showFindReplaceDialog by remember { mutableStateOf(false) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    var findResults by remember { mutableStateOf<List<ReaderFindResult>>(emptyList()) }
    var originalSegmentFindResults by remember { mutableStateOf<List<OriginalSegmentFindResult>>(emptyList()) }
    var findErrorMessage by remember { mutableStateOf<String?>(null) }
    var hasSearchedFind by remember { mutableStateOf(false) }
    var findIsCaseSensitive by rememberSaveable { mutableStateOf(false) }
    var pendingFindSelection by remember { mutableStateOf<PendingFindSelection?>(null) }
    var searchResultsMode by remember { mutableStateOf<SearchResultsMode?>(null) }
    var findReplaceErrorMessage by remember { mutableStateOf<String?>(null) }
    var findText by rememberSaveable { mutableStateOf("") }
    var replaceText by rememberSaveable { mutableStateOf("") }
    var isCaseSensitive by rememberSaveable { mutableStateOf(false) }
    var showAiPreviewDialog by remember { mutableStateOf(false) }
    var showAiErrorDialog by remember { mutableStateOf(false) }
    var pendingAiCleaningSourceText by remember { mutableStateOf<String?>(null) }
    var selectionRange by remember { mutableStateOf<SelectionRange?>(null) }
    var activeHighlight by remember { mutableStateOf<TextHighlight?>(null) }
    var showHighlightNoteDialog by remember { mutableStateOf(false) }
    var highlightNoteDraft by remember { mutableStateOf("") }
    var highlightNoteTarget by remember { mutableStateOf<TextHighlight?>(null) }
    var highlightNoteSelectionRange by remember { mutableStateOf<SelectionRange?>(null) }
    var studyTextView by remember { mutableStateOf<JustifiedStudyTextView?>(null) }
    val originalSelectionCoordinator = remember { OriginalSelectionCoordinator() }
    var lastKnownStudyScroll by rememberSaveable(subtitle.id) {
        mutableStateOf(subtitle.lastStudyScroll)
    }
    var hasRestoredStudyScroll by rememberSaveable(subtitle.id) { mutableStateOf(false) }
    var studyViewportHeightPx by remember { mutableStateOf(0) }
    var originalFallbackViewportHeightPx by remember { mutableStateOf(0) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var brightnessIndicatorPercent by remember { mutableStateOf(100) }
    var brightnessHideJob by remember { mutableStateOf<Job?>(null) }
    var gestureBrightness by remember { mutableStateOf<Float?>(null) }

    val persistReadingProgress by rememberUpdatedState(newValue = {
        val scrollToSave = if (readerMode == ReaderMode.STUDY) {
            studyScrollState.value
        } else {
            lastKnownStudyScroll
        }
        viewModel.updateLastStudyScroll(scrollToSave)
    })

    DisposableEffect(Unit) {
        onDispose { brightnessHideJob?.cancel() }
    }

    val hasUnsavedChanges = isEditing && editText != uiState.content

    val originalSegments = remember(subtitle.content) {
        SubtitleParser.parseToSegments(subtitle.content)
    }
    val originalFallbackText = uiState.originalParsedText.ifBlank { uiState.content }
    val originalModeText = remember(originalSegments, showTimestamps, originalFallbackText) {
        formatOriginalModeCopyText(originalSegments, showTimestamps, originalFallbackText)
    }

    fun currentText(): String = currentReaderText(
        readerMode = readerMode,
        isEditing = isEditing,
        editText = editText,
        studyContent = uiState.content,
        originalModeText = originalModeText
    )

    fun applyTextUpdate(updated: String) {
        applyReaderTextUpdate(
            updated = updated,
            isEditing = isEditing,
            setEditText = { editText = it },
            updateContent = { viewModel.updateContent(it) }
        )
    }

    fun resetFindReplaceDialogState() {
        findReplaceErrorMessage = null
        findText = ""
        replaceText = ""
        isCaseSensitive = false
    }

    fun resetFindDialogState() {
        findQuery = ""
        findResults = emptyList()
        originalSegmentFindResults = emptyList()
        findErrorMessage = null
        hasSearchedFind = false
        findIsCaseSensitive = false
    }

    fun clearSearchResultsMode() {
        searchResultsMode = null
        pendingFindSelection = null
    }

    fun dismissHighlightNoteDialog() {
        showHighlightNoteDialog = false
        highlightNoteDraft = ""
        highlightNoteTarget = null
        highlightNoteSelectionRange = null
    }

    fun openHighlightNoteDialog() {
        val selectedHighlight = activeHighlight
        if (selectedHighlight != null) {
            highlightNoteTarget = selectedHighlight
            highlightNoteSelectionRange = null
            highlightNoteDraft = selectedHighlight.note.orEmpty()
            showHighlightNoteDialog = true
            return
        }

        val selectedRange = selectionRange ?: return
        highlightNoteTarget = null
        highlightNoteSelectionRange = selectedRange
        highlightNoteDraft = ""
        showHighlightNoteDialog = true
    }

    fun activateSearchResultsMode(mode: SearchResultsMode) {
        searchResultsMode = mode
        pendingFindSelection = activePendingFindSelection(mode)
    }

    fun moveSearchResultsBackward() {
        val currentMode = searchResultsMode ?: return
        val updated = moveToPreviousSearchResult(currentMode)
        searchResultsMode = updated
        pendingFindSelection = activePendingFindSelection(updated)
    }

    fun moveSearchResultsForward() {
        val currentMode = searchResultsMode ?: return
        val updated = moveToNextSearchResult(currentMode)
        searchResultsMode = updated
        pendingFindSelection = activePendingFindSelection(updated)
    }

    fun runFindSearch() {
        clearSearchResultsMode()
        executeReaderFindSearch(
            query = findQuery,
            isCaseSensitive = findIsCaseSensitive,
            readerMode = readerMode,
            sourceText = currentText(),
            originalSegments = originalSegments,
            originalFallbackText = originalFallbackText,
            setHasSearched = { hasSearchedFind = it },
            setErrorMessage = { findErrorMessage = it },
            setFindResults = { findResults = it },
            setOriginalSegmentResults = { originalSegmentFindResults = it }
        )
    }

    fun runPendingAction(action: PendingAction) {
        executeReaderPendingAction(
            action = action,
            uiContent = uiState.content,
            persistReadingProgress = persistReadingProgress,
            onBack = onBack,
            setIsEditing = { isEditing = it },
            setEditText = { editText = it },
            clearStudySelection = {
                selectionRange = null
                activeHighlight = null
                dismissHighlightNoteDialog()
                studyTextView?.clearSelection()
                clearSearchResultsMode()
            },
            setReaderMode = { readerMode = it }
        )
    }

    suspend fun startAiCleaningJob(sourceText: String) {
        startReaderAiCleaningJob(
            viewModel = viewModel,
            sourceText = sourceText,
            context = context,
            showSnackbar = { snackbarHostState.showSnackbar(it) }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val sourceText = pendingAiCleaningSourceText
        pendingAiCleaningSourceText = null
        if (sourceText.isNullOrBlank()) {
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch {
            if (!granted) {
                val shouldShowSettingsHint = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                val messageRes = if (shouldShowSettingsHint) {
                    R.string.notification_permission_denied_settings_hint
                } else {
                    R.string.notification_permission_denied_hint
                }
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(messageRes),
                    actionLabel = context.getString(R.string.open_settings)
                )
                if (result == SnackbarResult.ActionPerformed) {
                    openAppNotificationSettings(context)
                }
            }
            startAiCleaningJob(sourceText)
        }
    }

    fun requestAction(action: PendingAction) {
        requestReaderAction(
            action = action,
            hasUnsavedChanges = hasUnsavedChanges,
            showUnsavedDialog = { showUnsavedDialog = true },
            runPendingAction = { runPendingAction(it) },
            setPendingAction = { pendingAction = it }
        )
    }

    fun showBrightnessValue(brightness: Float) {
        brightnessIndicatorPercent = (brightness.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)
        showBrightnessIndicator = true
        brightnessHideJob?.cancel()
    }

    fun scheduleHideBrightnessValue() {
        brightnessHideJob?.cancel()
        brightnessHideJob = coroutineScope.launch {
            delay(BRIGHTNESS_INDICATOR_HIDE_DELAY_MS)
            showBrightnessIndicator = false
        }
    }

    suspend fun scrollOnePage(isForward: Boolean) {
        when (readerMode) {
            ReaderMode.STUDY -> {
                val target = targetScrollForPageStep(
                    currentValue = studyScrollState.value,
                    maxValue = studyScrollState.maxValue,
                    viewportHeightPx = studyViewportHeightPx,
                    isForward = isForward
                )
                studyScrollState.scrollTo(target)
            }

            ReaderMode.ORIGINAL -> {
                if (originalSegments.isEmpty()) {
                    val target = targetScrollForPageStep(
                        currentValue = originalFallbackScrollState.value,
                        maxValue = originalFallbackScrollState.maxValue,
                        viewportHeightPx = originalFallbackViewportHeightPx,
                        isForward = isForward
                    )
                    originalFallbackScrollState.scrollTo(target)
                } else {
                    val targetIndex = targetListIndexForPageStep(
                        currentFirstVisibleItemIndex = originalListState.firstVisibleItemIndex,
                        totalItems = originalSegments.size,
                        visibleItemsCount = originalListState.layoutInfo.visibleItemsInfo.size,
                        isForward = isForward
                    )
                    originalListState.scrollToItem(targetIndex)
                }
            }
        }
    }

    val activeStudySearchRange = remember(searchResultsMode) {
        when (val mode = searchResultsMode) {
            is SearchResultsMode.Study -> mode.results.getOrNull(mode.activeIndex)?.let {
                SelectionRange(start = it.start, end = it.end)
            }

            else -> null
        }
    }
    val activeOriginalFallbackSearchRange = remember(searchResultsMode) {
        when (val mode = searchResultsMode) {
            is SearchResultsMode.OriginalFallback -> mode.results.getOrNull(mode.activeIndex)?.let {
                SelectionRange(start = it.start, end = it.end)
            }

            else -> null
        }
    }
    val activeOriginalSegmentSearchResult = remember(searchResultsMode) {
        when (val mode = searchResultsMode) {
            is SearchResultsMode.OriginalSegment -> mode.results.getOrNull(mode.activeIndex)
            else -> null
        }
    }

    fun handleReaderTap(tapPosition: ReaderTapPosition) {
        if (isEditing) return

        when (classifyReaderTapZone(tapPosition)) {
            ReaderTapZone.PREVIOUS_PAGE -> {
                coroutineScope.launch { scrollOnePage(isForward = false) }
            }

            ReaderTapZone.TOGGLE_UI -> {
                isUiVisible = !isUiVisible
            }

            ReaderTapZone.NEXT_PAGE -> {
                coroutineScope.launch { scrollOnePage(isForward = true) }
            }
        }
    }

    ReaderCoreEffects(
        subtitleId = subtitle.id,
        subtitleLastStudyScroll = subtitle.lastStudyScroll,
        subtitleLastTimestamp = subtitle.lastTimestamp,
        uiContent = uiState.content,
        pendingAiCleanedText = uiState.pendingAiCleanedText,
        aiCleaningErrorLog = uiState.aiCleaningErrorLog,
        isEditing = isEditing,
        readerMode = readerMode,
        studyScrollState = studyScrollState,
        originalFallbackScrollState = originalFallbackScrollState,
        originalListState = originalListState,
        originalSegments = originalSegments,
        studyTextView = studyTextView,
        pendingFindSelection = pendingFindSelection,
        onEditTextSync = { editText = it },
        clearSelectionState = {
            selectionRange = null
            activeHighlight = null
            dismissHighlightNoteDialog()
            studyTextView?.clearSelection()
            originalSelectionCoordinator.clearAllSelections()
            clearSearchResultsMode()
        },
        onResetAiDialogs = {
            showAiPreviewDialog = false
            showAiErrorDialog = false
        },
        setLastKnownStudyScroll = { lastKnownStudyScroll = it },
        setHasRestoredStudyScroll = { hasRestoredStudyScroll = it },
        hasRestoredStudyScroll = hasRestoredStudyScroll,
        persistReadingProgress = persistReadingProgress,
        activity = activity,
        onEnterEditing = {
            isUiVisible = true
            selectionRange = null
            activeHighlight = null
            dismissHighlightNoteDialog()
            studyTextView?.clearSelection()
            originalSelectionCoordinator.clearAllSelections()
            clearSearchResultsMode()
        },
        onShowAiPreviewDialog = { showAiPreviewDialog = true },
        onShowAiErrorDialog = { showAiErrorDialog = true },
        onReaderModeChangedToOriginal = {
            selectionRange = null
            activeHighlight = null
            dismissHighlightNoteDialog()
            studyTextView?.clearSelection()
            clearSearchResultsMode()
        },
        onReaderModeChangedToStudy = {
            originalSelectionCoordinator.clearAllSelections()
            clearSearchResultsMode()
        },
        clearPendingFindSelection = { pendingFindSelection = null },
        onOriginalTimestampVisible = { viewModel.updateLastTimestamp(it) },
        onSelectStudyFindMatch = { selection ->
            val textView = studyTextView ?: return@ReaderCoreEffects
            val targetScroll = textView.verticalOffsetForSelection(selection.start)
                .coerceIn(0, studyScrollState.maxValue)
            studyScrollState.animateScrollTo(targetScroll)
            pendingFindSelection = null
        },
        onSelectOriginalFallbackFindMatch = { selection ->
            val textView = originalSelectionCoordinator.textViews[-1] ?: return@ReaderCoreEffects
            val targetScroll = textView.verticalOffsetForSelection(selection.start)
                .coerceIn(0, originalFallbackScrollState.maxValue)
            originalFallbackScrollState.animateScrollTo(targetScroll)
            pendingFindSelection = null
        },
        onSelectOriginalSegmentFindMatch = { selection ->
            originalListState.animateScrollToItem(selection.segmentIndex)
            repeat(10) {
                val textView = originalSelectionCoordinator.textViews[selection.segmentIndex]
                if (textView != null && textView.isShown) {
                    pendingFindSelection = null
                    return@ReaderCoreEffects
                }
                delay(16)
            }
            pendingFindSelection = null
        }
    )

    BackHandler {
        if (searchResultsMode != null) {
            clearSearchResultsMode()
        } else if (isUiVisible) {
            isUiVisible = false
        } else {
            requestAction(PendingAction.ExitScreen)
        }
    }

    val showSelectionToolbar = readerMode == ReaderMode.STUDY &&
        !isEditing &&
        searchResultsMode == null &&
        (selectionRange != null || activeHighlight != null)
    val showSearchResultsToolbar = !isEditing && searchResultsMode != null
    val topContentPadding by animateDpAsState(
        targetValue = if (isUiVisible) TopAppBarDefaults.TopAppBarExpandedHeight else 0.dp,
        label = "readerTopContentPadding"
    )
    val bottomContentPadding by animateDpAsState(
        targetValue = (if (isUiVisible) READER_BOTTOM_BAR_HEIGHT else 0.dp) +
            (if (showSearchResultsToolbar) READER_SEARCH_RESULTS_BAR_HEIGHT else 0.dp),
        label = "readerBottomContentPadding"
    )

    val fullscreenProgressPercent by remember(
        readerMode,
        originalSegments,
        originalListState.firstVisibleItemIndex,
        originalListState.canScrollForward,
        originalListState.canScrollBackward,
        studyScrollState.value,
        studyScrollState.maxValue,
        originalFallbackScrollState.value,
        originalFallbackScrollState.maxValue
    ) {
        derivedStateOf {
            when (readerMode) {
                ReaderMode.ORIGINAL -> {
                    if (originalSegments.isEmpty()) {
                        scrollPercent(
                            value = originalFallbackScrollState.value,
                            maxValue = originalFallbackScrollState.maxValue,
                            canScrollForward = originalFallbackScrollState.canScrollForward,
                            canScrollBackward = originalFallbackScrollState.canScrollBackward
                        )
                    } else {
                        lazyListScrollPercent(
                            firstVisibleItemIndex = originalListState.firstVisibleItemIndex,
                            totalItems = originalSegments.size,
                            canScrollForward = originalListState.canScrollForward,
                            canScrollBackward = originalListState.canScrollBackward
                        )
                    }
                }

                ReaderMode.STUDY -> {
                    scrollPercent(
                        value = studyScrollState.value,
                        maxValue = studyScrollState.maxValue,
                        canScrollForward = studyScrollState.canScrollForward,
                        canScrollBackward = studyScrollState.canScrollBackward
                    )
                }
            }
        }
    }

    val fullscreenPageProgress by remember(
        readerMode,
        originalSegments,
        originalListState.firstVisibleItemIndex,
        originalListState.canScrollForward,
        originalListState.layoutInfo.visibleItemsInfo.size,
        studyScrollState.value,
        studyScrollState.maxValue,
        studyViewportHeightPx,
        originalFallbackScrollState.value,
        originalFallbackScrollState.maxValue,
        originalFallbackViewportHeightPx
    ) {
        derivedStateOf {
            when (readerMode) {
                ReaderMode.ORIGINAL -> {
                    if (originalSegments.isEmpty()) {
                        pagedScrollProgress(
                            value = originalFallbackScrollState.value,
                            maxValue = originalFallbackScrollState.maxValue,
                            viewportHeightPx = originalFallbackViewportHeightPx
                        )
                    } else {
                        lazyListPageProgress(
                            firstVisibleItemIndex = originalListState.firstVisibleItemIndex,
                            totalItems = originalSegments.size,
                            visibleItemsCount = originalListState.layoutInfo.visibleItemsInfo.size,
                            canScrollForward = originalListState.canScrollForward
                        )
                    }
                }

                ReaderMode.STUDY -> {
                    pagedScrollProgress(
                        value = studyScrollState.value,
                        maxValue = studyScrollState.maxValue,
                        viewportHeightPx = studyViewportHeightPx
                    )
                }
            }
        }
    }

    LaunchedEffect(fullscreenProgressPercent) {
        viewModel.updateReadingProgressPercent(fullscreenProgressPercent)
    }

    ReaderScreenMainLayer(
        readerMode = readerMode,
        isUiVisible = isUiVisible,
        isEditing = isEditing,
        showTimestamps = showTimestamps,
        subtitleTitle = subtitle.title,
        fontSize = fontSize,
        fontFamilyName = uiState.fontFamily,
        fontFamily = fontFamily,
        lineHeightMultiplier = uiState.lineHeightMultiplier,
        lineHeightSp = lineHeightSp,
        readerTextColor = readerTextColor,
        readerBackgroundColor = readerBackgroundColor,
        originalSegments = originalSegments,
        originalFallbackText = originalFallbackText,
        studyContent = uiState.content,
        highlights = uiState.highlights,
        activeStudySearchRange = activeStudySearchRange,
        activeOriginalFallbackSearchRange = activeOriginalFallbackSearchRange,
        activeOriginalSegmentSearchResult = activeOriginalSegmentSearchResult,
        editText = editText,
        topContentPadding = topContentPadding,
        bottomContentPadding = bottomContentPadding,
        originalListState = originalListState,
        originalFallbackScrollState = originalFallbackScrollState,
        studyScrollState = studyScrollState,
        originalSelectionCoordinator = originalSelectionCoordinator,
        appBrightnessPreference = appBrightnessPreference,
        gestureBrightness = gestureBrightness,
        currentEffectiveBrightness = {
            currentEffectiveBrightness(activity, appBrightnessPreference)
        },
        onGestureBrightnessChanged = { gestureBrightness = it },
        onShowBrightnessValue = { showBrightnessValue(it) },
        onScheduleHideBrightnessValue = { scheduleHideBrightnessValue() },
        onApplyReaderBrightness = { applyReaderBrightness(activity, it) },
        onPersistBrightnessPreference = { userPreferencesRepository.setAppBrightness(it) },
        onReaderTap = { handleReaderTap(it) },
        onSelectionRangeChanged = { start, end ->
            val normalizedStart = minOf(start, end)
            val normalizedEnd = maxOf(start, end)
            selectionRange = if (normalizedStart >= 0 && normalizedStart < normalizedEnd) {
                activeHighlight = null
                dismissHighlightNoteDialog()
                clearSearchResultsMode()
                SelectionRange(normalizedStart, normalizedEnd)
            } else {
                null
            }
        },
        onHighlightTapped = { tappedHighlight ->
            clearSearchResultsMode()
            dismissHighlightNoteDialog()
            activeHighlight = tappedHighlight
            selectionRange = null
        },
        hasActiveHighlight = { activeHighlight != null },
        onClearActiveHighlight = { activeHighlight = null },
        clearSelectionNow = { studyTextView?.clearSelection() },
        onStudyTextViewReady = { studyTextView = it },
        onEditTextChange = { editText = it },
        onOriginalFallbackViewportChanged = { originalFallbackViewportHeightPx = it },
        onStudyViewportChanged = { studyViewportHeightPx = it },
        onRequestAction = { requestAction(it) },
        onToggleTimestamps = { showTimestamps = !showTimestamps },
        onEditSaveTap = {
            if (isEditing) {
                if (editText.isBlank()) {
                    showEmptyDialog = true
                } else {
                    viewModel.updateContent(editText.trimEnd())
                    isEditing = false
                }
            } else {
                isEditing = true
            }
        },
        onDecreaseFontSize = { viewModel.updateFontSize(fontSize - 2f) },
        onIncreaseFontSize = { viewModel.updateFontSize(fontSize + 2f) },
        onChangeFontFamily = { viewModel.updateFontFamily(it) },
        isNotificationPermissionGranted = hasNotificationPermission(context),
        currentText = currentText(),
        onCopyText = { clipboardManager.setText(it) },
        onShareText = { textToShare ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, textToShare)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share text"))
        },
        onRemoveEmptyLines = {
            val cleaned = currentText()
                .lines()
                .filter { it.isNotBlank() }
                .joinToString("\n")
            applyTextUpdate(cleaned)
        },
        onShowFind = {
            clearSearchResultsMode()
            resetFindDialogState()
            showFindDialog = true
        },
        onShowFindAndReplace = {
            clearSearchResultsMode()
            resetFindReplaceDialogState()
            showFindReplaceDialog = true
        },
        onStartAiCleaning = { sourceText ->
            if (sourceText.isBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Nothing to clean.")
                }
            } else {
                coroutineScope.launch { startAiCleaningJob(sourceText) }
            }
        },
        onRequestNotificationPermission = { sourceText ->
            if (sourceText.isBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Nothing to clean.")
                }
            } else {
                pendingAiCleaningSourceText = sourceText
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        isAiCleaning = uiState.isAiCleaning,
        showSelectionToolbar = showSelectionToolbar,
        onSelectionColorSelected = { color ->
            val selectedHighlight = activeHighlight
            if (selectedHighlight != null) {
                viewModel.updateHighlightColor(selectedHighlight, color)
                activeHighlight = selectedHighlight.copy(color = color)
            } else {
                val range = selectionRange ?: return@ReaderScreenMainLayer
                viewModel.applyHighlight(range.start, range.end, color)
                selectionRange = null
                studyTextView?.clearSelection()
            }
        },
        onSelectionNoteClick = { openHighlightNoteDialog() },
        selectionHasNote = activeHighlight?.note != null,
        onDeleteHighlight = {
            val selectedHighlight = activeHighlight ?: return@ReaderScreenMainLayer
            viewModel.deleteHighlight(selectedHighlight)
            activeHighlight = null
            selectionRange = null
            dismissHighlightNoteDialog()
            studyTextView?.clearSelection()
        },
        showSearchResultsToolbar = showSearchResultsToolbar,
        searchResultsCurrentIndex = (searchResultsMode?.activeIndex ?: 0) + 1,
        searchResultsTotalCount = searchResultsMode?.totalResults ?: 0,
        canNavigateToPreviousSearchResult = canNavigateToPreviousSearchResult(searchResultsMode),
        canNavigateToNextSearchResult = canNavigateToNextSearchResult(searchResultsMode),
        onNavigateToPreviousSearchResult = { moveSearchResultsBackward() },
        onNavigateToNextSearchResult = { moveSearchResultsForward() },
        onCloseSearchResults = { clearSearchResultsMode() },
        fullscreenProgressPercent = fullscreenProgressPercent,
        fullscreenPageProgress = fullscreenPageProgress,
        showBrightnessIndicator = showBrightnessIndicator,
        brightnessIndicatorPercent = brightnessIndicatorPercent,
        snackbarHostState = snackbarHostState
    )

    ReaderDialogHost(
        showUnsavedDialog = showUnsavedDialog,
        onDismissUnsaved = {
            showUnsavedDialog = false
            pendingAction = null
        },
        onDiscardUnsaved = {
            showUnsavedDialog = false
            isEditing = false
            editText = uiState.content
            pendingAction?.let { action ->
                runPendingAction(action)
            }
            pendingAction = null
        },
        showFindDialog = showFindDialog,
        findQuery = findQuery,
        onFindQueryChange = { findQuery = it },
        findErrorMessage = findErrorMessage,
        hasSearchedFind = hasSearchedFind,
        findResults = findResults,
        originalSegmentFindResults = originalSegmentFindResults,
        findIsCaseSensitive = findIsCaseSensitive,
        onFindIsCaseSensitiveChange = { findIsCaseSensitive = it },
        onRunFindSearch = { runFindSearch() },
        onSelectStudyFindResult = { result ->
            showFindDialog = false
            selectionRange = null
            activeHighlight = null
            dismissHighlightNoteDialog()
            studyTextView?.clearSelection()
            originalSelectionCoordinator.clearAllSelections()
            activateSearchResultsMode(
                SearchResultsMode.Study(
                    results = findResults,
                    activeIndex = (result.number - 1).coerceAtLeast(0)
                )
            )
        },
        onSelectOriginalFallbackFindResult = { result ->
            showFindDialog = false
            selectionRange = null
            activeHighlight = null
            dismissHighlightNoteDialog()
            studyTextView?.clearSelection()
            originalSelectionCoordinator.clearAllSelections()
            activateSearchResultsMode(
                SearchResultsMode.OriginalFallback(
                    results = findResults,
                    activeIndex = (result.number - 1).coerceAtLeast(0)
                )
            )
        },
        onSelectOriginalSegmentFindResult = { result ->
            showFindDialog = false
            selectionRange = null
            activeHighlight = null
            dismissHighlightNoteDialog()
            studyTextView?.clearSelection()
            originalSelectionCoordinator.clearAllSelections()
            activateSearchResultsMode(
                SearchResultsMode.OriginalSegment(
                    results = originalSegmentFindResults,
                    activeIndex = (result.number - 1).coerceAtLeast(0)
                )
            )
        },
        isOriginalMode = readerMode == ReaderMode.ORIGINAL,
        onCloseFindDialog = {
            resetFindDialogState()
            showFindDialog = false
        },
        showFindReplaceDialog = showFindReplaceDialog,
        findText = findText,
        onFindTextChange = {
            findText = it
            findReplaceErrorMessage = null
        },
        replaceText = replaceText,
        onReplaceTextChange = { replaceText = it },
        findReplaceErrorMessage = findReplaceErrorMessage,
        isCaseSensitive = isCaseSensitive,
        onCaseSensitiveChange = { isCaseSensitive = it },
        onReplace = {
            val replaceResult = replaceRegexMatches(
                text = currentText(),
                query = findText,
                replacement = replaceText,
                isCaseSensitive = isCaseSensitive
            )
            if (replaceResult.isFailure) {
                findReplaceErrorMessage = replaceResult.exceptionOrNull()?.message ?: "Invalid regex."
            } else {
                val updated = replaceResult.getOrThrow()
                applyTextUpdate(updated)
                resetFindReplaceDialogState()
                showFindReplaceDialog = false
            }
        },
        onCancelFindReplace = {
            resetFindReplaceDialogState()
            showFindReplaceDialog = false
        },
        showAiPreviewDialog = showAiPreviewDialog,
        previewText = uiState.pendingAiCleanedText,
        onApplyAiPreview = { previewText ->
            if (isEditing) {
                editText = previewText
            } else {
                applyTextUpdate(previewText)
            }
            showAiPreviewDialog = false
            viewModel.clearPendingAiCleaningResult()
        },
        onCancelAiPreview = {
            showAiPreviewDialog = false
            viewModel.clearPendingAiCleaningResult()
        },
        showAiErrorDialog = showAiErrorDialog,
        aiErrorSummary = uiState.aiCleaningErrorSummary,
        aiErrorLog = uiState.aiCleaningErrorLog,
        clipboardSetText = { clipboardManager.setText(it) },
        onDismissAiError = {
            showAiErrorDialog = false
            viewModel.clearAiCleaningError()
        },
        showEmptyDialog = showEmptyDialog,
        onDismissEmptyDialog = { showEmptyDialog = false },
        showHighlightNoteDialog = showHighlightNoteDialog,
        highlightNoteText = highlightNoteDraft,
        hasExistingHighlightNote = highlightNoteTarget?.note != null,
        onHighlightNoteTextChange = { highlightNoteDraft = it },
        onSaveHighlightNote = {
            val existingHighlight = highlightNoteTarget
            val normalizedNote = normalizeHighlightNote(highlightNoteDraft)
            when {
                existingHighlight != null && normalizedNote != null -> {
                    viewModel.saveHighlightNote(existingHighlight, normalizedNote)
                    activeHighlight = existingHighlight.copy(note = normalizedNote)
                    coroutineScope.launch { snackbarHostState.showSnackbar("Note saved.") }
                }

                existingHighlight != null -> {
                    viewModel.deleteHighlightNote(existingHighlight)
                    activeHighlight = existingHighlight.copy(note = null)
                    coroutineScope.launch { snackbarHostState.showSnackbar("Note removed.") }
                }

                normalizedNote != null -> {
                    val range = highlightNoteSelectionRange
                    if (range != null) {
                        val createdHighlight = viewModel.applyHighlightWithNote(
                            start = range.start,
                            end = range.end,
                            color = DEFAULT_NOTE_HIGHLIGHT_COLOR,
                            note = normalizedNote
                        )
                        activeHighlight = createdHighlight
                        selectionRange = null
                        studyTextView?.clearSelection()
                        coroutineScope.launch { snackbarHostState.showSnackbar("Note saved.") }
                    }
                }
            }
            dismissHighlightNoteDialog()
        },
        onDismissHighlightNote = { dismissHighlightNoteDialog() },
        onDeleteHighlightNote = {
            highlightNoteTarget?.let { target ->
                viewModel.deleteHighlightNote(target)
                activeHighlight = target.copy(note = null)
                coroutineScope.launch { snackbarHostState.showSnackbar("Note removed.") }
            }
            dismissHighlightNoteDialog()
        },
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope
    )
}
