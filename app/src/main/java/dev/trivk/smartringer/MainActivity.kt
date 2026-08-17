package dev.trivk.smartringer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.trivk.smartringer.ui.SmartRingerApp
import dev.trivk.smartringer.ui.theme.SmartRingerTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            SmartRingerTheme(customSeedArgb = settings.customThemeColorArgb) {
                SmartRingerApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reconcile()
    }
}
