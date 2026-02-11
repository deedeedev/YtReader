package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.data.local.SubtitleDao
import android.content.Intent

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

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val lineHeightSp = fontSize * uiState.lineHeightMultiplier

    var isEditing by rememberSaveable { mutableStateOf(false) }
    var editText by rememberSaveable(subtitle.id) { mutableStateOf(uiState.content) }
    var showEmptyDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
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

    val hasUnsavedChanges = isEditing && editText != uiState.content

    fun currentText(): String = if (isEditing) editText else uiState.content

    fun applyTextUpdate(updated: String) {
        if (isEditing) {
            editText = updated
        } else {
            viewModel.updateContent(updated)
        }
    }

    val attemptLeaveEditMode: () -> Unit = {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onBack()
        }
    }

    BackHandler {
        attemptLeaveEditMode()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(subtitle.title) },
                navigationIcon = {
                    IconButton(onClick = attemptLeaveEditMode) {
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
                        clipboardManager.setText(AnnotatedString(uiState.content))
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy text")
                    }

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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
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

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved changes") },
            text = { Text("Do you want to leave without saving your changes?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    isEditing = false
                    editText = uiState.content
                    onBack()
                }) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) {
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
