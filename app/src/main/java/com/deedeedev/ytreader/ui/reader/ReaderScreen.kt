package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.systemBarsPadding

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.domain.SubtitleParser
import com.deedeedev.ytreader.domain.SubtitleSegment
import android.content.Intent
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow

import androidx.compose.material.icons.filled.FormatSize

private enum class ReaderMode {
    ORIGINAL,
    STUDY
}

private sealed interface PendingAction {
    data object ExitScreen : PendingAction
    data class SwitchMode(val targetMode: ReaderMode) : PendingAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    subtitleId: Long,
    subtitleDao: SubtitleDao,
    userPreferencesRepository: UserPreferencesRepository,
    onBack: () -> Unit
) {
    val viewModel: ReaderViewModel = viewModel(
        key = "Reader_$subtitleId",
        factory = ReaderViewModel.provideFactory(subtitleDao, userPreferencesRepository, subtitleId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val subtitle = uiState.subtitle

    if (uiState.isLoading || subtitle == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
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
    val context = LocalContext.current
    val originalListState = rememberLazyListState()
    val studyScrollState = rememberScrollState()
    val originalFallbackScrollState = rememberScrollState()
    val lineHeightSp = fontSize * uiState.lineHeightMultiplier

    var readerMode by rememberSaveable { mutableStateOf(ReaderMode.STUDY) }
    var showTimestamps by rememberSaveable { mutableStateOf(false) }
    var isUiVisible by rememberSaveable { mutableStateOf(false) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var editText by rememberSaveable(subtitle.id) { mutableStateOf(uiState.content) }
    var showEmptyDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showFindReplaceDialog by remember { mutableStateOf(false) }
    var findText by rememberSaveable { mutableStateOf("") }
    var replaceText by rememberSaveable { mutableStateOf("") }
    var isCaseSensitive by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.content, isEditing) {
        if (!isEditing) {
            editText = uiState.content
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            isUiVisible = true
        }
    }

    val hasUnsavedChanges = isEditing && editText != uiState.content

    val originalSegments = remember(subtitle.content) {
        SubtitleParser.parseToSegments(subtitle.content)
    }
    val originalModeText = remember(originalSegments, showTimestamps, uiState.originalParsedText) {
        formatOriginalModeCopyText(originalSegments, showTimestamps, uiState.originalParsedText)
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

    fun runPendingAction(action: PendingAction) {
        when (action) {
            PendingAction.ExitScreen -> onBack()
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
        requestAction(PendingAction.ExitScreen)
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isUiVisible,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it })
            ) {
                TopAppBar(
                    title = { Text(subtitle.title) },
                    navigationIcon = {
                        IconButton(onClick = { requestAction(PendingAction.ExitScreen) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = isUiVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
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
                        imageVector = if (readerMode == ReaderMode.STUDY) Icons.Filled.Timer else Icons.Filled.Edit,
                        contentDescription = if (readerMode == ReaderMode.STUDY) {
                            "Switch to original mode"
                        } else {
                            "Switch to study mode"
                        }
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isUiVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Copy
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
                            // Edit / Save
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

                    // Decrease Font Size
                    IconButton(onClick = {
                        if (fontSize > 12f) viewModel.updateFontSize(fontSize - 2f)
                    }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease Font Size")
                    }

                    // Increase Font Size
                    IconButton(onClick = {
                        if (fontSize < 42f) viewModel.updateFontSize(fontSize + 2f)
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase Font Size")
                    }

                      // Font Family
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

                    // More
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
                                DropdownMenuItem(
                                    text = { Text("Find and replace") },
                                    onClick = {
                                        showOverflowMenu = false
                                        showFindReplaceDialog = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("AI cleaning") },
                                onClick = { showOverflowMenu = false },
                                enabled = false
                            )
                        }
                    }
                }
            }
        }
        },

    ) { padding ->
        if (readerMode == ReaderMode.ORIGINAL) {
            if (originalSegments.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 16.dp)
                        .onUnconsumedTap { isUiVisible = !isUiVisible }
                        .verticalScroll(originalFallbackScrollState)
                ) {
                    SelectionContainer {
                        Text(
                            text = uiState.content,
                            fontSize = fontSize.sp,
                            lineHeight = lineHeightSp.sp,
                            fontFamily = fontFamily
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = originalListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 16.dp)
                        .onUnconsumedTap { isUiVisible = !isUiVisible }
                ) {
                    itemsIndexed(originalSegments) { _, segment ->
                        SelectionContainer {
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
                        Text(
                            text = segment.text,
                            fontSize = fontSize.sp,
                            lineHeight = lineHeightSp.sp,
                            fontFamily = fontFamily
                        )
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp)
                    .then(if (!isEditing) {
                        Modifier.onUnconsumedTap { isUiVisible = !isUiVisible }
                    } else Modifier)
                    .verticalScroll(studyScrollState)
            ) {
                if (isEditing) {
                    TextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontSize = fontSize.sp,
                            lineHeight = lineHeightSp.sp,
                            fontFamily = fontFamily
                        ),
                        colors = TextFieldDefaults.colors()
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = uiState.content,
                            fontSize = fontSize.sp,
                            lineHeight = lineHeightSp.sp,
                            fontFamily = fontFamily
                        )
                    }
                }
            }
        }
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

    if (showFindReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showFindReplaceDialog = false },
            title = { Text("Find and replace") },
            text = {
                Column {
                    TextField(
                        value = findText,
                        onValueChange = { findText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Find") }
                    )
                    Spacer(modifier = Modifier.padding(top = 12.dp))
                    TextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        modifier = Modifier.fillMaxWidth(),
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
                    val query = findText
                    if (query.isNotEmpty()) {
                        val regex = if (isCaseSensitive) {
                            Regex(Regex.escape(query))
                        } else {
                            Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
                        }
                        val updated = currentText().replace(regex, replaceText)
                        applyTextUpdate(updated)
                    }
                    showFindReplaceDialog = false
                }) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFindReplaceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
