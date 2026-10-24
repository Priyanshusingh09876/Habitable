package edu.bu.cs501.habitable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.di.AppContainer
import edu.bu.cs501.habitable.ui.navigation.HabitableNavHost
import edu.bu.cs501.habitable.ui.theme.HabitableTheme
import kotlinx.coroutines.flow.map

/**
 * Single activity. Compose owns the whole UI; configuration changes are handled
 * by Compose + ViewModel, and the manifest keeps the activity alive on rotation
 * so the sensor flow and chart state are not rebuilt.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = HabitableApp.container(this)
        setContent {
            HabitableTheme {
                Surface(Modifier.fillMaxSize()) {
                    HabitableRoot(container)
                }
            }
        }
    }
}

@Composable
private fun HabitableRoot(container: AppContainer) {
    // Decide the start destination once: setup until an apartment exists.
    val onboarded by container.settings.settings.map { it.onboarded }.collectAsStateWithLifecycle(initialValue = null)
    when (onboarded) {
        null -> Unit // one frame while DataStore loads; avoids flashing the wrong screen
        else -> HabitableNavHost(container = container, startAtSetup = onboarded == false)
    }
}
