package com.deedeedev.ytreader.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deedeedev.ytreader.AppContainer
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.ui.home.VideoCardSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppearanceScreen(
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_appearance)) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            var themeExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = themeExpanded,
                onExpandedChange = { themeExpanded = !themeExpanded }
            ) {
                TextField(
                    value = stringResource(uiState.appTheme.labelRes),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_app_theme)) }
                )
                ExposedDropdownMenu(
                    expanded = themeExpanded,
                    onDismissRequest = { themeExpanded = false }
                ) {
                    uiState.availableThemes.forEach { appTheme ->
                        DropdownMenuItem(
                            text = { Text(stringResource(appTheme.labelRes)) },
                            onClick = {
                                viewModel.setAppTheme(appTheme)
                                themeExpanded = false
                            }
                        )
                    }
                }
            }

            var languageExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = languageExpanded,
                onExpandedChange = { languageExpanded = !languageExpanded }
            ) {
                TextField(
                    value = stringResource(uiState.appLanguage.labelRes),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_app_language)) }
                )
                ExposedDropdownMenu(
                    expanded = languageExpanded,
                    onDismissRequest = { languageExpanded = false }
                ) {
                    uiState.availableLanguages.forEach { appLanguage ->
                        DropdownMenuItem(
                            text = { Text(stringResource(appLanguage.labelRes)) },
                            onClick = {
                                viewModel.setAppLanguage(appLanguage)
                                languageExpanded = false
                            }
                        )
                    }
                }
            }

            var videoCardSizeExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = videoCardSizeExpanded,
                onExpandedChange = { videoCardSizeExpanded = !videoCardSizeExpanded }
            ) {
                TextField(
                    value = stringResource(uiState.videoCardSize.labelRes),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = videoCardSizeExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_video_card_size)) }
                )
                ExposedDropdownMenu(
                    expanded = videoCardSizeExpanded,
                    onDismissRequest = { videoCardSizeExpanded = false }
                ) {
                    uiState.availableVideoCardSizes.forEach { size ->
                        DropdownMenuItem(
                            text = { Text(stringResource(size.labelRes)) },
                            onClick = {
                                viewModel.setVideoCardSize(size)
                                videoCardSizeExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}