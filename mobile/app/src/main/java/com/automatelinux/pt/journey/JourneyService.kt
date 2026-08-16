package com.automatelinux.pt.journey

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.automatelinux.pt.MainActivity
import com.automatelinux.pt.R
import com.automatelinux.pt.util.SettingsStore
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Keeps a journey alive while the phone is in a pocket.
 *
 * It does three things the UI cannot: hold a location subscription the system will not
 * kill, put an ongoing notification on the lock screen that always says how many stops
 * are left, and buzz the rider before their stop. The journey logic itself is not here
 * — it is in [JourneySession] and the pure engine behind it.
 */
class JourneyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var subscribed = false

    // Resolved from Koin rather than injected: this service is not a Hilt entry point,
    // and SettingsModule bridges the very same singleton into Hilt the same way. One
    // instance, one SharedPreferences file, no second copy to fall out of sync.
    private val settings: SettingsStore by lazy { GlobalContext.get().get() }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            JourneySession.onFix(
                GeoFix(
                    lat = loc.latitude,
                    lon = loc.longitude,
                    accuracyMeters = loc.accuracy.toDouble(),
                    atMs = System.currentTimeMillis()
                )
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopJourney()
                return START_NOT_STICKY
            }
            ACTION_START -> Unit
            else -> Unit
        }

        // Typed explicitly: on API 34+ an untyped promotion of a service the manifest
        // declares as `location` is the kind of mismatch that throws at runtime.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            ongoingNotification(JourneySession.progress.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
        )
        subscribeToLocation()

        scope.launch {
            JourneySession.progress.collectLatest { progress ->
                if (progress == null) return@collectLatest
                notifier().notify(NOTIFICATION_ID, ongoingNotification(progress))
            }
        }
        scope.launch {
            JourneySession.alerts.collectLatest { alert -> fire(alert) }
        }
        scope.launch {
            // A fresh sighting redraws the shade line even when progress stood still.
            JourneySession.live.collectLatest {
                val p = JourneySession.progress.value ?: return@collectLatest
                notifier().notify(NOTIFICATION_ID, ongoingNotification(p))
            }
        }
        scope.launch {
            // The session owns the journey's lifetime — it now ends a finished trip
            // by itself, a beat after arrival. The service just follows: with no
            // itinerary there is nothing to keep alive from the shade.
            JourneySession.itinerary.collectLatest { if (it == null) stopJourney() }
        }

        // A journey the user swiped away, or one the system restarted with no session
        // behind it, has nothing to navigate — do not sit in the notification shade
        // pretending otherwise.
        if (!JourneySession.active) stopJourney()
        return START_STICKY
    }

    override fun onDestroy() {
        if (subscribed) {
            locationClient.removeLocationUpdates(locationCallback)
            subscribed = false
        }
        scope.cancel()
        super.onDestroy()
    }

    /** Swipe-away / task removal ends the trip rather than orphaning it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopJourney()
        super.onTaskRemoved(rootIntent)
    }

    private fun stopJourney() {
        JourneySession.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun subscribeToLocation() {
        if (subscribed) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // The session only starts this service after the permission was granted;
            // if it was revoked while running, the journey continues off the timetable
            // and the panel says so. Nothing to recover here.
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateDistanceMeters(LOCATION_MIN_METERS)
            .setWaitForAccurateLocation(false)
            .build()
        locationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        subscribed = true
    }

    // ---- notifications -------------------------------------------------------

    private fun notifier() = getSystemService(NotificationManager::class.java)

    private fun createChannels() {
        val strings = JourneySession.strings
        val manager = notifier()
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                strings.journeyChannelOngoing,
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                strings.journeyChannelAlerts,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = ALIGHT_VIBRATION
            }
        )
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, JourneyService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /**
     * The lock-screen line. It is written to be read at a glance through a pocket
     * check: the stop you want, and how many are left before it.
     */
    private fun ongoingNotification(progress: JourneyProgress?): Notification {
        val strings = JourneySession.strings
        val title = JourneyText.notificationTitle(progress, strings)
        // The live sighting leads the pocket-check line: "64 arrives in 6 min" is
        // the number the rider pulled the phone out for.
        val banner = JourneyText.liveBanner(
            JourneySession.live.value,
            JourneySession.itinerary.value,
            System.currentTimeMillis(),
            strings
        )
        val body = listOfNotNull(banner, JourneyText.notificationBody(progress, strings))
            .joinToString(" · ")
        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent())
            .addAction(0, strings.journeyEnd, stopIntent())
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun fire(alert: JourneyAlert) {
        // Read at fire time, not at journey start: the rider can flip the setting from
        // the gear menu mid-ride, and the next alert has to honour that rather than a
        // snapshot taken when they boarded. SettingsStore reads straight through to
        // SharedPreferences, so there is nothing cached to go stale.
        if (settings.journeyAlertsEnabled) {
            val strings = JourneySession.strings
            val progress = JourneySession.progress.value
            JourneyText.alertText(alert, progress, strings)?.let { (title, body) ->
                notifier().notify(
                    ALERT_ID,
                    NotificationCompat.Builder(this, CHANNEL_ALERT)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setContentIntent(contentIntent())
                        .setAutoCancel(true)
                        .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_SOUND)
                        .build()
                )
                vibrate()
            }
        }

        // Arrival needs no control flow here: the session lingers on "You've
        // arrived!" for a moment and then clears itself, and the itinerary watcher
        // above takes this service down with it — alerts silenced or not.
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(ALIGHT_VIBRATION, -1))
    }

    companion object {
        const val ACTION_START = "com.automatelinux.pt.journey.START"
        const val ACTION_STOP = "com.automatelinux.pt.journey.STOP"

        private const val CHANNEL_ONGOING = "journey_ongoing"
        private const val CHANNEL_ALERT = "journey_alerts"
        private const val NOTIFICATION_ID = 4201
        private const val ALERT_ID = 4202

        private const val LOCATION_INTERVAL_MS = 5_000L
        private const val LOCATION_MIN_METERS = 15f

        /** Long-short-long: felt through a coat, unlike a single buzz. */
        private val ALIGHT_VIBRATION = longArrayOf(0, 500, 200, 200, 200, 500)
    }
}
