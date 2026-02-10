package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.local.SubtitleDao
import com.deedeedev.ytreader.domain.SubtitleParser
import com.deedeedev.ytreader.domain.SubtitleSegment

import androidx.compose.material.icons.filled.FormatSize

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
    
    var showTimestamps by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    // Scroll to last position on first load
    LaunchedEffect(subtitle.id) {
        val lastTimestamp = subtitle.lastTimestamp
        if (lastTimestamp > 0) {
            val index = uiState.segments.indexOfFirst { it.startTime >= lastTimestamp }
            if (index >= 0) {
                listState.scrollToItem(index)
            }
        }
    }

    // Save scroll position
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                if (index < uiState.segments.size) {
                    val timestamp = uiState.segments[index].startTime
                    viewModel.updateLastTimestamp(timestamp)
                }
            }
    }

    val fullText by remember(uiState.segments, showTimestamps) {
        derivedStateOf {
            uiState.segments.joinToString("\n\n") { segment ->
                if (showTimestamps) {
                    "[${formatTime(segment.startTime)}] ${segment.text}"
                } else {
                    segment.text
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(subtitle.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Copy
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(fullText))
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy text")
                    }

                    // Show/Hide Timestamps
                    IconButton(onClick = { showTimestamps = !showTimestamps }) {
                        Icon(
                            imageVector = if (showTimestamps) Icons.Filled.TimerOff else Icons.Filled.Timer,
                            contentDescription = if (showTimestamps) "Hide Timestamps" else "Show Timestamps"
                        )
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
                            val fonts = listOf("Default", "Serif", "SansSerif", "Monospace", "Cursive")
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
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            itemsIndexed(uiState.segments) { index, segment ->
                SelectionContainer {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        if (showTimestamps) {
                            Text(
                                text = formatTime(segment.startTime),
                                fontSize = (fontSize * 0.8).sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                fontFamily = fontFamily
                            )
                        }
                        Text(
                            text = segment.text,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.5).sp,
                            fontFamily = fontFamily
                        )
                    }
                }
            }
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

