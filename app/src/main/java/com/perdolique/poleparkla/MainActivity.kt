package com.perdolique.poleparkla

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.perdolique.poleparkla.ui.PoleParklaApp
import com.perdolique.poleparkla.ui.PoleParklaTheme
import com.perdolique.poleparkla.ui.PoleParklaViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val container: AppContainer
        get() = (application as PoleParklaApplication).container

    private val viewModel: PoleParklaViewModel by viewModels {
        PoleParklaViewModel.Factory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PoleParklaTheme {
                PoleParklaApp(
                    viewModel = viewModel,
                    photoStore = container.photoStore,
                    startNewReport = intent.action == ACTION_NEW_REPORT,
                    onRequestInAppReview = {
                        lifecycleScope.launch {
                            container.playReviewLauncher.launch(this@MainActivity)
                        }
                    },
                )
            }
        }
    }

    private companion object {
        const val ACTION_NEW_REPORT = "com.perdolique.poleparkla.action.NEW_REPORT"
    }
}
