package com.deedeedev.ytreader.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deedeedev.ytreader.AppContainer
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.ui.FontOption
import com.deedeedev.ytreader.ui.settings.ProgressIndicatorMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsReaderScreen(
    appContainer: AppContainer,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(
            appContainer,
            appContainer.appContext,
            appContainer.userPreferencesRepository,
            appContainer.youtubeRepository,
            appContainer.videoRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val selectedFontFamily = when (FontOption.fromStorageValue(uiState.fontFamily)) {
        FontOption.SERIF -> FontFamily.Serif
        FontOption.SANS_SERIF -> FontFamily.SansSerif
        FontOption.MONOSPACE -> FontFamily.Monospace
        FontOption.CURSIVE -> FontFamily.Cursive
        FontOption.DEFAULT -> FontFamily.Default
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_reader_defaults)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.settings_default_font_size,
                    uiState.defaultFontSize.toInt()
                ),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = uiState.defaultFontSize.sp,
                    fontFamily = selectedFontFamily
                )
            )
            Slider(
                value = uiState.defaultFontSize,
                onValueChange = { viewModel.setDefaultFontSize(it) },
                valueRange = 12f..42f,
                steps = 14
            )

            var fontExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = fontExpanded,
                onExpandedChange = { fontExpanded = !fontExpanded }
            ) {
                TextField(
                    value = stringResource(FontOption.fromStorageValue(uiState.fontFamily).labelRes),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_font_family)) }
                )
                ExposedDropdownMenu(
                    expanded = fontExpanded,
                    onDismissRequest = { fontExpanded = false }
                ) {
                    uiState.availableFonts.forEach { font ->
                        DropdownMenuItem(
                            text = { Text(stringResource(FontOption.fromStorageValue(font).labelRes)) },
                            onClick = {
                                viewModel.setFontFamily(font)
                                fontExpanded = false
                            }
                        )
                    }
                }
            }

            Text(
                text = stringResource(
                    R.string.settings_line_height_multiplier,
                    String.format("%.1f", uiState.lineHeightMultiplier)
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = uiState.lineHeightMultiplier,
                onValueChange = { viewModel.setLineHeightMultiplier(it) },
                valueRange = 0.5f..2.5f,
                steps = 19
            )

            var progressExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = progressExpanded,
                onExpandedChange = { progressExpanded = !progressExpanded }
            ) {
                TextField(
                    value = stringResource(uiState.progressIndicatorMode.labelRes),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = progressExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_progress_indicator)) }
                )
                ExposedDropdownMenu(
                    expanded = progressExpanded,
                    onDismissRequest = { progressExpanded = false }
                ) {
                    uiState.availableProgressIndicatorModes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(stringResource(mode.labelRes)) },
                            onClick = {
                                viewModel.setProgressIndicatorMode(mode)
                                progressExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}