package com.deedeedev.ytreader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.deedeedev.ytreader.ui.MainScreen
import com.deedeedev.ytreader.ui.home.HomeViewModel
import com.deedeedev.ytreader.ui.reader.ReaderScreen
import com.deedeedev.ytreader.ui.theme.YtReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as YtReaderApplication).container
        
        setContent {
            YtReaderTheme {
                val viewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.provideFactory(
                        appContainer.youtubeRepository,
                        appContainer.subtitleDao
                    )
                )
                
                val uiState by viewModel.uiState.collectAsState()
                
                // Handle Intent
                LaunchedEffect(intent) {
                    if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { url ->
                            viewModel.onUrlChange(url)
                            viewModel.searchVideo()
                        }
                    }
                }

                if (uiState.selectedSubtitle != null) {
                    ReaderScreen(
                        subtitle = uiState.selectedSubtitle!!,
                        onBack = { viewModel.clearSelection() }
                    )
                } else {
                    MainScreen(
                        appContainer = appContainer,
                        onSubtitleClick = { id -> 
                            viewModel.selectSubtitle(id)
                        },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
