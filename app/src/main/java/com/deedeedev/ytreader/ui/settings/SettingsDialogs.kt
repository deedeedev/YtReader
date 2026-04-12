package com.deedeedev.ytreader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.deedeedev.ytreader.R
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTimePickerDialog(
    autoBackupTime: String,
    onTimeSet: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val timeParts = autoBackupTime.split(":")
    val initialHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 2
    val initialMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_backup_time_picker_title)) },
        text = {
            TimePicker(state = timePickerState)
        },
        confirmButton = {
            TextButton(onClick = {
                val hour = timePickerState.hour
                val minute = timePickerState.minute
                val timeString = String.format("%02d:%02d", hour, minute)
                onTimeSet(timeString)
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
internal fun SettingsDirectoryConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_backup_confirm_title)) },
        text = { Text(stringResource(R.string.auto_backup_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
internal fun SettingsThumbnailActionDialog(
    titleRes: Int,
    messageRes: Int,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isBusy) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isBusy,
            dismissOnClickOutside = !isBusy
        ),
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(messageRes)) },
        confirmButton = {
            TextButton(
                enabled = !isBusy,
                onClick = onConfirm
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isBusy,
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
internal fun SettingsImportConfirmDialog(
    target: SettingsImportTarget,
    isBusy: Boolean,
    preview: DataBackupPreview?,
    pendingForceImport: Boolean,
    onForceImportChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isBusy) {
                onDismiss()
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
                                    onForceImportChange(!pendingForceImport)
                                }
                            ) {
                                Checkbox(
                                    checked = pendingForceImport,
                                    onCheckedChange = onForceImportChange
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
                        preview?.isCompatible == true ||
                        pendingForceImport
                ),
                onClick = onConfirm
            ) {
                Text(stringResource(R.string.settings_import))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isBusy,
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
