package com.deedeedev.ytreader.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class SettingsImportTarget {
    PREFERENCES,
    DATA
}

private enum class ThumbnailBulkAction {
    DOWNLOAD_MISSING,
    CLEAN_ORPHANS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appContainer: AppContainer,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(
            appContainer.appContext,
            appContainer.userPreferencesRepository,
            appContainer.youtubeRepository,
            appContainer.videoRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var pendingImportTarget by remember { mutableStateOf<SettingsImportTarget?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDataImportPreview by remember { mutableStateOf<DataBackupPreview?>(null) }
    var pendingForceImport by remember { mutableStateOf(false) }
    var pendingThumbnailAction by remember { mutableStateOf<ThumbnailBulkAction?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDirectoryChange by remember { mutableStateOf(false) }

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

    fun launchBlockingUiTask(block: suspend () -> Unit) {
        coroutineScope.launch {
            if (isBusy) {
                return@launch
            }
            isBusy = true
            try {
                block()
            } catch (error: Exception) {
                snackbarHostState.showSnackbar(
                    error.message ?: context.getString(R.string.operation_failed)
                )
            } finally {
                isBusy = false
            }
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
            launchBlockingUiTask {
                val preview = readDataBackupPreview(context, uri.toString())
                pendingImportTarget = SettingsImportTarget.DATA
                pendingImportUri = uri
                pendingDataImportPreview = preview
            }
        }
    }

    val autoBackupDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            viewModel.setAutoBackupDirectoryUri(uri.toString())
        }
        pendingDirectoryChange = false
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
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = stringResource(uiState.appLanguage.labelRes),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_app_language)) }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        uiState.availableLanguages.forEach { appLanguage ->
                            DropdownMenuItem(
                                text = { Text(stringResource(appLanguage.labelRes)) },
                                onClick = {
                                    viewModel.setAppLanguage(appLanguage)
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

            item {
                AutomaticBackupSection(
                    enabled = uiState.autoBackupEnabled,
                    directoryUri = uiState.autoBackupDirectoryUri,
                    backupTime = uiState.autoBackupTime,
                    includeSettings = uiState.autoBackupIncludeSettings,
                    onEnabledChange = { viewModel.setAutoBackupEnabled(it) },
                    onSelectDirectory = {
                        if (!uiState.autoBackupDirectoryUri.isNullOrBlank()) {
                            pendingDirectoryChange = true
                        } else {
                            autoBackupDirectoryLauncher.launch(null)
                        }
                    },
                    onTimeClick = { showTimePicker = true },
                    onIncludeSettingsChange = { viewModel.setAutoBackupIncludeSettings(it) }
                )
            }

            item {
                BulkThumbnailActionsSection(
                    enabled = !isBusy && !uiState.isRunningThumbnailBulkAction,
                    onDownloadMissing = {
                        pendingThumbnailAction = ThumbnailBulkAction.DOWNLOAD_MISSING
                    },
                    onCleanOrphans = {
                        pendingThumbnailAction = ThumbnailBulkAction.CLEAN_ORPHANS
                    }
                )
            }

            if (isBusy || uiState.isRunningThumbnailBulkAction) {
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

    if (showTimePicker) {
        val timeParts = uiState.autoBackupTime.split(":")
        val initialHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 2
        val initialMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.auto_backup_time_picker_title)) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val hour = timePickerState.hour
                    val minute = timePickerState.minute
                    val timeString = String.format("%02d:%02d", hour, minute)
                    viewModel.setAutoBackupTime(timeString)
                    showTimePicker = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (pendingDirectoryChange) {
        AlertDialog(
            onDismissRequest = { pendingDirectoryChange = false },
            title = { Text(stringResource(R.string.auto_backup_confirm_title)) },
            text = { Text(stringResource(R.string.auto_backup_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDirectoryChange = false
                    autoBackupDirectoryLauncher.launch(null)
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDirectoryChange = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingThumbnailAction?.let { action ->
        val titleRes = when (action) {
            ThumbnailBulkAction.DOWNLOAD_MISSING -> R.string.settings_thumbnail_download_confirm_title
            ThumbnailBulkAction.CLEAN_ORPHANS -> R.string.settings_thumbnail_cleanup_confirm_title
        }
        val messageRes = when (action) {
            ThumbnailBulkAction.DOWNLOAD_MISSING -> R.string.settings_thumbnail_download_confirm_message
            ThumbnailBulkAction.CLEAN_ORPHANS -> R.string.settings_thumbnail_cleanup_confirm_message
        }
        AlertDialog(
            onDismissRequest = {
                if (!isBusy && !uiState.isRunningThumbnailBulkAction) {
                    pendingThumbnailAction = null
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = !isBusy && !uiState.isRunningThumbnailBulkAction,
                dismissOnClickOutside = !isBusy && !uiState.isRunningThumbnailBulkAction
            ),
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(messageRes)) },
            confirmButton = {
                TextButton(
                    enabled = !isBusy && !uiState.isRunningThumbnailBulkAction,
                    onClick = {
                        val selectedAction = action
                        pendingThumbnailAction = null
                        launchTask {
                            when (selectedAction) {
                                ThumbnailBulkAction.DOWNLOAD_MISSING -> viewModel.downloadMissingThumbnails()
                                ThumbnailBulkAction.CLEAN_ORPHANS -> viewModel.cleanOrphanThumbnails()
                            }
                        }
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isBusy && !uiState.isRunningThumbnailBulkAction,
                    onClick = { pendingThumbnailAction = null }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (pendingImportTarget != null && pendingImportUri != null) {
        val target = pendingImportTarget!!
        AlertDialog(
            onDismissRequest = {
                if (!isBusy) {
                    pendingImportTarget = null
                    pendingImportUri = null
                    pendingDataImportPreview = null
                    pendingForceImport = false
                }
            },
            properties = DialogProperties(dismissOnBackPress = !isBusy, dismissOnClickOutside = !isBusy),
            title = {
                Text(
                    if (target == SettingsImportTarget.PREFERENCES) {
                        stringResource(R.string.settings_preferences_import_confirm)
                    } else {
                        stringResource(R.string.settings_data_backup_preview)
                    }
                )
            },
            text = {
                if (target == SettingsImportTarget.PREFERENCES) {
                    Text(stringResource(R.string.settings_preferences_import_message))
                } else {
                    val preview = pendingDataImportPreview
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.settings_data_import_message))
                        if (preview != null) {
                            preview.createdAtEpochMillis?.let { createdAt ->
                                Text(
                                    stringResource(
                                        R.string.settings_data_backup_preview_created,
                                        DateFormat.getDateTimeInstance().format(Date(createdAt))
                                    )
                                )
                            }
                            preview.appVersionName?.takeIf { it.isNotBlank() }?.let { appVersion ->
                                Text(
                                    stringResource(
                                        R.string.settings_data_backup_preview_app_version,
                                        appVersion
                                    )
                                )
                            }
                            Text(
                                stringResource(
                                    R.string.settings_data_backup_preview_schema,
                                    preview.schemaVersion
                                )
                            )
                            Text(
                                stringResource(
                                    R.string.settings_data_backup_preview_subtitles,
                                    preview.subtitleCount
                                )
                            )
                            Text(
                                stringResource(
                                    R.string.settings_data_backup_preview_collections,
                                    preview.collectionCount
                                )
                            )
                            Text(
                                stringResource(
                                    R.string.settings_data_backup_preview_bookmarks,
                                    preview.bookmarkCount
                                )
                            )
                            Text(
                                stringResource(
                                    R.string.settings_data_backup_preview_notes,
                                    preview.highlightNoteCount
                                )
                            )
                            Text(
                                stringResource(
                                    R.string.settings_data_backup_preview_manifest,
                                    stringResource(
                                        if (preview.hasManifest) {
                                            R.string.settings_data_backup_preview_manifest_present
                                        } else {
                                            R.string.settings_data_backup_preview_manifest_missing
                                        }
                                    )
                                )
                            )
                            Text(
                                text = if (preview.isCompatible) {
                                    stringResource(R.string.settings_data_backup_preview_ready)
                                } else {
                                    preview.incompatibilityReason?.let { issue ->
                                        when (issue) {
                                            BackupCompatibilityIssue.SCHEMA_VERSION_MISMATCH -> {
                                                stringResource(R.string.settings_backup_error_schema_mismatch)
                                            }

                                            BackupCompatibilityIssue.ROOM_IDENTITY_MISMATCH -> {
                                                stringResource(R.string.settings_backup_error_identity_mismatch)
                                            }
                                        }
                                    } ?: stringResource(R.string.settings_data_backup_preview_incompatible)
                                },
                                color = if (preview.isCompatible) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                            if (!preview.isCompatible) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        pendingForceImport = !pendingForceImport
                                    }
                                ) {
                                    Checkbox(
                                        checked = pendingForceImport,
                                        onCheckedChange = { pendingForceImport = it }
                                    )
                                    Column {
                                        Text(
                                            stringResource(R.string.settings_backup_force_import),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            stringResource(R.string.settings_backup_force_import_warning),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(stringResource(R.string.settings_working))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy && (
                        target != SettingsImportTarget.DATA ||
                            pendingDataImportPreview?.isCompatible == true ||
                            pendingForceImport
                    ),
                    onClick = {
                        val uri = pendingImportUri ?: return@TextButton
                        val currentTarget = pendingImportTarget ?: return@TextButton
                        val forceImport = pendingForceImport
                        launchTask {
                            if (currentTarget == SettingsImportTarget.PREFERENCES) {
                                importPreferencesBackup(context, appContainer, uri.toString())
                                pendingImportTarget = null
                                pendingImportUri = null
                                pendingDataImportPreview = null
                                pendingForceImport = false
                                context.getString(R.string.settings_preferences_imported)
                            } else {
                                importDataBackup(context, appContainer, uri.toString(), forceImport)
                                pendingImportTarget = null
                                pendingImportUri = null
                                pendingDataImportPreview = null
                                pendingForceImport = false
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
                        pendingDataImportPreview = null
                        pendingForceImport = false
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun BulkThumbnailActionsSection(
    enabled: Boolean,
    onDownloadMissing: () -> Unit,
    onCleanOrphans: () -> Unit
) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_thumbnail_tools),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_thumbnail_tools_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onDownloadMissing,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_thumbnail_download_missing))
            }
            OutlinedButton(
                onClick = onCleanOrphans,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_thumbnail_clean_orphans))
            }
        }
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

@Composable
private fun AutomaticBackupSection(
    enabled: Boolean,
    directoryUri: String?,
    backupTime: String,
    includeSettings: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onSelectDirectory: () -> Unit,
    onTimeClick: () -> Unit,
    onIncludeSettingsChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val folderName = remember(directoryUri) {
        if (directoryUri.isNullOrBlank()) return@remember null
        runCatching {
            val uri = Uri.parse(directoryUri)
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            val segments = docId.split(":")
            segments.lastOrNull() ?: docId
        }.getOrNull()
    }

    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.auto_backup_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.auto_backup_description),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.auto_backup_enabled),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectDirectory() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auto_backup_location),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = folderName ?: stringResource(R.string.auto_backup_location_not_set),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (folderName != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                OutlinedButton(onClick = onSelectDirectory) {
                    Text(stringResource(R.string.auto_backup_location_select))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTimeClick() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.auto_backup_time),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = backupTime,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auto_backup_include_settings),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.auto_backup_include_settings_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Checkbox(
                    checked = includeSettings,
                    onCheckedChange = onIncludeSettingsChange
                )
            }
            if (!enabled && !directoryUri.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.auto_backup_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
