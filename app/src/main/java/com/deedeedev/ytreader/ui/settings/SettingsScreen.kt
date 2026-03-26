package com.deedeedev.ytreader.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deedeedev.ytreader.AppContainer
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.ui.FontOption
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private enum class SettingsImportTarget {
    PREFERENCES,
    DATA
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appContainer: AppContainer,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(appContainer.userPreferencesRepository)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var pendingImportTarget by remember { mutableStateOf<SettingsImportTarget?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val selectedFontFamily = when (FontOption.fromStorageValue(uiState.fontFamily)) {
        FontOption.SERIF -> FontFamily.Serif
        FontOption.SANS_SERIF -> FontFamily.SansSerif
        FontOption.MONOSPACE -> FontFamily.Monospace
        FontOption.CURSIVE -> FontFamily.Cursive
        FontOption.DEFAULT -> FontFamily.Default
    }

    fun launchTask(block: suspend () -> String) {
        coroutineScope.launch {
            if (isBusy) {
                return@launch
            }
            isBusy = true
            val message = try {
                block()
            } catch (error: Exception) {
                error.message ?: context.getString(R.string.operation_failed)
            }
            isBusy = false
            snackbarHostState.showSnackbar(message)
        }
    }

    val exportPreferencesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            launchTask {
                exportPreferencesBackup(context, appContainer, uri.toString())
                context.getString(R.string.settings_preferences_exported)
            }
        }
    }

    val importPreferencesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportTarget = SettingsImportTarget.PREFERENCES
            pendingImportUri = uri
        }
    }

    val exportDataLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            launchTask {
                exportDataBackup(context, uri.toString())
                context.getString(R.string.settings_data_exported)
            }
        }
    }

    val importDataLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportTarget = SettingsImportTarget.DATA
            pendingImportUri = uri
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
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
                        value = stringResource(uiState.appTheme.labelRes),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_app_theme)) }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        uiState.availableThemes.forEach { appTheme ->
                            DropdownMenuItem(
                                text = { Text(stringResource(appTheme.labelRes)) },
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
                    text = stringResource(R.string.settings_reader_defaults),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
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
                        value = stringResource(FontOption.fromStorageValue(uiState.fontFamily).labelRes),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_font_family)) }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        uiState.availableFonts.forEach { font ->
                            DropdownMenuItem(
                                text = { Text(stringResource(FontOption.fromStorageValue(font).labelRes)) },
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
                    steps = 19 // (2.5-0.5)/0.1 - 1 = 19 steps of size 0.1
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.aiEndpoint,
                    onValueChange = { viewModel.setAiEndpoint(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_ai_endpoint)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_endpoint_placeholder)) },
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.aiApiKey,
                    onValueChange = { viewModel.setAiApiKey(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_ai_api_key)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_api_key_placeholder)) },
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.aiModel,
                    onValueChange = { viewModel.setAiModel(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_ai_model)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_model_placeholder)) },
                    singleLine = true
                )
            }

            item {
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

            item {
                Text(
                    text = stringResource(R.string.settings_backup_restore),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                BackupActionSection(
                    title = stringResource(R.string.settings_preferences),
                    description = stringResource(R.string.settings_preferences_description),
                    exportLabel = stringResource(R.string.settings_preferences_export),
                    importLabel = stringResource(R.string.settings_preferences_import),
                    enabled = !isBusy,
                    onExport = {
                        exportPreferencesLauncher.launch(buildPreferencesBackupFileName())
                    },
                    onImport = {
                        importPreferencesLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                )
            }

            item {
                BackupActionSection(
                    title = stringResource(R.string.settings_data),
                    description = stringResource(R.string.settings_data_description),
                    exportLabel = stringResource(R.string.settings_data_export),
                    importLabel = stringResource(R.string.settings_data_import),
                    enabled = !isBusy,
                    onExport = {
                        exportDataLauncher.launch(buildDataBackupFileName())
                    },
                    onImport = {
                        importDataLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                )
            }

            if (isBusy) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.settings_working),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    if (pendingImportTarget != null && pendingImportUri != null) {
        val target = pendingImportTarget!!
        AlertDialog(
            onDismissRequest = {
                if (!isBusy) {
                    pendingImportTarget = null
                    pendingImportUri = null
                }
            },
            properties = DialogProperties(dismissOnBackPress = !isBusy, dismissOnClickOutside = !isBusy),
            title = {
                Text(
                    if (target == SettingsImportTarget.PREFERENCES) {
                        stringResource(R.string.settings_preferences_import_confirm)
                    } else {
                        stringResource(R.string.settings_data_import_confirm)
                    }
                )
            },
            text = {
                Text(
                    if (target == SettingsImportTarget.PREFERENCES) {
                        stringResource(R.string.settings_preferences_import_message)
                    } else {
                        stringResource(R.string.settings_data_import_message)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        val uri = pendingImportUri ?: return@TextButton
                        val currentTarget = pendingImportTarget ?: return@TextButton
                        launchTask {
                            if (currentTarget == SettingsImportTarget.PREFERENCES) {
                                importPreferencesBackup(context, appContainer, uri.toString())
                                pendingImportTarget = null
                                pendingImportUri = null
                                context.getString(R.string.settings_preferences_imported)
                            } else {
                                importDataBackup(context, appContainer, uri.toString())
                                pendingImportTarget = null
                                pendingImportUri = null
                                context.getString(R.string.settings_data_imported)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.settings_import))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        pendingImportTarget = null
                        pendingImportUri = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun BackupActionSection(
    title: String,
    description: String,
    exportLabel: String,
    importLabel: String,
    enabled: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onExport,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(exportLabel)
                }
                Button(
                    onClick = onImport,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(importLabel)
                }
            }
        }
    }
}
