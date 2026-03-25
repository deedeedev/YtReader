package com.deedeedev.ytreader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (showCancelAction) {
                        TextButton(onClick = onCancelEditing) {
                            Text("Cancel")
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
    onRemoveEmptyLines: () -> Unit,
    onShowFind: () -> Unit,
    onShowVideoNotes: () -> Unit,
    onShowFindAndReplace: () -> Unit,
    onStartAiCleaning: (String) -> Unit,
    onRequestNotificationPermission: (String) -> Unit
) {
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
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy text")
                    }

                    if (isOriginalMode) {
                        IconButton(onClick = onToggleTimestamps) {
                            Icon(
                                imageVector = if (showTimestamps) Icons.Filled.TimerOff else Icons.Filled.Timer,
                                contentDescription = if (showTimestamps) {
                                    "Hide timestamps"
                                } else {
                                    "Show timestamps"
                                }
                            )
                        }
                    } else {
                        IconButton(onClick = onEditSaveTap) {
                            Icon(
                                imageVector = if (isEditing) Icons.Filled.Save else Icons.Filled.Edit,
                                contentDescription = if (isEditing) "Save" else "Edit"
                            )
                        }
                    }

                    IconButton(onClick = onDecreaseFontSize, enabled = fontSize > 12f) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease Font Size")
                    }

                    IconButton(onClick = onIncreaseFontSize, enabled = fontSize < 42f) {
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
                                        onChangeFontFamily(font)
                                        showFontMenu = false
                                    },
                                    trailingIcon = {
                                        if (selectedFontFamily == font) {
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

                    var showOverflowMenu by remember { mutableStateOf(false) }
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
                                    onShareText(currentText)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Highlights & notes") },
                                onClick = {
                                    showOverflowMenu = false
                                    onShowVideoNotes()
                                }
                            )
                            if (!isOriginalMode) {
                                DropdownMenuItem(
                                    text = { Text("Remove empty lines") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onRemoveEmptyLines()
                                    }
                                )
                            }
                            if (!(isEditing && !isOriginalMode)) {
                                DropdownMenuItem(
                                    text = { Text("Find") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onShowFind()
                                    }
                                )
                            }
                            if (!isOriginalMode) {
                                DropdownMenuItem(
                                    text = { Text("Find and replace") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onShowFindAndReplace()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(if (isAiCleaning) "AI cleaning..." else "AI cleaning")
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
                    "Switch to original mode"
                } else {
                    "Switch to study mode"
                }
            )
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
