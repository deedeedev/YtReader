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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAiScreen(
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
                title = { Text(stringResource(R.string.settings_ai_configuration)) },
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
            OutlinedTextField(
                value = uiState.aiEndpoint,
                onValueChange = { viewModel.setAiEndpoint(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_ai_endpoint)) },
                placeholder = { Text(stringResource(R.string.settings_ai_endpoint_placeholder)) },
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.aiApiKey,
                onValueChange = { viewModel.setAiApiKey(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_ai_api_key)) },
                placeholder = { Text(stringResource(R.string.settings_ai_api_key_placeholder)) },
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.aiModel,
                onValueChange = { viewModel.setAiModel(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_ai_model)) },
                placeholder = { Text(stringResource(R.string.settings_ai_model_placeholder)) },
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.aiPrompt,
                onValueChange = { viewModel.setAiPrompt(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_ai_cleaning_prompt)) },
                placeholder = { Text(stringResource(R.string.settings_ai_prompt_placeholder)) },
                minLines = 6,
                maxLines = 12
            )
        }
    }
}