package com.deedeedev.ytreader.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deedeedev.ytreader.AppContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appContainer: AppContainer,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(appContainer.userPreferencesRepository)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val selectedFontFamily = when (uiState.fontFamily) {
        "Serif" -> FontFamily.Serif
        "SansSerif" -> FontFamily.SansSerif
        "Monospace" -> FontFamily.Monospace
        "Cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Settings") })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = uiState.appTheme.label,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth(),
                        label = { Text("App Theme") }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        uiState.availableThemes.forEach { appTheme ->
                            DropdownMenuItem(
                                text = { Text(appTheme.label) },
                                onClick = {
                                    viewModel.setAppTheme(appTheme)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Reader Defaults",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Text(
                    text = "Default Font Size: ${uiState.defaultFontSize.toInt()}sp",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = uiState.defaultFontSize.sp,
                        fontFamily = selectedFontFamily
                    )
                )
                Slider(
                    value = uiState.defaultFontSize,
                    onValueChange = { viewModel.setDefaultFontSize(it) },
                    valueRange = 12f..42f,
                    steps = 14 // (42-12)/2 - 1 = 14 steps of size 2
                )
            }

            item {
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = uiState.fontFamily,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth(),
                        label = { Text("Font Family") }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        uiState.availableFonts.forEach { font ->
                            DropdownMenuItem(
                                text = { Text(font) },
                                onClick = {
                                    viewModel.setFontFamily(font)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Line Height Multiplier: ${String.format("%.1f", uiState.lineHeightMultiplier)}x",
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = uiState.lineHeightMultiplier,
                    onValueChange = { viewModel.setLineHeightMultiplier(it) },
                    valueRange = 0.5f..2.5f,
                    steps = 19 // (2.5-0.5)/0.1 - 1 = 19 steps of size 0.1
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.aiEndpoint,
                    onValueChange = { viewModel.setAiEndpoint(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AI Endpoint") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.aiApiKey,
                    onValueChange = { viewModel.setAiApiKey(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AI API key") },
                    placeholder = { Text("Paste your API key") },
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.aiModel,
                    onValueChange = { viewModel.setAiModel(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AI Model") },
                    placeholder = { Text("gpt-4o-mini") },
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.aiPrompt,
                    onValueChange = { viewModel.setAiPrompt(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AI Cleaning Prompt") },
                    placeholder = { Text("Describe how subtitle text should be cleaned") },
                    minLines = 6,
                    maxLines = 12
                )
            }
        }
    }
}
