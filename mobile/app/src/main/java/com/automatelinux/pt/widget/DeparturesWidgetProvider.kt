package com.automatelinux.pt.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.automatelinux.pt.MainActivity
import com.automatelinux.pt.R
import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.ui.arrivals.formatArrivalTime
import com.automatelinux.pt.util.EnStrings
import com.automatelinux.pt.util.HeStrings
import com.automatelinux.pt.util.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Home-screen widget showing live departures for the station it was pinned for.
 * Pinned from the Station Arrivals tab; each widget instance remembers its own
 * station in SettingsStore. Refreshes on the system update period and on tap of
 * the refresh arrow; tapping the body opens the app on that station.
 */
@AndroidEntryPoint
class DeparturesWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var api: PtApi
    @Inject lateinit var settingsStore: SettingsStore

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_PINNED) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            val code = intent.getStringExtra(EXTRA_STATION_CODE)
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && !code.isNullOrBlank()) {
                settingsStore.setWidgetStation(
                    widgetId, code, intent.getStringExtra(EXTRA_STATION_NAME) ?: ""
                )
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { settingsStore.removeWidgetStation(it) }
    }

    private suspend fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val strings = if (settingsStore.language == "he") HeStrings else EnStrings
        val views = RemoteViews(context.packageName, R.layout.widget_departures)
        val station = settingsStore.getWidgetStation(widgetId)

        ROW_IDS.forEach { views.setViewVisibility(it, View.GONE) }
        views.setViewVisibility(R.id.widgetMessage, View.GONE)

        if (station == null) {
            views.setTextViewText(R.id.widgetStation, "PT")
            showMessage(views, strings.widgetLoadFailed)
        } else {
            val (code, name) = station
            views.setTextViewText(R.id.widgetStation, name.ifBlank { code })
            try {
                val response = withTimeout(8_000) { api.getTransport(station = code) }
                val visits = response.siri?.serviceDelivery?.stopMonitoringDelivery
                    ?.flatMap { it.monitoredStopVisit ?: emptyList() }
                    .orEmpty()
                    .sortedBy {
                        it.monitoredVehicleJourney?.monitoredCall?.expectedArrivalTime ?: "￿"
                    }
                    .take(ROW_IDS.size)

                if (visits.isEmpty()) {
                    showMessage(views, strings.widgetNoDepartures)
                } else {
                    visits.forEachIndexed { i, visit ->
                        val journey = visit.monitoredVehicleJourney ?: return@forEachIndexed
                        val line = journey.publishedLineName ?: "?"
                        val dest = journey.destinationRef?.let {
                            response.stopNames?.get(it) ?: it
                        } ?: ""
                        views.setViewVisibility(ROW_IDS[i], View.VISIBLE)
                        views.setTextViewText(LINE_IDS[i], line)
                        views.setTextViewText(DEST_IDS[i], dest)
                        views.setTextViewText(
                            ETA_IDS[i],
                            formatArrivalTime(
                                journey.monitoredCall?.expectedArrivalTime, 0L, strings
                            ).primary
                        )
                        if (Build.VERSION.SDK_INT >= 31) {
                            views.setColorStateList(
                                LINE_IDS[i], "setBackgroundTintList",
                                ColorStateList.valueOf(lineColorInt(line))
                            )
                        }
                    }
                }
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                views.setTextViewText(
                    R.id.widgetUpdated,
                    now.hour.toString().padStart(2, '0') + ":" +
                        now.minute.toString().padStart(2, '0')
                )
            } catch (e: Exception) {
                showMessage(views, strings.widgetLoadFailed)
            }
        }

        views.setOnClickPendingIntent(R.id.widgetRefresh, refreshIntent(context, widgetId))
        views.setOnClickPendingIntent(R.id.widgetRoot, openAppIntent(context, widgetId, station))
        manager.updateAppWidget(widgetId, views)
    }

    private fun showMessage(views: RemoteViews, text: String) {
        views.setViewVisibility(R.id.widgetMessage, View.VISIBLE)
        views.setTextViewText(R.id.widgetMessage, text)
    }

    private fun refreshIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, DeparturesWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
        return PendingIntent.getBroadcast(
            context, widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(
        context: Context,
        widgetId: Int,
        station: Pair<String, String>?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            station?.let {
                putExtra(EXTRA_STATION_CODE, it.first)
                putExtra(EXTRA_STATION_NAME, it.second)
            }
        }
        return PendingIntent.getActivity(
            context, widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_PINNED = "com.automatelinux.pt.widget.PINNED"
        const val EXTRA_STATION_CODE = "widget_station_code"
        const val EXTRA_STATION_NAME = "widget_station_name"

        private val ROW_IDS = intArrayOf(R.id.row1, R.id.row2, R.id.row3, R.id.row4)
        private val LINE_IDS = intArrayOf(R.id.line1, R.id.line2, R.id.line3, R.id.line4)
        private val DEST_IDS = intArrayOf(R.id.dest1, R.id.dest2, R.id.dest3, R.id.dest4)
        private val ETA_IDS = intArrayOf(R.id.eta1, R.id.eta2, R.id.eta3, R.id.eta4)

        // Same palette as StationArrivals.lineColor, as ARGB ints for RemoteViews.
        private val LINE_COLORS = intArrayOf(
            0xFF4CAF50.toInt(), 0xFFFF9800.toInt(), 0xFF2196F3.toInt(),
            0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF00BCD4.toInt(),
            0xFF795548.toInt(), 0xFFFF5722.toInt(), 0xFF607D8B.toInt(),
            0xFF3F51B5.toInt(),
        )

        private fun lineColorInt(lineName: String): Int {
            val hash = lineName.hashCode().and(0x7FFFFFFF)
            return LINE_COLORS[hash % LINE_COLORS.size]
        }

        /** Ask the launcher to pin a widget for [stationCode]; per-widget mapping is stored on the pin callback. */
        fun requestPin(context: Context, stationCode: String, stationName: String): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            val callback = PendingIntent.getBroadcast(
                context,
                stationCode.hashCode(),
                Intent(context, DeparturesWidgetProvider::class.java).apply {
                    action = ACTION_PINNED
                    putExtra(EXTRA_STATION_CODE, stationCode)
                    putExtra(EXTRA_STATION_NAME, stationName)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            return manager.requestPinAppWidget(
                ComponentName(context, DeparturesWidgetProvider::class.java), null, callback
            )
        }
    }
}
