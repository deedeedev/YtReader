package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deedeedev.ytreader.data.local.SubtitleEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    subtitle: SubtitleEntity,
    onBack: () -> Unit
) {
    var fontSize by remember { mutableFloatStateOf(16f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(subtitle.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Font size control placeholder or simple button to cycle?
                    // Let's use a Slider in the bottom bar or top bar?
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Text("Font Size", modifier = Modifier.padding(horizontal = 16.dp))
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 12f..30f,
                    modifier = Modifier.weight(1f).padding(end = 16.dp)
                )
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
            Text(
                text = subtitle.content,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.5).sp
            )
        }
    }
}
