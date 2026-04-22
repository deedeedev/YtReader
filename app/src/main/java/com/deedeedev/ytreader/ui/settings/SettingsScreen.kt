package com.deedeedev.ytreader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.AppContainer
import com.deedeedev.ytreader.R

data class SettingsMenuItem(
    val titleRes: Int,
    val descriptionRes: Int,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appContainer: AppContainer,
    onNavigateToSettingsAppearance: () -> Unit,
    onNavigateToSettingsReader: () -> Unit,
    onNavigateToSettingsAi: () -> Unit,
    onNavigateToSettingsBackup: () -> Unit
) {
    val menuItems = listOf(
        SettingsMenuItem(
            titleRes = R.string.settings_appearance,
            descriptionRes = R.string.settings_appearance_description,
            icon = Icons.Default.Palette,
            onClick = onNavigateToSettingsAppearance
        ),
        SettingsMenuItem(
            titleRes = R.string.settings_reader_defaults,
            descriptionRes = R.string.settings_reader_description,
            icon = Icons.Default.TextFields,
            onClick = onNavigateToSettingsReader
        ),
        SettingsMenuItem(
            titleRes = R.string.settings_ai_configuration,
            descriptionRes = R.string.settings_ai_description,
            icon = Icons.Default.AutoAwesome,
            onClick = onNavigateToSettingsAi
        ),
        SettingsMenuItem(
            titleRes = R.string.settings_backup_restore,
            descriptionRes = R.string.settings_backup_description,
            icon = Icons.Default.Backup,
            onClick = onNavigateToSettingsBackup
        )
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(menuItems) { item ->
                SettingsMenuCard(item = item)
            }
        }
    }
}

@Composable
private fun SettingsMenuCard(
    item: SettingsMenuItem
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { item.onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(item.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}