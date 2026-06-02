package com.automatelinux.pt

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.ui.MainScreen
import com.automatelinux.pt.ui.theme.PTTheme
import com.automatelinux.pt.util.EnStrings
import com.automatelinux.pt.util.HeStrings
import com.automatelinux.pt.util.LocalAppStrings
import com.automatelinux.pt.util.ServerConfig
import com.automatelinux.pt.util.SettingsStore
import com.automatelinux.pt.ui.map.MapZoomHandler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsStore: SettingsStore

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            Log.d("MapZoom", "key down: ${event.keyCode}")
            when (event.keyCode) {
                KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS,
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    MapZoomHandler.zoomIn()
                    return true
                }
                KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    MapZoomHandler.zoomOut()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapZoomHandler.clear()
        setContent {
            var language by remember { mutableStateOf(settingsStore.language) }
            val strings = if (language == "he") HeStrings else EnStrings
            val layoutDirection = if (language == "he") LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(
                LocalAppStrings provides strings,
                LocalLayoutDirection provides layoutDirection,
            ) {
                PTTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ServerCheckScreen(settingsStore) { newLang ->
                            settingsStore.language = newLang
                            language = newLang
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCheckScreen(
    settingsStore: SettingsStore,
    onLanguageChange: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    var serverReady by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var failedServer by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val server = ServerConfig.findReachableServer()
        if (server != null) {
            serverReady = true
        } else {
            failedServer = strings.noServerReachable
        }
        checking = false
    }

    if (serverReady) {
        MainScreen(settingsStore = settingsStore, onLanguageChange = onLanguageChange)
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (checking) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        strings.connectingToServer,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                } else {
                    Text(
                        failedServer ?: strings.connectionFailed,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        strings.serversTried(ServerConfig.servers.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}
