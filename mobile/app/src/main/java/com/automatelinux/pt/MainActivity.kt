package com.automatelinux.pt

import android.content.Intent
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.automatelinux.pt.analytics.AnalyticsRepository
import com.automatelinux.pt.ui.MainScreen
import com.automatelinux.pt.ui.PricingNoticeDialog
import com.automatelinux.pt.ui.RegistrationScreen
import com.automatelinux.pt.ui.theme.PTTheme
import kotlinx.coroutines.launch
import com.automatelinux.pt.util.EnStrings
import com.automatelinux.pt.util.HeStrings
import com.automatelinux.pt.util.LocalAppStrings
import com.automatelinux.pt.util.ServerConfig
import com.automatelinux.pt.util.SettingsStore
import com.automatelinux.pt.util.TripLink
import com.automatelinux.pt.ui.map.MapZoomHandler
import com.automatelinux.pt.widget.DeparturesWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var analytics: AnalyticsRepository

    // Trip arriving via a shared https link (VIEW intent); consumed by MainScreen.
    private val pendingSharedTrip = mutableStateOf<TripLink.SharedTrip?>(null)

    // Station arriving via a tap on a home-screen departures widget; consumed by MainScreen.
    private val pendingWidgetStation = mutableStateOf<Pair<String, String>?>(null)

    private fun parseWidgetStation(intent: Intent?) {
        val code = intent?.getStringExtra(DeparturesWidgetProvider.EXTRA_STATION_CODE)
        if (!code.isNullOrBlank()) {
            pendingWidgetStation.value = Pair(
                code,
                intent.getStringExtra(DeparturesWidgetProvider.EXTRA_STATION_NAME) ?: ""
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        TripLink.parse(intent.data)?.let { pendingSharedTrip.value = it }
        parseWidgetStation(intent)
    }

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
        TripLink.parse(intent?.data)?.let { pendingSharedTrip.value = it }
        parseWidgetStation(intent)
        // Identity first, ping second: trackLaunch reads the install id, and
        // minting a fresh one before the vault has been consulted would report a
        // reinstalling user as a brand-new install.
        //
        // Anonymous launch ping. Fire-and-forget on the activity scope so it can
        // never delay first paint; AnalyticsRepository swallows its own failures.
        lifecycleScope.launch {
            analytics.resolveIdentity()
            analytics.trackLaunch()
        }
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
                        // Told up front, on the very first launch: the app is free
                        // now and will become paid later. Nobody gets surprised.
                        var showPricingNotice by remember {
                            mutableStateOf(!settingsStore.pricingNoticeAcknowledged)
                        }
                        // Not read from SettingsStore directly: on a reinstall the
                        // prefs are empty but the Block Store vault may still hold
                        // the account, so the answer is only known once
                        // resolveIdentity has run. Showing the registration screen
                        // in the meantime would ask a returning user to type their
                        // details again for no reason.
                        val identity by analytics.identityState.collectAsStateWithLifecycle()

                        if (showPricingNotice) {
                            PricingNoticeDialog(
                                onDismiss = {
                                    settingsStore.pricingNoticeAcknowledged = true
                                    showPricingNotice = false
                                    lifecycleScope.launch {
                                        analytics.trackEvent("notice_acknowledged")
                                    }
                                }
                            )
                        }

                        // The pricing promise is shown before registration is
                        // asked for, so nobody hands over contact details without
                        // first knowing what they are being told about.
                        if (identity == AnalyticsRepository.IdentityState.RESOLVING) {
                            // Blank rather than a spinner: the vault answers in
                            // milliseconds, and a flashed spinner reads as a stall.
                            return@Surface
                        }

                        if (identity == AnalyticsRepository.IdentityState.UNREGISTERED) {
                            RegistrationScreen(
                                onSubmit = { email, phone, onError ->
                                    lifecycleScope.launch {
                                        analytics.register(email, phone)
                                            .onFailure { onError(strings.registerFailed) }
                                    }
                                }
                            )
                            return@Surface
                        }

                        ServerCheckScreen(
                            settingsStore = settingsStore,
                            sharedTrip = pendingSharedTrip.value,
                            onSharedTripConsumed = { pendingSharedTrip.value = null },
                            widgetStation = pendingWidgetStation.value,
                            onWidgetStationConsumed = { pendingWidgetStation.value = null },
                            onLanguageChange = { newLang ->
                                settingsStore.language = newLang
                                language = newLang
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCheckScreen(
    settingsStore: SettingsStore,
    sharedTrip: TripLink.SharedTrip?,
    onSharedTripConsumed: () -> Unit,
    widgetStation: Pair<String, String>?,
    onWidgetStationConsumed: () -> Unit,
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
        MainScreen(
            settingsStore = settingsStore,
            onLanguageChange = onLanguageChange,
            sharedTrip = sharedTrip,
            onSharedTripConsumed = onSharedTripConsumed,
            widgetStation = widgetStation,
            onWidgetStationConsumed = onWidgetStationConsumed
        )
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
