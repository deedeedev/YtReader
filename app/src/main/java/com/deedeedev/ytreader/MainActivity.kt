package com.deedeedev.ytreader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deedeedev.ytreader.ui.MainScreen
import com.deedeedev.ytreader.ui.home.HomeViewModel
import com.deedeedev.ytreader.ui.theme.YtReaderTheme

class MainActivity : ComponentActivity() {
    private var latestIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        latestIntent = intent
        val appContainer = (application as YtReaderApplication).container

        setContent {
            val appTheme by appContainer.userPreferencesRepository.appTheme.collectAsState()
            val currentIntent = latestIntent

            YtReaderTheme(appTheme = appTheme) {
                val viewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.provideFactory(
                        appContainer.youtubeRepository,
                        appContainer.subtitleDao,
                        appContainer.userPreferencesRepository
                    )
                )

                LaunchedEffect(currentIntent) {
                    if (currentIntent?.action == Intent.ACTION_SEND && currentIntent.type == "text/plain") {
                        currentIntent.getStringExtra(Intent.EXTRA_TEXT)?.let { url ->
                            viewModel.onUrlChange(url)
                            viewModel.searchVideo()
                        }
                    }
                }

                MainScreen(
                    appContainer = appContainer,
                    viewModel = viewModel,
                    requestedReaderSubtitleId = extractRequestedSubtitleId(currentIntent),
                    onReaderSubtitleHandled = {
                        val current = latestIntent
                        if (current != null && current.hasExtra(EXTRA_SUBTITLE_ID)) {
                            latestIntent = Intent(current).apply {
                                removeExtra(EXTRA_SUBTITLE_ID)
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntent = intent
    }

    private fun extractRequestedSubtitleId(intent: Intent?): Long? {
        if (intent?.action != ACTION_OPEN_READER || !intent.hasExtra(EXTRA_SUBTITLE_ID)) {
            return null
        }
        val subtitleId = intent.getLongExtra(EXTRA_SUBTITLE_ID, -1L)
        return subtitleId.takeIf { it > 0L }
    }

    companion object {
        const val ACTION_OPEN_READER = "com.deedeedev.ytreader.action.OPEN_READER"
        const val EXTRA_SUBTITLE_ID = "subtitle_id"
    }
}
