package com.deedeedev.ytreader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.ui.FontOption
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(
    modifier: Modifier = Modifier,
    visible: Boolean,
    title: String,
    topBarTag: String,
    showCancelAction: Boolean,
    onBack: () -> Unit,
    onCancelEditing: () -> Unit
) {
    val backLabel = stringResource(R.string.back)
    val cancelLabel = stringResource(R.string.cancel)
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BottomAppBarDefaults.containerColor)
                .statusBarsPadding()
                .testTag(topBarTag)
        ) {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BottomAppBarDefaults.containerColor
                ),
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
                    }
                },
                actions = {
                    if (showCancelAction) {
                        TextButton(onClick = onCancelEditing) {
                            Text(cancelLabel)
                        }
                    }
                }
            )
        }
    }
}

@Composable
internal fun ReaderBottomBar(
    modifier: Modifier = Modifier,
    visible: Boolean,
    isOriginalMode: Boolean,
    isEditing: Boolean,
    showTimestamps: Boolean,
    fontSize: Float,
    selectedFontFamily: String,
    isAiCleaning: Boolean,
    isNotificationPermissionGranted: Boolean,
    currentText: String,
    onCopyText: (AnnotatedString) -> Unit,
    onToggleTimestamps: () -> Unit,
    onEditSaveTap: () -> Unit,
    onDecreaseFontSize: () -> Unit,
    onIncreaseFontSize: () -> Unit,
    onChangeFontFamily: (String) -> Unit,
    onShareText: (String) -> Unit,
    onReplaceWithClipboard: () -> Unit,
    onRemoveEmptyLines: () -> Unit,
    onShowFind: () -> Unit,
    onShowVideoNotes: () -> Unit,
    onShowFindAndReplace: () -> Unit,
    onStartExternalAiCleaning: (String) -> Unit,
    onStartAiCleaning: (String) -> Unit,
    onRequestNotificationPermission: (String) -> Unit,
    hasTimestampedSegments: Boolean,
    onShowJumpToTime: () -> Unit,
    useWebView: Boolean = false,
    onEditUnavailable: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val copyTextLabel = stringResource(R.string.copy_text)
    val hideTimestampsLabel = stringResource(R.string.hide_timestamps)
    val showTimestampsLabel = stringResource(R.string.show_timestamps)
    val saveLabel = stringResource(R.string.save)
    val editLabel = stringResource(R.string.edit)
    val decreaseFontSizeLabel = stringResource(R.string.decrease_font_size)
    val increaseFontSizeLabel = stringResource(R.string.increase_font_size)
    val fontFamilyLabel = stringResource(R.string.font_family)
    val selectedLabel = stringResource(R.string.selected)
    val moreOptionsLabel = stringResource(R.string.more_options)
    val shareTextLabel = stringResource(R.string.share_text)
    val replaceWithClipboardLabel = stringResource(R.string.reader_replace_with_clipboard)
    val highlightsAndNotesLabel = stringResource(R.string.highlights_and_notes)
    val removeEmptyLinesLabel = stringResource(R.string.remove_empty_lines)
    val findLabel = stringResource(R.string.find)
    val externalAiCleaningLabel = stringResource(R.string.ai_cleaning_external_menu_label)
    val findAndReplaceLabel = stringResource(R.string.find_and_replace)
    val aiCleaningLabel = stringResource(R.string.ai_cleaning_menu_label)
    val aiCleaningRunningLabel = stringResource(R.string.ai_cleaning_running_menu_label)
    val jumpToTimeLabel = stringResource(R.string.reader_jump_to_time)
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
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
                    IconButton(onClick = { onCopyText(AnnotatedString(currentText)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = copyTextLabel)
                    }

                    if (isOriginalMode) {
                        IconButton(onClick = onToggleTimestamps) {
                            Icon(
                                imageVector = if (showTimestamps) Icons.Filled.TimerOff else Icons.Filled.Timer,
                                contentDescription = if (showTimestamps) {
                                    hideTimestampsLabel
                                } else {
                                    showTimestampsLabel
                                }
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                onEditSaveTap()
                            }
                        ) {
                            Icon(
                                imageVector = if (isEditing) Icons.Filled.Save else Icons.Filled.Edit,
                                contentDescription = if (isEditing) saveLabel else editLabel
                            )
                        }
                    }

                    IconButton(onClick = onDecreaseFontSize, enabled = fontSize > 12f) {
                        Icon(Icons.Filled.Remove, contentDescription = decreaseFontSizeLabel)
                    }

                    IconButton(onClick = onIncreaseFontSize, enabled = fontSize < 42f) {
                        Icon(Icons.Filled.Add, contentDescription = increaseFontSizeLabel)
                    }

                    var showFontMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showFontMenu = true }) {
                            Icon(Icons.Filled.FormatSize, contentDescription = fontFamilyLabel)
                        }
                        DropdownMenu(
                            expanded = showFontMenu,
                            onDismissRequest = { showFontMenu = false }
                        ) {
                            FontOption.labels(context, includeCursive = false).forEach { (fontValue, fontLabel) ->
                                DropdownMenuItem(
                                    text = { Text(fontLabel) },
                                    onClick = {
                                        onChangeFontFamily(fontValue)
                                        showFontMenu = false
                                    },
                                    trailingIcon = {
                                        if (selectedFontFamily == fontValue) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = selectedLabel
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = onShowVideoNotes) {
                        Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = highlightsAndNotesLabel)
                    }

                    var showOverflowMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = moreOptionsLabel)
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(shareTextLabel) },
                                onClick = {
                                    showOverflowMenu = false
                                    onShareText(currentText)
                                }
                            )
                            if (!isOriginalMode) {
                                DropdownMenuItem(
                                    text = { Text(removeEmptyLinesLabel) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onRemoveEmptyLines()
                                    }
                                )
                            }
                            if (!(isEditing && !isOriginalMode)) {
                                DropdownMenuItem(
                                    text = { Text(findLabel) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onShowFind()
                                    }
                                )
                            }
                            if (isOriginalMode && hasTimestampedSegments) {
                                DropdownMenuItem(
                                    text = { Text(jumpToTimeLabel) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onShowJumpToTime()
                                    }
                                )
                            }
                            if (!isOriginalMode) {
                                DropdownMenuItem(
                                    text = { Text(findAndReplaceLabel) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onShowFindAndReplace()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(replaceWithClipboardLabel) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onReplaceWithClipboard()
                                    }
                                )
                            }
                            if (!isOriginalMode) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (isAiCleaning) aiCleaningRunningLabel else aiCleaningLabel)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        if (isNotificationPermissionGranted) {
                                            onStartAiCleaning(currentText)
                                        } else {
                                            onRequestNotificationPermission(currentText)
                                        }
                                    },
                                    enabled = !isAiCleaning
                                )
                                DropdownMenuItem(
                                    text = { Text(externalAiCleaningLabel) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onStartExternalAiCleaning(currentText)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReaderModeFab(
    modifier: Modifier = Modifier,
    visible: Boolean,
    isOriginalMode: Boolean,
    onSwitchMode: () -> Unit
) {
    val switchToOriginalModeLabel = stringResource(R.string.switch_to_original_mode)
    val switchToStudyModeLabel = stringResource(R.string.switch_to_study_mode)
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        FloatingActionButton(onClick = onSwitchMode) {
            Icon(
                imageVector = if (!isOriginalMode) {
                    Icons.Filled.Subtitles
                } else {
                    Icons.AutoMirrored.Filled.MenuBook
                },
                contentDescription = if (!isOriginalMode) {
                    switchToOriginalModeLabel
                } else {
                    switchToStudyModeLabel
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScrollSlider(
    modifier: Modifier = Modifier,
    visible: Boolean,
    scrollProgress: Float,
    enabled: Boolean,
    showReturnButton: Boolean = false,
    onScrollToProgress: (Float) -> Unit,
    onReturnClick: () -> Unit = {},
    onValueChangeFinished: () -> Unit = {}
) {
    val sliderLabel = stringResource(R.string.reader_scroll_slider)
    val returnLabel = stringResource(R.string.reader_return_to_previous_position)
    AnimatedVisibility(
        visible = visible && enabled,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = showReturnButton,
                    enter = fadeIn() + scaleIn(initialScale = 0.5f),
                    exit = fadeOut() + scaleOut(targetScale = 0.5f)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 4.dp)
                            .size(28.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                shape = CircleShape
                            )
                            .clickable(onClick = onReturnClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = returnLabel,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Slider(
                    value = scrollProgress,
                    onValueChange = onScrollToProgress,
                    onValueChangeFinished = onValueChangeFinished,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp, vertical = 2.dp)
                        .testTag(READER_SCROLL_SLIDER_TAG),
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        activeTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                )
            }
        }
    }
}

@Composable
internal fun ReaderBrightnessGestureArea(
    modifier: Modifier = Modifier,
    isEditing: Boolean,
    appBrightnessPreference: Float,
    gestureTag: String,
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
            .testTag(gestureTag)
            .then(
                if (isEditing) {
                    Modifier
                } else {
                    Modifier.pointerInput(appBrightnessPreference, isEditing) {
                        detectVerticalDragGestures(
                            onDragStart = { onStart() },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            },
                            onDragEnd = onEnd,
                            onDragCancel = onCancel
                        )
                    }
                }
            )
    )
}

@Composable
internal fun ReaderAnnotationsSwipeArea(
    modifier: Modifier = Modifier,
    isEditing: Boolean,
    gestureTag: String,
    onSwipeUp: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(READER_ANNOTATIONS_SWIPE_AREA_HEIGHT)
            .testTag(gestureTag)
            .then(
                if (isEditing) {
                    Modifier
                } else {
                    Modifier.pointerInput(onSwipeUp) {
                        awaitEachGesture {
                            val first = awaitFirstDown(requireUnconsumed = false)
                            var triggered = false
                            val startYs = mutableMapOf(first.id to first.position.y)
                            do {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    startYs.putIfAbsent(change.id, change.position.y)
                                }
                            if (!triggered && event.changes.any { ch ->
                                    val sy = startYs[ch.id] ?: return@any false
                                    (sy - ch.position.y) > viewConfiguration.touchSlop
                                }) {
                                triggered = true
                                onSwipeUp()
                            }
                        } while (event.changes.any { it.pressed })
                        }
                    }
                }
            )
    )
}
