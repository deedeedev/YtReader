package com.deedeedev.ytreader.ui.settings

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.R

@Composable
internal fun BackupActionSection(
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
internal fun AutomaticBackupSection(
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

@Composable
internal fun BulkThumbnailActionsSection(
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
