package com.deedeedev.ytreader.ui.reader

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deedeedev.ytreader.data.AiCleaningRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.domain.SubtitleParser
import com.deedeedev.ytreader.domain.SubtitleSegment
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Color as AndroidColor
import kotlin.math.ceil

private enum class ReaderMode {
    ORIGINAL,
    STUDY
}

private sealed interface PendingAction {
    data object ExitScreen : PendingAction
    data object ExitEditing : PendingAction
    data class SwitchMode(val targetMode: ReaderMode) : PendingAction
}

private data class SelectionRange(
    val start: Int,
    val end: Int
)

private data class PageProgress(
    val currentPage: Int,
    val totalPages: Int
)

private sealed interface PendingFindSelection {
    data class Study(val start: Int, val end: Int) : PendingFindSelection
    data class OriginalSegment(val segmentIndex: Int, val start: Int, val end: Int) : PendingFindSelection
    data class OriginalFallback(val start: Int, val end: Int) : PendingFindSelection
}

private class OriginalSelectionCoordinator {
    val textViews = mutableMapOf<Int, SelectableHighlightTextView>()
    var activeOwner: Int? = null

    fun clearAllSelections() {
        textViews.values.forEach { it.clearSelection() }
        activeOwner = null
    }
}

private const val READER_TOP_BAR_TAG = "reader_top_bar"
private const val READER_EDIT_TEXT_FIELD_TAG = "reader_edit_text_field"
private const val READER_SELECTION_TOOLBAR_TAG = "reader_selection_toolbar"
private const val READER_FIND_DIALOG_TAG = "reader_find_dialog"
private const val READER_FIND_INPUT_TAG = "reader_find_input"
private const val READER_FIND_RESULTS_TAG = "reader_find_results"
private const val READER_FIND_REPLACE_INPUT_TAG = "reader_find_replace_input"
private const val READER_FIND_REPLACE_REPLACEMENT_TAG = "reader_find_replace_replacement"
private val READER_BOTTOM_BAR_HEIGHT = 80.dp

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    subtitleId: Long,
    subtitleDao: SubtitleDao,
    userPreferencesRepository: UserPreferencesRepository,
    onChromeReady: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ReaderViewModel = viewModel(
        key = "Reader_$subtitleId",
        factory = ReaderViewModel.provideFactory(
            context.applicationContext,
            subtitleDao,
            userPreferencesRepository,
            subtitleId
        )
    )
    val uiState by viewModel.uiState.collectAsState()
    val subtitle = uiState.subtitle

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
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val originalListState = rememberLazyListState()
    val studyScrollState = rememberScrollState()
    val originalFallbackScrollState = rememberScrollState()
    val lineHeightSp = fontSize * uiState.lineHeightMultiplier
    val readerTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val readerBackgroundColor = Color.Transparent.toArgb()
    val activity = remember(context) { context.findActivity() }

    var readerMode by rememberSaveable { mutableStateOf(ReaderMode.STUDY) }
    var showTimestamps by rememberSaveable { mutableStateOf(false) }
    var isUiVisible by rememberSaveable { mutableStateOf(false) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    // Large transcripts can exceed the saved-instance-state Binder limit if persisted via rememberSaveable.
    var editText by remember(subtitle.id) { mutableStateOf(uiState.content) }
    var showEmptyDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showFindDialog by remember { mutableStateOf(false) }
    var showFindReplaceDialog by remember { mutableStateOf(false) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    var findResults by remember { mutableStateOf<List<ReaderFindResult>>(emptyList()) }
    var originalSegmentFindResults by remember { mutableStateOf<List<OriginalSegmentFindResult>>(emptyList()) }
    var findErrorMessage by remember { mutableStateOf<String?>(null) }
    var hasSearchedFind by remember { mutableStateOf(false) }
    var findIsCaseSensitive by rememberSaveable { mutableStateOf(false) }
    var pendingFindSelection by remember { mutableStateOf<PendingFindSelection?>(null) }
    var findReplaceErrorMessage by remember { mutableStateOf<String?>(null) }
    var findText by rememberSaveable { mutableStateOf("") }
    var replaceText by rememberSaveable { mutableStateOf("") }
    var isCaseSensitive by rememberSaveable { mutableStateOf(false) }
    var showAiPreviewDialog by remember { mutableStateOf(false) }
    var showAiErrorDialog by remember { mutableStateOf(false) }
    var selectionRange by remember { mutableStateOf<SelectionRange?>(null) }
    var activeHighlight by remember { mutableStateOf<TextHighlight?>(null) }
    var studyTextView by remember { mutableStateOf<JustifiedStudyTextView?>(null) }
    val originalSelectionCoordinator = remember { OriginalSelectionCoordinator() }
    var lastKnownStudyScroll by rememberSaveable(subtitle.id) {
        mutableStateOf(subtitle.lastStudyScroll)
    }
    var hasRestoredStudyScroll by rememberSaveable(subtitle.id) { mutableStateOf(false) }
    var studyViewportHeightPx by remember { mutableStateOf(0) }
    var originalFallbackViewportHeightPx by remember { mutableStateOf(0) }

    LaunchedEffect(uiState.content, isEditing) {
        if (!isEditing) {
            editText = uiState.content
        }
    }

    LaunchedEffect(uiState.content) {
        selectionRange = null
        activeHighlight = null
        studyTextView?.clearSelection()
        originalSelectionCoordinator.clearAllSelections()
        pendingFindSelection = null
    }

    LaunchedEffect(subtitle.id) {
        showAiPreviewDialog = false
        showAiErrorDialog = false
    }

    LaunchedEffect(subtitle.id) {
        lastKnownStudyScroll = subtitle.lastStudyScroll
        hasRestoredStudyScroll = false
    }

    LaunchedEffect(subtitle.id, studyScrollState.maxValue, hasRestoredStudyScroll) {
        if (hasRestoredStudyScroll) return@LaunchedEffect
        val targetScroll = subtitle.lastStudyScroll.coerceAtLeast(0)
        val maxValue = studyScrollState.maxValue
        if (targetScroll == 0 || maxValue > 0) {
            studyScrollState.scrollTo(targetScroll.coerceIn(0, maxValue))
            hasRestoredStudyScroll = true
        }
    }

    LaunchedEffect(studyScrollState, subtitle.id) {
        snapshotFlow { studyScrollState.value }
            .distinctUntilChanged()
            .collectLatest { scroll ->
                lastKnownStudyScroll = scroll
            }
    }

    val persistReadingProgress by rememberUpdatedState(newValue = {
        val scrollToSave = if (readerMode == ReaderMode.STUDY) {
            studyScrollState.value
        } else {
            lastKnownStudyScroll
        }
        viewModel.updateLastStudyScroll(scrollToSave)
    })

    DisposableEffect(activity, subtitle.id) {
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle
        if (lifecycle == null) {
            onDispose {
                persistReadingProgress()
            }
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
            isUiVisible = true
            selectionRange = null
            activeHighlight = null
            studyTextView?.clearSelection()
            originalSelectionCoordinator.clearAllSelections()
        }
    }

    LaunchedEffect(uiState.pendingAiCleanedText) {
        if (!uiState.pendingAiCleanedText.isNullOrBlank()) {
            showAiPreviewDialog = true
        }
    }

    LaunchedEffect(uiState.aiCleaningErrorLog) {
        if (!uiState.aiCleaningErrorLog.isNullOrBlank()) {
            showAiErrorDialog = true
        }
    }

    LaunchedEffect(readerMode) {
        if (readerMode != ReaderMode.STUDY) {
            selectionRange = null
            activeHighlight = null
            studyTextView?.clearSelection()
        } else {
            originalSelectionCoordinator.clearAllSelections()
        }
        pendingFindSelection = null
    }

    val hasUnsavedChanges = isEditing && editText != uiState.content

    val originalSegments = remember(subtitle.content) {
        SubtitleParser.parseToSegments(subtitle.content)
    }
    val originalFallbackText = uiState.originalParsedText.ifBlank { uiState.content }
    val originalModeText = remember(originalSegments, showTimestamps, originalFallbackText) {
        formatOriginalModeCopyText(originalSegments, showTimestamps, originalFallbackText)
    }

    fun currentText(): String = when (readerMode) {
        ReaderMode.ORIGINAL -> originalModeText
        ReaderMode.STUDY -> if (isEditing) editText else uiState.content
    }

    fun applyTextUpdate(updated: String) {
        if (isEditing) {
            editText = updated
        } else {
            viewModel.updateContent(updated)
        }
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

    fun runFindSearch() {
        hasSearchedFind = true
        val regex = compileFindRegex(
            query = findQuery,
            isCaseSensitive = findIsCaseSensitive
        ).getOrElse { error ->
            findErrorMessage = error.message ?: "Invalid regex."
            findResults = emptyList()
            originalSegmentFindResults = emptyList()
            return
        }

        findErrorMessage = null
        when (readerMode) {
            ReaderMode.STUDY -> {
                findResults = findRegexMatches(currentText(), regex)
                originalSegmentFindResults = emptyList()
            }

            ReaderMode.ORIGINAL -> {
                if (originalSegments.isEmpty()) {
                    findResults = findRegexMatches(originalFallbackText, regex)
                    originalSegmentFindResults = emptyList()
                } else {
                    originalSegmentFindResults = findRegexMatchesInSegments(originalSegments, regex)
                    findResults = emptyList()
                }
            }
        }
    }

    fun runPendingAction(action: PendingAction) {
        when (action) {
            PendingAction.ExitScreen -> {
                persistReadingProgress()
                onBack()
            }
            PendingAction.ExitEditing -> {
                isEditing = false
                editText = uiState.content
                selectionRange = null
                activeHighlight = null
                studyTextView?.clearSelection()
            }
            is PendingAction.SwitchMode -> {
                if (action.targetMode == ReaderMode.ORIGINAL) {
                    isEditing = false
                    editText = uiState.content
                }
                readerMode = action.targetMode
            }
        }
    }

    fun requestAction(action: PendingAction) {
        if (hasUnsavedChanges) {
            pendingAction = action
            showUnsavedDialog = true
        } else {
            runPendingAction(action)
        }
    }

    // Scroll to last position on first load in original mode.
    LaunchedEffect(subtitle.id, originalSegments) {
        val lastTimestamp = subtitle.lastTimestamp
        if (lastTimestamp > 0 && originalSegments.isNotEmpty()) {
            val index = originalSegments.indexOfFirst { it.startTime >= lastTimestamp }
            if (index >= 0) {
                originalListState.scrollToItem(index)
            }
        }
    }

    // Persist current timestamp while browsing original mode.
    LaunchedEffect(originalListState, originalSegments) {
        snapshotFlow { originalListState.firstVisibleItemIndex }
            .collectLatest { index ->
                if (readerMode == ReaderMode.ORIGINAL &&
                    originalSegments.isNotEmpty() &&
                    index < originalSegments.size
                ) {
                    viewModel.updateLastTimestamp(originalSegments[index].startTime)
                }
            }
    }

    BackHandler {
        if (!uiState.isAiCleaning) {
            requestAction(PendingAction.ExitScreen)
        }
    }

    LaunchedEffect(pendingFindSelection, studyTextView, studyScrollState.maxValue) {
        val selection = pendingFindSelection as? PendingFindSelection.Study ?: return@LaunchedEffect
        val textView = studyTextView ?: return@LaunchedEffect
        textView.setSelectionRange(selection.start, selection.end)
        val targetScroll = textView.verticalOffsetForSelection(selection.start)
            .coerceIn(0, studyScrollState.maxValue)
        studyScrollState.animateScrollTo(targetScroll)
        pendingFindSelection = null
    }

    LaunchedEffect(
        pendingFindSelection,
        originalSegments,
        originalFallbackScrollState.maxValue
    ) {
        when (val selection = pendingFindSelection) {
            is PendingFindSelection.OriginalFallback -> {
                val textView = originalSelectionCoordinator.textViews[-1] ?: return@LaunchedEffect
                textView.setSelectionRange(selection.start, selection.end)
                val targetScroll = textView.verticalOffsetForSelection(selection.start)
                    .coerceIn(0, originalFallbackScrollState.maxValue)
                originalFallbackScrollState.animateScrollTo(targetScroll)
                pendingFindSelection = null
            }

            is PendingFindSelection.OriginalSegment -> {
                originalListState.animateScrollToItem(selection.segmentIndex)
                repeat(10) {
                    val textView = originalSelectionCoordinator.textViews[selection.segmentIndex]
                    if (textView != null && textView.isShown) {
                        textView.setSelectionRange(selection.start, selection.end)
                        pendingFindSelection = null
                        return@LaunchedEffect
                    }
                    delay(16)
                }
                pendingFindSelection = null
            }

            is PendingFindSelection.Study,
            null -> Unit
        }
    }

    val showSelectionToolbar = readerMode == ReaderMode.STUDY &&
        !isEditing &&
        (selectionRange != null || activeHighlight != null)
    val topContentPadding by animateDpAsState(
        targetValue = if (isUiVisible) TopAppBarDefaults.TopAppBarExpandedHeight else 0.dp,
        label = "readerTopContentPadding"
    )
    val bottomContentPadding by animateDpAsState(
        targetValue = if (isUiVisible) READER_BOTTOM_BAR_HEIGHT else 0.dp,
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

    DisposableEffect(activity, view, isUiVisible, isEditing) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        if (insetsController != null) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isUiVisible || isEditing) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (readerMode == ReaderMode.ORIGINAL) {
            if (originalSegments.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = topContentPadding,
                            bottom = bottomContentPadding
                        )
                        .onUnconsumedTap { isUiVisible = !isUiVisible }
                        .onSizeChanged { originalFallbackViewportHeightPx = it.height }
                        .verticalScroll(originalFallbackScrollState)
                ) {
                    AndroidView<SelectableHighlightTextView>(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { context: android.content.Context ->
                            SelectableHighlightTextView(context).apply {
                                originalSelectionCoordinator.textViews[-1] = this
                                textSize = fontSize
                                setLineSpacing(0f, uiState.lineHeightMultiplier)
                                applyTypeface(uiState.fontFamily)
                                setReadableColors(
                                    textColor = readerTextColor,
                                    backgroundColor = readerBackgroundColor
                                )
                                onHighlightTappedListener = null
                                onTextTapListener = { tapOutcome ->
                                    if (tapOutcome == TextTapOutcome.PLAIN_TEXT &&
                                        originalSelectionCoordinator.activeOwner == null
                                    ) {
                                        isUiVisible = !isUiVisible
                                    }
                                }
                                onSelectionChangedListener = { start, end ->
                                    val hasSelection = minOf(start, end) >= 0 && maxOf(start, end) > minOf(start, end)
                                    originalSelectionCoordinator.activeOwner = if (hasSelection) {
                                        -1
                                    } else if (originalSelectionCoordinator.activeOwner == -1) {
                                        null
                                    } else {
                                        originalSelectionCoordinator.activeOwner
                                    }
                                }
                            }
                        },
                        update = { textView: SelectableHighlightTextView ->
                            originalSelectionCoordinator.textViews[-1] = textView
                            textView.textSize = fontSize
                            textView.setLineSpacing(0f, uiState.lineHeightMultiplier)
                            textView.applyTypeface(uiState.fontFamily)
                            textView.setReadableColors(
                                textColor = readerTextColor,
                                backgroundColor = readerBackgroundColor
                            )
                            textView.onHighlightTappedListener = null
                            textView.onTextTapListener = { tapOutcome ->
                                if (tapOutcome == TextTapOutcome.PLAIN_TEXT &&
                                    originalSelectionCoordinator.activeOwner == null
                                ) {
                                    isUiVisible = !isUiVisible
                                }
                            }
                            textView.onSelectionChangedListener = { start, end ->
                                val hasSelection = minOf(start, end) >= 0 && maxOf(start, end) > minOf(start, end)
                                originalSelectionCoordinator.activeOwner = if (hasSelection) {
                                    -1
                                } else if (originalSelectionCoordinator.activeOwner == -1) {
                                    null
                                } else {
                                    originalSelectionCoordinator.activeOwner
                                }
                            }
                            textView.setContentWithHighlights(
                                content = originalFallbackText,
                                highlights = emptyList(),
                                redColor = 0,
                                blueColor = 0,
                                greenColor = 0,
                                yellowColor = 0
                            )
                        }
                    )
                }
            } else {
                LazyColumn(
                    state = originalListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .onUnconsumedTap { isUiVisible = !isUiVisible }
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(
                        top = topContentPadding,
                        bottom = bottomContentPadding
                    )
                ) {
                    itemsIndexed(originalSegments) { index, segment ->
                        Column(
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                        ) {
                            if (showTimestamps) {
                                Text(
                                    text = formatTime(segment.startTime),
                                    fontSize = (fontSize * 0.8f).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontFamily = fontFamily
                                )
                            }
                            AndroidView<SelectableHighlightTextView>(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { context: android.content.Context ->
                                    SelectableHighlightTextView(context).apply {
                                        originalSelectionCoordinator.textViews[index] = this
                                        textSize = fontSize
                                        setLineSpacing(0f, uiState.lineHeightMultiplier)
                                        applyTypeface(uiState.fontFamily)
                                        setJustificationEnabled(false)
                                        setReadableColors(
                                            textColor = readerTextColor,
                                            backgroundColor = readerBackgroundColor
                                        )
                                        onHighlightTappedListener = null
                                        onTextTapListener = { tapOutcome ->
                                            val selectionOwner = originalSelectionCoordinator.activeOwner
                                            if (tapOutcome == TextTapOutcome.PLAIN_TEXT && selectionOwner != null) {
                                                if (selectionOwner != index) {
                                                    originalSelectionCoordinator.textViews[selectionOwner]?.clearSelection()
                                                    originalSelectionCoordinator.activeOwner = null
                                                }
                                            } else if (tapOutcome == TextTapOutcome.PLAIN_TEXT) {
                                                isUiVisible = !isUiVisible
                                            }
                                        }
                                        onSelectionChangedListener = { start, end ->
                                            val normalizedStart = minOf(start, end)
                                            val normalizedEnd = maxOf(start, end)
                                            val hasSelection = normalizedStart >= 0 && normalizedEnd > normalizedStart
                                            originalSelectionCoordinator.activeOwner = if (hasSelection) {
                                                index
                                            } else if (originalSelectionCoordinator.activeOwner == index) {
                                                null
                                            } else {
                                                originalSelectionCoordinator.activeOwner
                                            }
                                        }
                                    }
                                },
                                update = { textView: SelectableHighlightTextView ->
                                    originalSelectionCoordinator.textViews[index] = textView
                                    textView.textSize = fontSize
                                    textView.setLineSpacing(0f, uiState.lineHeightMultiplier)
                                    textView.applyTypeface(uiState.fontFamily)
                                    textView.setJustificationEnabled(false)
                                    textView.setReadableColors(
                                        textColor = readerTextColor,
                                        backgroundColor = readerBackgroundColor
                                    )
                                    textView.onHighlightTappedListener = null
                                    textView.onTextTapListener = { tapOutcome ->
                                        val selectionOwner = originalSelectionCoordinator.activeOwner
                                        if (tapOutcome == TextTapOutcome.PLAIN_TEXT && selectionOwner != null) {
                                            if (selectionOwner != index) {
                                                originalSelectionCoordinator.textViews[selectionOwner]?.clearSelection()
                                                originalSelectionCoordinator.activeOwner = null
                                            }
                                        } else if (tapOutcome == TextTapOutcome.PLAIN_TEXT) {
                                            isUiVisible = !isUiVisible
                                        }
                                    }
                                    textView.onSelectionChangedListener = { start, end ->
                                        val normalizedStart = minOf(start, end)
                                        val normalizedEnd = maxOf(start, end)
                                        val hasSelection = normalizedStart >= 0 && normalizedEnd > normalizedStart
                                        originalSelectionCoordinator.activeOwner = if (hasSelection) {
                                            index
                                        } else if (originalSelectionCoordinator.activeOwner == index) {
                                            null
                                        } else {
                                            originalSelectionCoordinator.activeOwner
                                        }
                                    }
                                    textView.setContentWithHighlights(
                                        content = segment.text,
                                        highlights = emptyList(),
                                        redColor = 0,
                                        blueColor = 0,
                                        greenColor = 0,
                                        yellowColor = 0
                                    )
                                }
                            )
                        }
                    }
                }
            }
        } else {
            if (isEditing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = topContentPadding,
                            bottom = bottomContentPadding
                        )
                ) {
                    TextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(READER_EDIT_TEXT_FIELD_TAG),
                        textStyle = TextStyle(
                            fontSize = fontSize.sp,
                            lineHeight = lineHeightSp.sp,
                            fontFamily = fontFamily
                        ),
                        colors = TextFieldDefaults.colors()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = topContentPadding,
                            bottom = bottomContentPadding
                        )
                        .onUnconsumedTap { isUiVisible = !isUiVisible }
                        .onSizeChanged { studyViewportHeightPx = it.height }
                        .verticalScroll(studyScrollState)
                ) {
                    AndroidView<JustifiedStudyTextView>(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { context: android.content.Context ->
                            JustifiedStudyTextView(context).apply {
                                studyTextView = this
                                setTextSizeSp(fontSize)
                                setLineHeightMultiplier(uiState.lineHeightMultiplier)
                                applyTypeface(uiState.fontFamily)
                                setReadableColors(
                                    textColor = readerTextColor,
                                    backgroundColor = readerBackgroundColor
                                )
                                onTextTapListener = { tapOutcome ->
                                    if (tapOutcome == TextTapOutcome.DISMISSED_SELECTION) {
                                        Unit
                                    }
                                }
                                onSelectionChangedListener = { start, end ->
                                    val normalizedStart = minOf(start, end)
                                    val normalizedEnd = maxOf(start, end)
                                    selectionRange = if (normalizedStart >= 0 && normalizedStart < normalizedEnd) {
                                        activeHighlight = null
                                        SelectionRange(normalizedStart, normalizedEnd)
                                    } else {
                                        null
                                    }
                                }
                                onHighlightTappedListener = { tappedHighlight ->
                                    if (tappedHighlight != null) {
                                        activeHighlight = tappedHighlight
                                        selectionRange = null
                                        clearSelection()
                                    } else if (activeHighlight != null) {
                                        activeHighlight = null
                                    } else {
                                        isUiVisible = !isUiVisible
                                    }
                                }
                            }
                        },
                        update = { textView: JustifiedStudyTextView ->
                            studyTextView = textView
                            textView.setTextSizeSp(fontSize)
                            textView.setLineHeightMultiplier(uiState.lineHeightMultiplier)
                            textView.applyTypeface(uiState.fontFamily)
                            textView.setReadableColors(
                                textColor = readerTextColor,
                                backgroundColor = readerBackgroundColor
                            )
                            textView.onTextTapListener = { tapOutcome ->
                                if (tapOutcome == TextTapOutcome.DISMISSED_SELECTION) {
                                    Unit
                                }
                            }
                            textView.onSelectionChangedListener = { start, end ->
                                val normalizedStart = minOf(start, end)
                                val normalizedEnd = maxOf(start, end)
                                selectionRange = if (normalizedStart >= 0 && normalizedStart < normalizedEnd) {
                                    activeHighlight = null
                                    SelectionRange(normalizedStart, normalizedEnd)
                                } else {
                                    null
                                }
                            }
                            textView.onHighlightTappedListener = { tappedHighlight ->
                                if (tappedHighlight != null) {
                                    activeHighlight = tappedHighlight
                                    selectionRange = null
                                    textView.clearSelection()
                                } else if (activeHighlight != null) {
                                    activeHighlight = null
                                } else {
                                    isUiVisible = !isUiVisible
                                }
                            }
                            textView.setContentWithHighlights(
                                content = uiState.content,
                                highlights = uiState.highlights,
                                redColor = highlightSpanColor(HighlightColor.RED),
                                blueColor = highlightSpanColor(HighlightColor.BLUE),
                                greenColor = highlightSpanColor(HighlightColor.GREEN),
                                yellowColor = highlightSpanColor(HighlightColor.YELLOW)
                            )
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isUiVisible,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BottomAppBarDefaults.containerColor)
                    .statusBarsPadding()
                    .testTag(READER_TOP_BAR_TAG)
            ) {
                TopAppBar(
                    title = { Text(subtitle.title) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BottomAppBarDefaults.containerColor
                    ),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                        IconButton(onClick = { requestAction(PendingAction.ExitScreen) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (readerMode == ReaderMode.STUDY && isEditing) {
                            TextButton(onClick = { requestAction(PendingAction.ExitEditing) }) {
                                Text("Cancel")
                            }
                        }
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = isUiVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(currentText()))
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy text")
                        }

                        when (readerMode) {
                            ReaderMode.ORIGINAL -> {
                                IconButton(
                                    onClick = { showTimestamps = !showTimestamps },
                                    enabled = originalSegments.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = if (showTimestamps) {
                                            Icons.Filled.TimerOff
                                        } else {
                                            Icons.Filled.Timer
                                        },
                                        contentDescription = if (showTimestamps) {
                                            "Hide timestamps"
                                        } else {
                                            "Show timestamps"
                                        }
                                    )
                                }
                            }
                            ReaderMode.STUDY -> {
                                IconButton(onClick = {
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
                                }) {
                                    Icon(
                                        imageVector = if (isEditing) Icons.Filled.Save else Icons.Filled.Edit,
                                        contentDescription = if (isEditing) "Save" else "Edit"
                                    )
                                }
                            }
                        }

                        IconButton(onClick = {
                            if (fontSize > 12f) viewModel.updateFontSize(fontSize - 2f)
                        }) {
                            Icon(Icons.Filled.Remove, contentDescription = "Decrease Font Size")
                        }

                        IconButton(onClick = {
                            if (fontSize < 42f) viewModel.updateFontSize(fontSize + 2f)
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = "Increase Font Size")
                        }

                        var showFontMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showFontMenu = true }) {
                                Icon(Icons.Filled.FormatSize, contentDescription = "Font Family")
                            }
                            DropdownMenu(
                                expanded = showFontMenu,
                                onDismissRequest = { showFontMenu = false }
                            ) {
                                val fonts = listOf("Default", "Serif", "SansSerif", "Monospace")
                                fonts.forEach { font ->
                                    DropdownMenuItem(
                                        text = { Text(font) },
                                        onClick = {
                                            viewModel.updateFontFamily(font)
                                            showFontMenu = false
                                        },
                                        trailingIcon = {
                                            if (uiState.fontFamily == font) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected"
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share text") },
                                    onClick = {
                                        showOverflowMenu = false
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, currentText())
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Share text")
                                        )
                                    }
                                )
                                if (readerMode == ReaderMode.STUDY) {
                                    DropdownMenuItem(
                                        text = { Text("Remove empty lines") },
                                        onClick = {
                                            showOverflowMenu = false
                                            val cleaned = currentText()
                                                .lines()
                                                .filter { it.isNotBlank() }
                                                .joinToString("\n")
                                            applyTextUpdate(cleaned)
                                        }
                                    )
                                }
                                if (!(readerMode == ReaderMode.STUDY && isEditing)) {
                                    DropdownMenuItem(
                                        text = { Text("Find") },
                                        onClick = {
                                            showOverflowMenu = false
                                            resetFindDialogState()
                                            showFindDialog = true
                                        }
                                    )
                                }
                                if (readerMode == ReaderMode.STUDY) {
                                    DropdownMenuItem(
                                        text = { Text("Find and replace") },
                                        onClick = {
                                            showOverflowMenu = false
                                            resetFindReplaceDialogState()
                                            showFindReplaceDialog = true
                                        }
                                    )
                                }
                                if (readerMode != ReaderMode.ORIGINAL) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (uiState.isAiCleaning) {
                                                    "AI cleaning..."
                                                } else {
                                                    "AI cleaning"
                                                }
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            val sourceText = currentText()
                                            if (sourceText.isBlank()) {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        "Nothing to clean."
                                                    )
                                                }
                                                return@DropdownMenuItem
                                            }

                                            coroutineScope.launch {
                                                val result = viewModel.enqueueAiCleaning(sourceText)
                                                result.onSuccess {
                                                    snackbarHostState.showSnackbar(
                                                        "AI cleaning started. Check notifications for progress."
                                                    )
                                                }.onFailure { error ->
                                                    val message = error.message
                                                        ?.takeIf { it.isNotBlank() }
                                                        ?: "AI cleaning failed."
                                                    snackbarHostState.showSnackbar(message)
                                                }
                                            }
                                        },
                                        enabled = !uiState.isAiCleaning
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isUiVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (isUiVisible) 88.dp else 16.dp)
                .navigationBarsPadding()
        ) {
            FloatingActionButton(
                onClick = {
                    val targetMode = if (readerMode == ReaderMode.STUDY) {
                        ReaderMode.ORIGINAL
                    } else {
                        ReaderMode.STUDY
                    }
                    requestAction(PendingAction.SwitchMode(targetMode))
                }
            ) {
                Icon(
                    imageVector = if (readerMode == ReaderMode.STUDY) {
                        Icons.Filled.Subtitles
                    } else {
                        Icons.AutoMirrored.Filled.MenuBook
                    },
                    contentDescription = if (readerMode == ReaderMode.STUDY) {
                        "Switch to original mode"
                    } else {
                        "Switch to study mode"
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showSelectionToolbar,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = if (isUiVisible) 84.dp else 16.dp
                )
                .navigationBarsPadding()
        ) {
            HighlightSelectionToolbar(
                modifier = Modifier.testTag(READER_SELECTION_TOOLBAR_TAG),
                onColorSelected = { color ->
                    val selectedHighlight = activeHighlight
                    if (selectedHighlight != null) {
                        viewModel.updateHighlightColor(selectedHighlight, color)
                        activeHighlight = selectedHighlight.copy(color = color)
                    } else {
                        val range = selectionRange ?: return@HighlightSelectionToolbar
                        viewModel.applyHighlight(range.start, range.end, color)
                        selectionRange = null
                        studyTextView?.clearSelection()
                    }
                },
                showDelete = activeHighlight != null,
                onDeleteHighlight = {
                    val selectedHighlight = activeHighlight ?: return@HighlightSelectionToolbar
                    viewModel.deleteHighlight(selectedHighlight)
                    activeHighlight = null
                    selectionRange = null
                    studyTextView?.clearSelection()
                }
            )
        }

        if (!isUiVisible && !isEditing) {
            TinyProgressIndicator(
                percent = fullscreenProgressPercent,
                currentPage = fullscreenPageProgress.currentPage,
                totalPages = fullscreenPageProgress.totalPages,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 8.dp, bottom = 8.dp)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = {
                showUnsavedDialog = false
                pendingAction = null
            },
            title = { Text("Unsaved changes") },
            text = { Text("Do you want to discard your unsaved changes?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    isEditing = false
                    editText = uiState.content
                    pendingAction?.let { action ->
                        runPendingAction(action)
                    }
                    pendingAction = null
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    pendingAction = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFindDialog) {
        val noFindResults = hasSearchedFind &&
            findErrorMessage == null &&
            findResults.isEmpty() &&
            originalSegmentFindResults.isEmpty()

        AlertDialog(
            modifier = Modifier.testTag(READER_FIND_DIALOG_TAG),
            onDismissRequest = {
                resetFindDialogState()
                showFindDialog = false
            },
            title = { Text("Find") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = findQuery,
                            onValueChange = { findQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(READER_FIND_INPUT_TAG),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = { runFindSearch() }
                            ),
                            label = { Text("Regex") },
                            isError = findErrorMessage != null,
                            supportingText = {
                                val errorMessage = findErrorMessage
                                if (errorMessage != null) {
                                    Text(errorMessage)
                                }
                            }
                        )
                        IconButton(onClick = { runFindSearch() }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search"
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = findIsCaseSensitive,
                            onCheckedChange = { findIsCaseSensitive = it }
                        )
                        Text("Case sensitive")
                    }
                    when {
                        originalSegmentFindResults.isNotEmpty() -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .padding(top = 12.dp)
                                    .testTag(READER_FIND_RESULTS_TAG)
                            ) {
                                items(originalSegmentFindResults.size) { index ->
                                    val result = originalSegmentFindResults[index]
                                    FindResultRow(
                                        number = result.number,
                                        excerpt = result.excerpt,
                                        progressPercent = result.progressPercent,
                                        onClick = {
                                            showFindDialog = false
                                            selectionRange = null
                                            activeHighlight = null
                                            studyTextView?.clearSelection()
                                            originalSelectionCoordinator.clearAllSelections()
                                            pendingFindSelection = PendingFindSelection.OriginalSegment(
                                                segmentIndex = result.segmentIndex,
                                                start = result.start,
                                                end = result.end
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        findResults.isNotEmpty() -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .padding(top = 12.dp)
                                    .testTag(READER_FIND_RESULTS_TAG)
                            ) {
                                items(findResults.size) { index ->
                                    val result = findResults[index]
                                    FindResultRow(
                                        number = result.number,
                                        excerpt = result.excerpt,
                                        progressPercent = result.progressPercent,
                                        onClick = {
                                            showFindDialog = false
                                            selectionRange = null
                                            activeHighlight = null
                                            studyTextView?.clearSelection()
                                            originalSelectionCoordinator.clearAllSelections()
                                            pendingFindSelection = when (readerMode) {
                                                ReaderMode.STUDY -> PendingFindSelection.Study(
                                                    start = result.start,
                                                    end = result.end
                                                )

                                                ReaderMode.ORIGINAL -> PendingFindSelection.OriginalFallback(
                                                    start = result.start,
                                                    end = result.end
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        noFindResults -> {
                            Text(
                                text = "No results.",
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    resetFindDialogState()
                    showFindDialog = false
                }) {
                    Text("Close")
                }
            }
        )
    }

    if (showFindReplaceDialog) {
        AlertDialog(
            onDismissRequest = {
                resetFindReplaceDialogState()
                showFindReplaceDialog = false
            },
            title = { Text("Find and replace") },
            text = {
                Column {
                    TextField(
                        value = findText,
                        onValueChange = {
                            findText = it
                            findReplaceErrorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(READER_FIND_REPLACE_INPUT_TAG),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None
                        ),
                        label = { Text("Regex") },
                        isError = findReplaceErrorMessage != null,
                        supportingText = {
                            val errorMessage = findReplaceErrorMessage
                            if (errorMessage != null) {
                                Text(errorMessage)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.padding(top = 12.dp))
                    TextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(READER_FIND_REPLACE_REPLACEMENT_TAG),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None
                        ),
                        label = { Text("Replace with") }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isCaseSensitive,
                            onCheckedChange = { isCaseSensitive = it }
                        )
                        Text("Case sensitive")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val updated = replaceRegexMatches(
                        text = currentText(),
                        query = findText,
                        replacement = replaceText,
                        isCaseSensitive = isCaseSensitive
                    ).getOrElse { error ->
                        findReplaceErrorMessage = error.message ?: "Invalid regex."
                        return@TextButton
                    }

                    applyTextUpdate(updated)
                    resetFindReplaceDialogState()
                    showFindReplaceDialog = false
                }) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    resetFindReplaceDialogState()
                    showFindReplaceDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAiPreviewDialog) {
        val previewText = uiState.pendingAiCleanedText
        if (previewText != null) {
            AlertDialog(
                onDismissRequest = {
                    showAiPreviewDialog = false
                    viewModel.clearPendingAiCleaningResult()
                },
                title = { Text("AI cleaned text") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(previewText)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (isEditing) {
                            editText = previewText
                        } else {
                            applyTextUpdate(previewText)
                        }
                        showAiPreviewDialog = false
                        viewModel.clearPendingAiCleaningResult()
                    }) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAiPreviewDialog = false
                        viewModel.clearPendingAiCleaningResult()
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    if (showAiErrorDialog) {
        val errorSummary = uiState.aiCleaningErrorSummary
        val errorLog = uiState.aiCleaningErrorLog
        if (!errorLog.isNullOrBlank()) {
            AlertDialog(
                onDismissRequest = {
                    showAiErrorDialog = false
                    viewModel.clearAiCleaningError()
                },
                title = { Text("AI cleaning failed") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        if (!errorSummary.isNullOrBlank()) {
                            Text(
                                text = errorSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.padding(top = 12.dp))
                        }
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Text(errorLog)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(errorLog))
                        showAiErrorDialog = false
                        viewModel.clearAiCleaningError()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Error copied to clipboard.")
                        }
                    }) {
                        Text("Copy error")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAiErrorDialog = false
                        viewModel.clearAiCleaningError()
                    }) {
                        Text("Dismiss")
                    }
                }
            )
        }
    }

    if (showEmptyDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyDialog = false },
            title = { Text("Empty text") },
            text = { Text("Subtitle text cannot be empty.") },
            confirmButton = {
                TextButton(onClick = { showEmptyDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun HighlightSelectionToolbar(
    modifier: Modifier = Modifier,
    onColorSelected: (HighlightColor) -> Unit,
    showDelete: Boolean,
    onDeleteHighlight: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HighlightColorButton(color = HighlightColor.RED, onClick = onColorSelected)
            HighlightColorButton(color = HighlightColor.BLUE, onClick = onColorSelected)
            HighlightColorButton(color = HighlightColor.GREEN, onClick = onColorSelected)
            HighlightColorButton(color = HighlightColor.YELLOW, onClick = onColorSelected)
            if (showDelete) {
                FilledTonalButton(
                    onClick = onDeleteHighlight,
                    modifier = Modifier.size(44.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete highlight"
                    )
                }
            }
        }
    }
}

@Composable
private fun FindResultRow(
    number: Int,
    excerpt: String,
    progressPercent: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = "$number.",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = excerpt,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$progressPercent%",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun HighlightColorButton(
    color: HighlightColor,
    onClick: (HighlightColor) -> Unit
) {
    Button(
        onClick = { onClick(color) },
        modifier = Modifier.size(44.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = highlightButtonColor(color)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Spacer(modifier = Modifier.size(1.dp))
    }
}

private fun highlightButtonColor(color: HighlightColor): Color = when (color) {
    HighlightColor.RED -> Color(0xFFE57373)
    HighlightColor.BLUE -> Color(0xFF64B5F6)
    HighlightColor.GREEN -> Color(0xFF81C784)
    HighlightColor.YELLOW -> Color(0xFFFFF176)
}

private fun highlightSpanColor(color: HighlightColor): Int = when (color) {
    HighlightColor.RED -> AndroidColor.parseColor("#66E57373")
    HighlightColor.BLUE -> AndroidColor.parseColor("#6664B5F6")
    HighlightColor.GREEN -> AndroidColor.parseColor("#6681C784")
    HighlightColor.YELLOW -> AndroidColor.parseColor("#66FFF176")
}

private fun formatOriginalModeCopyText(
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

private fun scrollPercent(
    value: Int,
    maxValue: Int,
    canScrollForward: Boolean,
    canScrollBackward: Boolean
): Int {
    if (maxValue <= 0 || (!canScrollForward && !canScrollBackward)) {
        return 100
    }
    if (!canScrollBackward || value <= 0) {
        return 0
    }
    if (!canScrollForward || value >= maxValue) {
        return 100
    }
    return ((value.toFloat() / maxValue.toFloat()) * 100f)
        .toInt()
        .coerceIn(0, 99)
}

private fun lazyListScrollPercent(
    firstVisibleItemIndex: Int,
    totalItems: Int,
    canScrollForward: Boolean,
    canScrollBackward: Boolean
): Int {
    if (totalItems <= 0 || (!canScrollForward && !canScrollBackward)) {
        return 100
    }
    if (!canScrollBackward || firstVisibleItemIndex <= 0) {
        return 0
    }
    if (!canScrollForward) {
        return 100
    }
    val maxIndex = (totalItems - 1).coerceAtLeast(1)
    return ((firstVisibleItemIndex.toFloat() / maxIndex.toFloat()) * 100f)
        .toInt()
        .coerceIn(0, 99)
}

private fun pagedScrollProgress(
    value: Int,
    maxValue: Int,
    viewportHeightPx: Int
): PageProgress {
    if (viewportHeightPx <= 0) {
        return PageProgress(currentPage = 1, totalPages = 1)
    }
    val contentHeightPx = (maxValue + viewportHeightPx).coerceAtLeast(viewportHeightPx)
    val totalPages = ceil(contentHeightPx.toFloat() / viewportHeightPx.toFloat())
        .toInt()
        .coerceAtLeast(1)
    val currentPage = ((value.coerceAtLeast(0)) / viewportHeightPx) + 1
    return PageProgress(
        currentPage = currentPage.coerceIn(1, totalPages),
        totalPages = totalPages
    )
}

private fun lazyListPageProgress(
    firstVisibleItemIndex: Int,
    totalItems: Int,
    visibleItemsCount: Int,
    canScrollForward: Boolean
): PageProgress {
    if (totalItems <= 0) {
        return PageProgress(currentPage = 1, totalPages = 1)
    }
    val itemsPerPage = visibleItemsCount.coerceAtLeast(1)
    val totalPages = ceil(totalItems.toFloat() / itemsPerPage.toFloat())
        .toInt()
        .coerceAtLeast(1)
    val estimatedCurrentPage = (firstVisibleItemIndex.coerceAtLeast(0) / itemsPerPage) + 1
    val currentPage = if (!canScrollForward) {
        totalPages
    } else {
        estimatedCurrentPage.coerceIn(1, totalPages)
    }
    return PageProgress(currentPage = currentPage, totalPages = totalPages)
}

@Composable
private fun TinyProgressIndicator(
    percent: Int,
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = "$percent% $currentPage/$totalPages",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun Modifier.onUnconsumedTap(onTap: () -> Unit): Modifier =
    pointerInput(onTap) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Final)
            val up = waitForUpOrCancellation(pass = PointerEventPass.Final) ?: return@awaitEachGesture
            if (!down.isConsumed && !up.isConsumed) {
                onTap()
            }
        }
    }

private fun formatTime(millis: Long): String {
    val seconds = millis / 1000
    val m = seconds / 60
    val s = seconds % 60
    val h = m / 60
    val mm = m % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, mm, s)
    } else {
        String.format("%d:%02d", mm, s)
    }
}
