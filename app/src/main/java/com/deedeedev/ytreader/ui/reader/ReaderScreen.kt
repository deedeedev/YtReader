package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deedeedev.ytreader.data.local.SubtitleEntity
import com.deedeedev.ytreader.domain.SubtitleParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    subtitle: SubtitleEntity,
    onBack: () -> Unit
) {
    var fontSize by remember { mutableFloatStateOf(16f) }
    var showTimestamps by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val textToDisplay by remember(subtitle.content, showTimestamps) {
        derivedStateOf {
            if (showTimestamps) {
                subtitle.content
            } else {
                SubtitleParser.parse(subtitle.content)
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
                        clipboardManager.setText(AnnotatedString(textToDisplay))
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
                        if (fontSize > 12f) fontSize -= 2f
                    }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease Font Size")
                    }

                    // Increase Font Size
                    IconButton(onClick = {
                        if (fontSize < 30f) fontSize += 2f
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase Font Size")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SelectionContainer {
                Text(
                    text = textToDisplay,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.5).sp
                )
            }
        }
    }
}
