package com.deedeedev.ytreader.ui.reader

import android.Manifest
import android.util.Log
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
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
import com.deedeedev.ytreader.AppContainer
import com.deedeedev.ytreader.ui.FontOption
import com.deedeedev.ytreader.data.NoteRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.local.BookmarkEntity
import com.deedeedev.ytreader.domain.SubtitleParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.core.app.ActivityCompat
import android.webkit.WebView
import com.deedeedev.ytreader.ui.reader.webview.WebViewReaderJs

internal class OriginalSelectionCoordinator {
    val textViews = mutableMapOf<Int, SelectableHighlightTextView>()
    var activeOwner: Int? = null

    fun clearAllSelections() {
        textViews.values.forEach { it.clearSelection() }
        activeOwner = null
    }
}

class ReaderTestHooks {
    lateinit var showChrome: () -> Unit
    lateinit var hideChrome: () -> Unit
    lateinit var activateFirstHighlight: () -> Unit
}

private const val TAG = "ReaderScreen"

private val DEFAULT_NOTE_HIGHLIGHT_COLOR = HighlightColor.YELLOW

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScreen(
    appContainer: AppContainer,
    subtitleId: Long,
    subtitleRepository: SubtitleRepository,
    noteRepository: NoteRepository,
    userPreferencesRepository: UserPreferencesRepository,
    initialReaderLocation: ReaderLocation? = null,
    initialJumpBackState: JumpBackState? = null,
    initialHighlightRange: Pair<Int, Int>? = null,
    initialBookmarkStart: Int? = null,
    onOpenVideoNotes: (String, JumpBackState?) -> Unit,
    onNavigateToReaderLocation: (ReaderLocation) -> Unit,
    onInitialNavigationConsumed: () -> Unit,
    onChromeReady: () -> Unit,
    onTestHooksReady: ((ReaderTestHooks) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    var isUiVisible by rememberSaveable(subtitleId) { mutableStateOf(false) }
    var isEditing by rememberSaveable(subtitleId) { mutableStateOf(false) }
    val testHooks = remember { ReaderTestHooks() }
    val viewModel: ReaderViewModel = viewModel(
        key = "Reader_$subtitleId",
        factory = ReaderViewModel.provideFactory(
            appContainer,
            context.applicationContext,
            subtitleRepository,
            noteRepository,
            userPreferencesRepository,
            subtitleId
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val subtitle = uiState.subtitle

    ReaderSystemBarsEffect(
        activity = activity,
        view = view
    )

    if (uiState.isLoading || subtitle == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    DisposableEffect(onTestHooksReady) {
        onTestHooksReady?.invoke(testHooks)
        onDispose { }
    }
    LaunchedEffect(subtitle.id) {
        onChromeReady()
    }

    val fontSize = uiState.fontSize
    val fontFamily = when (FontOption.fromStorageValue(uiState.fontFamily)) {
        FontOption.SERIF -> FontFamily.Serif
        FontOption.SANS_SERIF -> FontFamily.SansSerif
        FontOption.MONOSPACE -> FontFamily.Monospace
        FontOption.CURSIVE -> FontFamily.Cursive
        FontOption.DEFAULT -> FontFamily.Default
    }

    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val originalListState = rememberLazyListState()
    val studyScrollState = rememberScrollState()
    val originalFallbackScrollState = rememberScrollState()
    val lineHeightSp = fontSize * uiState.lineHeightMultiplier
    val readerTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val readerBackgroundColor = MaterialTheme.colorScheme.surface.toArgb()
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
    var interactiveReplaceState by remember { mutableStateOf<InteractiveReplaceState?>(null) }
    var searchInOriginalResults by remember { mutableStateOf<List<SearchInOriginalResult>>(emptyList()) }
    var showSearchInOriginalDialog by remember { mutableStateOf(false) }
    var searchInOriginalQuery by remember { mutableStateOf("") }
    var showAiPreviewDialog by remember { mutableStateOf(false) }
    var showAiErrorDialog by remember { mutableStateOf(false) }
    var pendingAiCleaningSourceText by remember { mutableStateOf<String?>(null) }
    var selectionRange by remember { mutableStateOf<SelectionRange?>(null) }
    var activeHighlight by remember { mutableStateOf<TextHighlight?>(null) }
    var showHighlightNoteDialog by remember { mutableStateOf(false) }
    var highlightNoteDraft by remember { mutableStateOf("") }
    var highlightNoteTarget by remember { mutableStateOf<TextHighlight?>(null) }
    var highlightNoteSelectionRange by remember { mutableStateOf<SelectionRange?>(null) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showJumpToTimeDialog by remember { mutableStateOf(false) }
    var bookmarkTitleDraft by remember { mutableStateOf("") }
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var pendingBookmarkAnchorStart by remember { mutableStateOf<Int?>(null) }
    var pendingBookmarkFallbackTitle by remember { mutableStateOf("") }
    var suppressSelectionToolbar by remember { mutableStateOf(false) }
    var studyTextView by remember { mutableStateOf<JustifiedStudyTextView?>(null) }
    val originalSelectionCoordinator = remember { OriginalSelectionCoordinator() }
    SideEffect {
        testHooks.showChrome = { isUiVisible = true }
        testHooks.hideChrome = { isUiVisible = false }
        testHooks.activateFirstHighlight = {
            suppressSelectionToolbar = false
            activeHighlight = uiState.highlights.firstOrNull()
            selectionRange = null
        }
    }
    var pendingInitialHighlightRange by remember(subtitleId, initialHighlightRange) {
        mutableStateOf(
            initialHighlightRange?.let { (start, end) ->
                SelectionRange(start = start, end = end)
            }
        )
    }
    var pendingInitialBookmarkStart by remember(subtitleId, initialBookmarkStart) {
        mutableStateOf(initialBookmarkStart)
    }
    var pendingInitialReaderLocation by remember(subtitleId) {
        mutableStateOf(initialReaderLocation?.takeIf { it.subtitleId == subtitleId })
    }
    var jumpBackState by remember(subtitleId) {
        mutableStateOf(initialJumpBackState).also {
            Log.d(TAG, "jumpBackState init: subtitleId=$subtitleId initialJumpBackState=$initialJumpBackState")
        }
    }
    var lastKnownStudyScroll by rememberSaveable(subtitle.id) {
        mutableStateOf(uiState.lastStudyScroll)
    }
    var hasRestoredStudyScroll by rememberSaveable(subtitle.id) { mutableStateOf(false) }
    var studyViewportHeightPx by remember { mutableStateOf(0) }
    var originalFallbackViewportHeightPx by remember { mutableStateOf(0) }
    var stableStudyViewportHeightPx by remember { mutableStateOf(0) }
    var stableOriginalFallbackViewportHeightPx by remember { mutableStateOf(0) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var brightnessIndicatorPercent by remember { mutableStateOf(100) }
    var brightnessHideJob by remember { mutableStateOf<Job?>(null) }
    var gestureBrightness by remember { mutableStateOf<Float?>(null) }
    var showProgressIndicator by remember { mutableStateOf(false) }
    var progressIndicatorHideJob by remember { mutableStateOf<Job?>(null) }
    var webViewStudyRef by remember { mutableStateOf<WebView?>(null) }
    var webViewOriginalRef by remember { mutableStateOf<WebView?>(null) }
    var webViewScrollY by remember { mutableStateOf(0) }
    var webViewTotalHeight by remember { mutableStateOf(0) }
    var webViewViewportHeight by remember { mutableStateOf(0) }
    var webViewCharOffsetAtTop by remember { mutableStateOf(0) }

    val persistReadingProgress by rememberUpdatedState(newValue = {
        viewModel.updateLastStudyScroll(webViewCharOffsetAtTop)
    })

    val hasInitialNavigationTarget = initialReaderLocation != null ||
        initialHighlightRange != null ||
        initialBookmarkStart != null

    LaunchedEffect(subtitleId, pendingInitialReaderLocation, jumpBackState) {
        if (pendingInitialReaderLocation != null || jumpBackState != null) {
            onInitialNavigationConsumed()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            brightnessHideJob?.cancel()
            progressIndicatorHideJob?.cancel()
        }
    }

    val hasUnsavedChanges = isEditing && editText != uiState.content

    val originalSegments = remember(subtitle.content) {
        SubtitleParser.parseToSegments(subtitle.content)
    }
    val originalFallbackText = uiState.originalParsedText.ifBlank { uiState.content }
    val originalModeText = remember(originalSegments, showTimestamps, originalFallbackText) {
        formatOriginalModeCopyText(context, originalSegments, showTimestamps, originalFallbackText)
    }

    fun showAndScheduleHideProgressIndicator() {
        showProgressIndicator = true
        progressIndicatorHideJob?.cancel()
        progressIndicatorHideJob = coroutineScope.launch {
            delay(PROGRESS_INDICATOR_HIDE_DELAY_MS)
            showProgressIndicator = false
        }
    }

    LaunchedEffect(readerMode, originalSegments) {
        snapshotFlow {
            when (readerMode) {
                ReaderMode.STUDY -> studyScrollState.value
                ReaderMode.ORIGINAL -> {
                    if (originalSegments.isEmpty()) {
                        originalFallbackScrollState.value
                    } else {
                        originalListState.firstVisibleItemIndex
                    }
                }
            }
        }.collect {
            showAndScheduleHideProgressIndicator()
        }
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
        interactiveReplaceState = null
    }

    fun clearJumpBackState() {
        Log.d(TAG, "clearJumpBackState called, previous=$jumpBackState", Exception("clearJumpBackState stacktrace"))
        jumpBackState = null
    }

    fun captureCurrentLocation(): ReaderLocation? {
        val anchor = when (readerMode) {
            ReaderMode.STUDY -> {
                val anchorStart = studyTextView?.topVisibleLineAnchor(studyScrollState.value) ?: return null
                ReaderAnchor.Study(anchorStart)
            }

            ReaderMode.ORIGINAL -> {
                if (originalSegments.isEmpty()) {
                    val textView = originalSelectionCoordinator.textViews[-1] ?: return null
                    val anchorStart = textView.topVisibleLineAnchor(originalFallbackScrollState.value) ?: return null
                    ReaderAnchor.OriginalFallback(anchorStart)
                } else {
                    ReaderAnchor.OriginalSegment(originalListState.firstVisibleItemIndex)
                }
            }
        }
        return ReaderLocation(subtitleId = subtitle.id, anchor = anchor)
    }

    fun registerProgrammaticJump(
        reason: ReaderJumpReason,
        label: String? = null,
        replaceExisting: Boolean = true
    ) {
        if (!replaceExisting && jumpBackState != null) {
            return
        }
        val origin = captureCurrentLocation() ?: return
        jumpBackState = JumpBackState(origin = origin, reason = reason, label = label)
    }

    fun closeSearchResults() {
        clearSearchResultsMode()
        if (jumpBackState?.reason == ReaderJumpReason.SEARCH) {
            clearJumpBackState()
        }
    }

    suspend fun jumpBackTo(state: JumpBackState) {
        val origin = state.origin
        if (origin.subtitleId != subtitle.id) {
            clearSearchResultsMode()
            clearJumpBackState()
            onNavigateToReaderLocation(origin)
            return
        }

        when (val anchor = origin.anchor) {
            is ReaderAnchor.Study -> {
                if (readerMode != ReaderMode.STUDY) {
                    readerMode = ReaderMode.STUDY
                    repeat(10) {
                        val textView = studyTextView
                        if (textView != null && textView.isLaidOut) {
                            val targetScroll = textView.verticalOffsetForSelection(anchor.anchorStart)
                                .coerceIn(0, studyScrollState.maxValue)
                            studyScrollState.animateScrollTo(targetScroll)
                            clearSearchResultsMode()
                            clearJumpBackState()
                            return
                        }
                        delay(16)
                    }
                    return
                }

                val textView = studyTextView ?: return
                val targetScroll = textView.verticalOffsetForSelection(anchor.anchorStart)
                    .coerceIn(0, studyScrollState.maxValue)
                studyScrollState.animateScrollTo(targetScroll)
            }

            is ReaderAnchor.OriginalFallback -> {
                if (readerMode != ReaderMode.ORIGINAL) {
                    readerMode = ReaderMode.ORIGINAL
                }
                repeat(10) {
                    val textView = originalSelectionCoordinator.textViews[-1]
                    if (textView != null && textView.isShown) {
                        val targetScroll = textView.verticalOffsetForSelection(anchor.textOffset)
                            .coerceIn(0, originalFallbackScrollState.maxValue)
                        originalFallbackScrollState.animateScrollTo(targetScroll)
                        clearSearchResultsMode()
                        clearJumpBackState()
                        return
                    }
                    delay(16)
                }
                return
            }

            is ReaderAnchor.OriginalSegment -> {
                if (readerMode != ReaderMode.ORIGINAL) {
                    readerMode = ReaderMode.ORIGINAL
                }
                originalListState.animateScrollToItem(anchor.segmentIndex)
            }
        }

        clearSearchResultsMode()
        clearJumpBackState()
    }

    fun dismissHighlightNoteDialog() {
        showHighlightNoteDialog = false
        highlightNoteDraft = ""
        highlightNoteTarget = null
        highlightNoteSelectionRange = null
    }

    fun dismissBookmarkDialog() {
        showBookmarkDialog = false
        bookmarkTitleDraft = ""
        editingBookmark = null
        pendingBookmarkAnchorStart = null
        pendingBookmarkFallbackTitle = ""
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

    fun openBookmarkDialog() {
        val textView = studyTextView
        if (textView != null) {
            val anchorStart = textView.topVisibleLineAnchor(studyScrollState.value) ?: return
            editingBookmark = null
            pendingBookmarkAnchorStart = anchorStart
            pendingBookmarkFallbackTitle = textView.lineTextForOffset(anchorStart)
            bookmarkTitleDraft = ""
            showBookmarkDialog = true
        } else {
            val wv = webViewStudyRef ?: return
            with(WebViewReaderJs) {
                wv.getCharOffsetAtTop { anchorStart ->
                    editingBookmark = null
                    pendingBookmarkAnchorStart = anchorStart
                    pendingBookmarkFallbackTitle = lineTextForOffset(uiState.content, anchorStart)
                    bookmarkTitleDraft = ""
                    showBookmarkDialog = true
                }
            }
        }
    }

    fun openBookmarkDialog(bookmark: BookmarkEntity) {
        val textView = studyTextView
        val fallbackTitle: String
        if (textView != null) {
            fallbackTitle = textView.lineTextForOffset(bookmark.anchorStart)
        } else {
            fallbackTitle = lineTextForOffset(uiState.content, bookmark.anchorStart)
        }
        editingBookmark = bookmark
        pendingBookmarkAnchorStart = bookmark.anchorStart
        pendingBookmarkFallbackTitle = fallbackTitle
        bookmarkTitleDraft = bookmark.title
        showBookmarkDialog = true
    }

    fun activateSearchResultsMode(mode: SearchResultsMode) {
        registerProgrammaticJump(
            reason = ReaderJumpReason.SEARCH,
            replaceExisting = jumpBackState?.reason != ReaderJumpReason.SEARCH
        )
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
            emptyQueryMessage = context.getString(R.string.reader_enter_regex),
            invalidRegexMessage = context.getString(R.string.invalid_regex),
            excerptEllipsis = context.getString(R.string.reader_excerpt_ellipsis),
            setHasSearched = { hasSearchedFind = it },
            setErrorMessage = { findErrorMessage = it },
            setFindResults = { findResults = it },
            setOriginalSegmentResults = { originalSegmentFindResults = it }
        )
    }

    fun startInteractiveReplace() {
        if (findText.isBlank()) {
            findReplaceErrorMessage = context.getString(R.string.reader_enter_regex)
            return
        }
        val regex = compileFindRegex(
            query = findText,
            isCaseSensitive = isCaseSensitive,
            emptyQueryMessage = context.getString(R.string.reader_enter_regex),
            invalidRegexMessage = context.getString(R.string.invalid_regex)
        ).getOrElse {
            findReplaceErrorMessage = it.message ?: context.getString(R.string.invalid_regex)
            return
        }
        val sourceText = currentText()
        val matches = findRegexMatches(
            text = sourceText,
            regex = regex,
            excerptEllipsis = context.getString(R.string.reader_excerpt_ellipsis)
        )
        if (matches.isEmpty()) {
            findReplaceErrorMessage = context.getString(R.string.reader_no_results)
            return
        }
        interactiveReplaceState = InteractiveReplaceState(
            findText = findText,
            replaceText = replaceText,
            isCaseSensitive = isCaseSensitive
        )
        resetFindReplaceDialogState()
        showFindReplaceDialog = false
        selectionRange = null
        activeHighlight = null
        dismissHighlightNoteDialog()
        studyTextView?.clearSelection()
        originalSelectionCoordinator.clearAllSelections()
        activateSearchResultsMode(
            SearchResultsMode.Study(results = matches, activeIndex = 0)
        )
    }

    fun replaceCurrentOccurrence() {
        val currentMode = searchResultsMode as? SearchResultsMode.Study ?: return
        val state = interactiveReplaceState ?: return
        val result = currentMode.results.getOrNull(currentMode.activeIndex) ?: return
        val sourceText = currentText()
        val updatedText = replaceSingleMatch(
            text = sourceText,
            start = result.start,
            end = result.end,
            replacement = state.replaceText
        )
        applyTextUpdate(updatedText)
        val newTotal = state.totalReplacements + 1
        val updatedRegex = compileFindRegex(
            query = state.findText,
            isCaseSensitive = state.isCaseSensitive,
            emptyQueryMessage = context.getString(R.string.reader_enter_regex),
            invalidRegexMessage = context.getString(R.string.invalid_regex)
        ).getOrNull() ?: run {
            clearSearchResultsMode()
            return
        }
        val newMatches = findRegexMatches(
            text = updatedText,
            regex = updatedRegex,
            excerptEllipsis = context.getString(R.string.reader_excerpt_ellipsis)
        )
        if (newMatches.isEmpty()) {
            clearSearchResultsMode()
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.reader_replace_done, newTotal)
                )
            }
            return
        }
        val nextIndex = currentMode.activeIndex.coerceAtMost(newMatches.size - 1)
        interactiveReplaceState = state.copy(totalReplacements = newTotal)
        searchResultsMode = SearchResultsMode.Study(results = newMatches, activeIndex = nextIndex)
        pendingFindSelection = activePendingFindSelection(searchResultsMode)
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
                dismissBookmarkDialog()
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
        when (action) {
            is PendingAction.ShowWebViewEditUnavailable -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.reader_webview_edit_unavailable))
                }
                return
            }
            else -> {}
        }
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
        val wv = if (readerMode == ReaderMode.STUDY) webViewStudyRef else webViewOriginalRef
        if (wv != null) {
            val currentScroll = webViewScrollY
            val viewportHeight = webViewViewportHeight
            val target = if (isForward) {
                currentScroll + viewportHeight
            } else {
                currentScroll - viewportHeight
            }
            with(WebViewReaderJs) {
                wv.scrollToOffset(target.coerceAtLeast(0))
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

    LaunchedEffect(
        pendingInitialReaderLocation,
        studyTextView,
        studyScrollState.maxValue,
        originalFallbackScrollState.maxValue,
        originalListState,
        readerMode
    ) {
        val location = pendingInitialReaderLocation ?: return@LaunchedEffect
        when (val anchor = location.anchor) {
            is ReaderAnchor.Study -> {
                if (readerMode != ReaderMode.STUDY) {
                    readerMode = ReaderMode.STUDY
                    return@LaunchedEffect
                }

                val textView = studyTextView ?: return@LaunchedEffect
                if (!textView.isLaidOut) return@LaunchedEffect
                val targetScroll = textView.verticalOffsetForSelection(anchor.anchorStart)
                    .coerceIn(0, studyScrollState.maxValue)
                studyScrollState.scrollTo(targetScroll)
            }

            is ReaderAnchor.OriginalFallback -> {
                if (readerMode != ReaderMode.ORIGINAL) {
                    readerMode = ReaderMode.ORIGINAL
                    return@LaunchedEffect
                }

                repeat(10) {
                    val textView = originalSelectionCoordinator.textViews[-1]
                    if (textView != null && textView.isShown) {
                        val targetScroll = textView.verticalOffsetForSelection(anchor.textOffset)
                            .coerceIn(0, originalFallbackScrollState.maxValue)
                        originalFallbackScrollState.scrollTo(targetScroll)
                        pendingInitialReaderLocation = null
                        return@LaunchedEffect
                    }
                    delay(16)
                }
                return@LaunchedEffect
            }

            is ReaderAnchor.OriginalSegment -> {
                if (readerMode != ReaderMode.ORIGINAL) {
                    readerMode = ReaderMode.ORIGINAL
                    return@LaunchedEffect
                }

                originalListState.scrollToItem(anchor.segmentIndex)
            }
        }

        selectionRange = null
        activeHighlight = null
        suppressSelectionToolbar = false
        dismissHighlightNoteDialog()
        dismissBookmarkDialog()
        studyTextView?.clearSelection()
        originalSelectionCoordinator.clearAllSelections()
        clearSearchResultsMode()
        isUiVisible = false
        pendingInitialReaderLocation = null
    }

    LaunchedEffect(
        pendingInitialHighlightRange,
        studyTextView,
        studyScrollState.maxValue,
        uiState.highlights,
        readerMode
    ) {
        if (pendingInitialReaderLocation != null) return@LaunchedEffect
        val targetRange = pendingInitialHighlightRange ?: return@LaunchedEffect
        if (readerMode != ReaderMode.STUDY) {
            readerMode = ReaderMode.STUDY
            return@LaunchedEffect
        }

        val textView = studyTextView ?: return@LaunchedEffect
        if (!textView.isLaidOut) return@LaunchedEffect
        val targetHighlight = uiState.highlights.firstOrNull { highlight ->
            highlight.start == targetRange.start && highlight.end == targetRange.end
        } ?: return@LaunchedEffect

        val targetScroll = textView.verticalOffsetForSelection(targetHighlight.start)
            .coerceIn(0, studyScrollState.maxValue)
        studyScrollState.scrollTo(targetScroll)
        selectionRange = null
        activeHighlight = targetHighlight
        suppressSelectionToolbar = true
        dismissHighlightNoteDialog()
        dismissBookmarkDialog()
        studyTextView?.clearSelection()
        clearSearchResultsMode()
        isUiVisible = false
        pendingInitialHighlightRange = null
    }

    LaunchedEffect(
        pendingInitialBookmarkStart,
        studyTextView,
        studyScrollState.maxValue,
        readerMode
    ) {
        if (pendingInitialReaderLocation != null) return@LaunchedEffect
        val bookmarkStart = pendingInitialBookmarkStart ?: return@LaunchedEffect
        if (readerMode != ReaderMode.STUDY) {
            readerMode = ReaderMode.STUDY
            return@LaunchedEffect
        }

        val textView = studyTextView ?: return@LaunchedEffect
        if (!textView.isLaidOut) return@LaunchedEffect
        val targetScroll = textView.verticalOffsetForBookmark(bookmarkStart)
            .coerceIn(0, studyScrollState.maxValue)
        studyScrollState.scrollTo(targetScroll)
        selectionRange = null
        activeHighlight = null
        suppressSelectionToolbar = false
        dismissHighlightNoteDialog()
        dismissBookmarkDialog()
        studyTextView?.clearSelection()
        clearSearchResultsMode()
        isUiVisible = false
        pendingInitialBookmarkStart = null
    }

    fun handleReaderTap(tapPosition: ReaderTapPosition) {
        if (isEditing) return
        if (selectionRange != null || activeHighlight != null) return
        if (readerMode == ReaderMode.STUDY && isBookmarkCornerTap(tapPosition)) {
            openBookmarkDialog()
            return
        }

        when (classifyReaderTapZone(tapPosition)) {
            ReaderTapZone.PREVIOUS_PAGE -> {
                clearJumpBackState()
                coroutineScope.launch { scrollOnePage(isForward = false) }
            }

            ReaderTapZone.TOGGLE_UI -> {
                isUiVisible = !isUiVisible
            }

            ReaderTapZone.NEXT_PAGE -> {
                clearJumpBackState()
                coroutineScope.launch { scrollOnePage(isForward = true) }
            }
        }
    }

    fun openOriginalTimestamp(videoId: String, timestampMillis: Long) {
        val url = buildTimestampedYoutubeUrl(videoId = videoId, timestampMillis = timestampMillis)
        if (url.isBlank()) {
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    ReaderCoreEffects(
        subtitleId = subtitle.id,
        subtitleLastStudyScroll = uiState.lastStudyScroll,
        subtitleLastTimestamp = uiState.lastTimestamp,
        hasInitialNavigationTarget = hasInitialNavigationTarget,
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
            dismissBookmarkDialog()
            studyTextView?.clearSelection()
            originalSelectionCoordinator.clearAllSelections()
            if (interactiveReplaceState == null) {
                clearSearchResultsMode()
            }
        },
        onResetAiDialogs = {
            showAiPreviewDialog = false
            showAiErrorDialog = false
            dismissBookmarkDialog()
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
            dismissBookmarkDialog()
            studyTextView?.clearSelection()
            originalSelectionCoordinator.clearAllSelections()
            clearSearchResultsMode()
            clearJumpBackState()
        },
        onShowAiPreviewDialog = { showAiPreviewDialog = true },
        onShowAiErrorDialog = { showAiErrorDialog = true },
        onReaderModeChangedToOriginal = {
            selectionRange = null
            activeHighlight = null
            dismissHighlightNoteDialog()
            dismissBookmarkDialog()
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
            val textView = studyTextView
            if (textView != null) {
                val targetScroll = textView.verticalOffsetForSelection(selection.start)
                    .coerceIn(0, studyScrollState.maxValue)
                studyScrollState.animateScrollTo(targetScroll)
            } else {
                val wv = webViewStudyRef
                if (wv != null) {
                    with(WebViewReaderJs) { wv.scrollToCharOffset(selection.start) }
                }
            }
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
        if (searchResultsMode != null && jumpBackState?.reason == ReaderJumpReason.SEARCH) {
            coroutineScope.launch { jumpBackTo(jumpBackState ?: return@launch) }
        } else if (searchResultsMode != null) {
            closeSearchResults()
        } else if (isUiVisible) {
            isUiVisible = false
        } else if (jumpBackState != null) {
            coroutineScope.launch { jumpBackTo(jumpBackState ?: return@launch) }
        } else {
            requestAction(PendingAction.ExitScreen)
        }
    }

    val showSelectionToolbar = readerMode == ReaderMode.STUDY &&
        !isEditing &&
        searchResultsMode == null &&
        !suppressSelectionToolbar &&
        (selectionRange != null || activeHighlight != null)
    val showSearchResultsToolbar = !isEditing && searchResultsMode != null
    val showJumpBackToolbar = !isEditing && searchResultsMode == null && jumpBackState != null
    Log.d(TAG, "showJumpBackToolbar=$showJumpBackToolbar isEditing=$isEditing searchResultsMode=$searchResultsMode jumpBackState=$jumpBackState")
    val topContentPadding = 0.dp
    val bottomContentPadding = 0.dp

    val fullscreenProgressPercent by remember(
        webViewScrollY,
        webViewTotalHeight,
        webViewViewportHeight
    ) {
        derivedStateOf {
            val scrollY = webViewScrollY
            val totalHeight = webViewTotalHeight
            val viewportHeight = webViewViewportHeight
            if (totalHeight <= viewportHeight) 0
            else ((scrollY.toFloat() / (totalHeight - viewportHeight)) * 100).toInt().coerceIn(0, 100)
        }
    }

    val fullscreenPageProgress by remember(
        webViewTotalHeight,
        webViewViewportHeight,
        webViewScrollY
    ) {
        derivedStateOf {
            val scrollY = webViewScrollY
            val totalHeight = webViewTotalHeight
            val viewportHeight = webViewViewportHeight
            if (viewportHeight <= 0 || totalHeight <= viewportHeight) {
                PageProgress(currentPage = 1, totalPages = 1)
            } else {
                val totalPages = ((totalHeight + viewportHeight - 1) / viewportHeight).coerceAtLeast(1)
                val currentPage = ((scrollY + viewportHeight) / viewportHeight).coerceIn(1, totalPages)
                PageProgress(currentPage = currentPage, totalPages = totalPages)
            }
        }
    }

    LaunchedEffect(fullscreenProgressPercent, fullscreenPageProgress) {
        viewModel.updateReadingProgress(
            percent = fullscreenProgressPercent,
            currentPage = fullscreenPageProgress.currentPage,
            totalPages = fullscreenPageProgress.totalPages
        )
    }

    val webViewAnnotationScrollOffset = pendingInitialHighlightRange?.start
        ?: pendingInitialBookmarkStart

    val webViewAnnotationNavigated by rememberUpdatedState(newValue = {
        val pendingRange = pendingInitialHighlightRange
        if (pendingRange != null) {
            val targetHighlight = uiState.highlights.firstOrNull {
                it.start == pendingRange.start && it.end == pendingRange.end
            }
            if (targetHighlight != null) {
                activeHighlight = targetHighlight
                suppressSelectionToolbar = true
            }
            pendingInitialHighlightRange = null
        } else if (pendingInitialBookmarkStart != null) {
            pendingInitialBookmarkStart = null
        }
        selectionRange = null
        dismissHighlightNoteDialog()
        dismissBookmarkDialog()
        isUiVisible = false
    })

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
        bookmarks = uiState.bookmarks,
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
        onOriginalTimestampTap = { timestampMillis ->
            openOriginalTimestamp(subtitle.videoId, timestampMillis)
        },
        onSelectionRangeChanged = { start, end ->
            val normalizedStart = minOf(start, end)
            val normalizedEnd = maxOf(start, end)
            selectionRange = if (normalizedStart >= 0 && normalizedStart < normalizedEnd) {
                suppressSelectionToolbar = false
                activeHighlight = null
                dismissHighlightNoteDialog()
                clearSearchResultsMode()
                SelectionRange(normalizedStart, normalizedEnd)
            } else {
                null
            }
        },
        onHighlightTapped = { tappedHighlight ->
            suppressSelectionToolbar = false
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
        onOriginalFallbackViewportChanged = {
            originalFallbackViewportHeightPx = it
            if (it > stableOriginalFallbackViewportHeightPx) stableOriginalFallbackViewportHeightPx = it
        },
        onStudyViewportChanged = {
            studyViewportHeightPx = it
            if (it > stableStudyViewportHeightPx) stableStudyViewportHeightPx = it
        },
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
            context.startActivity(
                Intent.createChooser(shareIntent, context.getString(R.string.share_text_title))
            )
        },
        onReplaceWithClipboard = {
            val clipboardText = clipboardManager.getText()?.text?.toString().orEmpty()
            if (clipboardText.isBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.reader_clipboard_empty)
                    )
                }
            } else {
                applyTextUpdate(clipboardText)
            }
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
        onShowVideoNotes = {
            subtitle.videoId.takeIf { it.isNotBlank() }?.let { videoId ->
                val currentLocation = captureCurrentLocation()
                Log.d(TAG, "onShowVideoNotes: videoId=$videoId currentLocation=$currentLocation studyTextView=${studyTextView != null}")
                onOpenVideoNotes(
                    videoId,
                    currentLocation?.let {
                        JumpBackState(origin = it, reason = ReaderJumpReason.ANNOTATION)
                    }
                )
            }
        },
        onShowFindAndReplace = {
            clearSearchResultsMode()
            resetFindReplaceDialogState()
            showFindReplaceDialog = true
        },
        onStartExternalAiCleaning = { sourceText ->
            val shareText = buildExternalAiCleaningShareText(
                prompt = userPreferencesRepository.getAiPrompt(),
                studyText = sourceText
            )
            if (shareText.isBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.ai_cleaning_external_nothing_to_share)
                    )
                }
            } else {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(
                    Intent.createChooser(
                        shareIntent,
                        context.getString(R.string.ai_cleaning_external_share_title)
                    )
                )
            }
        },
        onStartAiCleaning = { sourceText ->
            if (sourceText.isBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.nothing_to_clean))
                }
            } else {
                coroutineScope.launch { startAiCleaningJob(sourceText) }
            }
        },
        onRequestNotificationPermission = { sourceText ->
            if (sourceText.isBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.nothing_to_clean))
                }
            } else {
                pendingAiCleaningSourceText = sourceText
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        hasTimestampedSegments = originalSegments.isNotEmpty(),
        onShowJumpToTime = { showJumpToTimeDialog = true },
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
        selectedColor = activeHighlight?.color ?: HighlightColor.RED,
        showSearchInOriginal = readerMode == ReaderMode.STUDY && !isEditing,
        onSearchInOriginal = {
            val range = selectionRange ?: return@ReaderScreenMainLayer
            val query = uiState.content.substring(range.start, range.end)
            val results = findLiteralInSegments(
                query = query,
                segments = originalSegments,
                excerptEllipsis = context.getString(R.string.reader_excerpt_ellipsis)
            )
            searchInOriginalQuery = query
            searchInOriginalResults = results
            showSearchInOriginalDialog = true
            selectionRange = null
            studyTextView?.clearSelection()
        },
        onBookmarkTapped = { bookmark ->
            suppressSelectionToolbar = false
            clearSearchResultsMode()
            dismissHighlightNoteDialog()
            activeHighlight = null
            selectionRange = null
            studyTextView?.clearSelection()
            openBookmarkDialog(bookmark)
        },
        showSearchResultsToolbar = showSearchResultsToolbar,
        showJumpBackToolbar = showJumpBackToolbar,
        searchResultsCurrentIndex = (searchResultsMode?.activeIndex ?: 0) + 1,
        searchResultsTotalCount = searchResultsMode?.totalResults ?: 0,
        canNavigateToPreviousSearchResult = canNavigateToPreviousSearchResult(searchResultsMode),
        canNavigateToNextSearchResult = canNavigateToNextSearchResult(searchResultsMode),
        onReturnToSearchOrigin = jumpBackState
            ?.takeIf { it.reason == ReaderJumpReason.SEARCH }
            ?.let { state -> { coroutineScope.launch { jumpBackTo(state) } } },
        onNavigateToPreviousSearchResult = { moveSearchResultsBackward() },
        onNavigateToNextSearchResult = { moveSearchResultsForward() },
        onCloseSearchResults = { closeSearchResults() },
        onReplaceCurrent = interactiveReplaceState?.let { { replaceCurrentOccurrence() } },
        onJumpBack = {
            jumpBackState?.let { state ->
                coroutineScope.launch { jumpBackTo(state) }
            }
        },
        onUserDrag = { clearJumpBackState() },
        fullscreenProgressPercent = fullscreenProgressPercent,
        fullscreenPageProgress = fullscreenPageProgress,
        showProgressIndicator = showProgressIndicator,
        showBrightnessIndicator = showBrightnessIndicator,
        brightnessIndicatorPercent = brightnessIndicatorPercent,
        snackbarHostState = snackbarHostState,
        initialScrollPercent = uiState.lastStudyScroll,
        annotationScrollOffset = webViewAnnotationScrollOffset,
        onAnnotationNavigated = { webViewAnnotationNavigated() },
        useWebView = true,
        onWebViewStudyReady = { wv -> webViewStudyRef = wv },
        onWebViewStudyDestroyed = { webViewStudyRef = null },
        onWebViewOriginalReady = { wv -> webViewOriginalRef = wv },
        onWebViewOriginalDestroyed = { webViewOriginalRef = null },
        onWebViewScrollProgress = { scrollY, totalHeight, viewportHeight ->
            webViewScrollY = scrollY
            webViewTotalHeight = totalHeight
            webViewViewportHeight = viewportHeight
            showAndScheduleHideProgressIndicator()
        },
        onWebViewVisibleCharOffset = { offset ->
            webViewCharOffsetAtTop = offset
        },
        onWebViewClearSelection = {
            webViewStudyRef?.let { wv ->
                with(WebViewReaderJs) { wv.clearSelection() }
            }
        },
        webViewStudyRef = webViewStudyRef,
        webViewScrollProgress = if (webViewTotalHeight > webViewViewportHeight) {
            webViewScrollY.toFloat() / (webViewTotalHeight - webViewViewportHeight).toFloat()
        } else 0f,
        webViewCanScroll = webViewTotalHeight > webViewViewportHeight,
        onWebViewScrollToProgress = { progress ->
            val activeWebView = if (readerMode == ReaderMode.STUDY) webViewStudyRef else webViewOriginalRef
            activeWebView?.let { wv ->
                val maxScroll = (webViewTotalHeight - webViewViewportHeight).coerceAtLeast(1)
                val targetY = (progress * maxScroll).toInt()
                with(WebViewReaderJs) { wv.scrollToOffset(targetY) }
            }
        }
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
                isCaseSensitive = isCaseSensitive,
                emptyQueryMessage = context.getString(R.string.reader_enter_regex),
                invalidRegexMessage = context.getString(R.string.invalid_regex)
            )
            if (replaceResult.isFailure) {
                findReplaceErrorMessage = replaceResult.exceptionOrNull()?.message
                    ?: context.getString(R.string.invalid_regex)
            } else {
                val updated = replaceResult.getOrThrow()
                applyTextUpdate(updated.updatedText)
                resetFindReplaceDialogState()
                showFindReplaceDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        context.resources.getQuantityString(
                            R.plurals.reader_replace_occurrences,
                            updated.replacementCount,
                            updated.replacementCount
                        )
                    )
                }
            }
        },
        showInteractiveReplace = readerMode == ReaderMode.STUDY,
        onInteractiveReplace = { startInteractiveReplace() },
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
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.note_saved))
                    }
                }

                existingHighlight != null -> {
                    viewModel.deleteHighlightNote(existingHighlight)
                    activeHighlight = existingHighlight.copy(note = null)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.note_removed))
                    }
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
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.note_saved))
                        }
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
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.note_removed))
                }
            }
            dismissHighlightNoteDialog()
        },
        showBookmarkDialog = showBookmarkDialog,
        isEditingBookmark = editingBookmark != null,
        bookmarkTitleText = bookmarkTitleDraft,
        onBookmarkTitleTextChange = { bookmarkTitleDraft = it },
        onSaveBookmark = {
            val anchorStart = pendingBookmarkAnchorStart
            val resolvedTitle = bookmarkTitleDraft.trim().ifBlank { pendingBookmarkFallbackTitle.trim() }
            if (anchorStart != null && resolvedTitle.isNotBlank()) {
                viewModel.saveBookmark(anchorStart = anchorStart, title = resolvedTitle)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.bookmark_saved))
                }
            }
            dismissBookmarkDialog()
        },
        onDismissBookmark = { dismissBookmarkDialog() },
        onDeleteBookmark = {
            pendingBookmarkAnchorStart?.let { anchorStart ->
                viewModel.deleteBookmark(anchorStart)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.bookmark_deleted))
                }
            }
            dismissBookmarkDialog()
        },
        showJumpToTimeDialog = showJumpToTimeDialog,
        maxTimeMillis = originalSegments.lastOrNull()?.endTime ?: 0L,
        onJumpToTime = { targetMillis ->
            showJumpToTimeDialog = false
            val index = originalSegments.indexOfLast { it.startTime <= targetMillis }
                .coerceAtLeast(0)
            registerProgrammaticJump(ReaderJumpReason.TIME_JUMP)
            coroutineScope.launch { originalListState.animateScrollToItem(index) }
        },
        onJumpToStart = {
            showJumpToTimeDialog = false
            if (originalSegments.isNotEmpty()) {
                registerProgrammaticJump(ReaderJumpReason.TIME_JUMP)
                coroutineScope.launch { originalListState.animateScrollToItem(0) }
            }
        },
        onJumpToEnd = {
            showJumpToTimeDialog = false
            if (originalSegments.isNotEmpty()) {
                registerProgrammaticJump(ReaderJumpReason.TIME_JUMP)
                coroutineScope.launch {
                    originalListState.animateScrollToItem(originalSegments.lastIndex)
                }
            }
        },
        onDismissJumpToTime = { showJumpToTimeDialog = false },
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope
    )

    if (showSearchInOriginalDialog) {
        SearchInOriginalDialog(
            query = searchInOriginalQuery,
            results = searchInOriginalResults,
            totalSegments = originalSegments.size,
            onSelectResult = { result ->
                showSearchInOriginalDialog = false
                registerProgrammaticJump(ReaderJumpReason.SEARCH)
                readerMode = ReaderMode.ORIGINAL
                coroutineScope.launch {
                    originalListState.scrollToItem(result.segmentIndex)
                }
            },
            onOpenInYoutube = { result ->
                openOriginalTimestamp(subtitle.videoId, result.startTime)
            },
            onDismiss = {
                showSearchInOriginalDialog = false
            }
        )
    }
}

private fun lineTextForOffset(text: String, offset: Int): String {
    if (text.isEmpty()) return ""
    val safeOffset = offset.coerceIn(0, text.lastIndex)
    val lineStart = text.lastIndexOf('\n', safeOffset - 1).let { if (it == -1) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', safeOffset).let { if (it == -1) text.length else it }
    return text.substring(lineStart, lineEnd).replace(Regex("\\s+"), " ").trim()
}
