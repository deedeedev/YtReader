package com.deedeedev.ytreader.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deedeedev.ytreader.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onVideoClick: (String, Pair<Int, Int>) -> Unit,
    onVideoSearchAgain: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
    val videoCardSize by viewModel.videoCardSize.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.screen_history),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (historyItems != null) {
                LibraryListSection(
                    items = historyItems!!,
                    emptyText = stringResource(R.string.history_empty),
                    modifier = Modifier.fillMaxSize(),
                    key = { it.videoId }
                ) { item ->
                    LibraryItemCard(
                        item = item,
                        onSubtitleClick = { _, _ -> },
                        onVideoClick = { id, scrollPos -> onVideoClick(id, scrollPos) },
                        onVideoSearchAgain = onVideoSearchAgain,
                        onMarkAsRead = null,
                        onAddToCollection = {},
                        onDownloadThumbnail = null,
                        onResetProgress = {},
                        onRemoveFromHistory = {
                            viewModel.removeFromHistory(item.videoId)
                            coroutineScope.launch {
                                delay(5_000)
                                snackbarHostState.currentSnackbarData?.dismiss()
                            }
                        },
                        onSubtitleDelete = {},
                        onSubtitleDownloadAgain = {},
                        downloadingSubtitleIds = emptySet(),
                        isDownloadingThumbnail = false,
                        onExportEpub = null,
                        compact = videoCardSize == VideoCardSize.COMPACT
                    )
                }
            }
        }
    }
}