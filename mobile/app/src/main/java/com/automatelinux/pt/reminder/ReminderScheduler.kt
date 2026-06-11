package com.automatelinux.pt.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

// Departure reminders via AlarmManager so they survive the app being killed.
// One reminder can be armed at a time (fixed request code), matching the UI,
// which exposes a single armed reminder.
object ReminderScheduler {
    const val CHANNEL_ID = "departure_reminders"
    const val EXTRA_TITLE = "reminder_title"
    const val EXTRA_TEXT = "reminder_text"
    private const val REQUEST_CODE = 1001

    fun ensureChannel(context: Context, channelName: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun schedule(
        context: Context,
        triggerAtMillis: Long,
        title: String,
        text: String,
        channelName: String
    ) {
        ensureChannel(context, channelName)
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_TEXT, text)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        // USE_EXACT_ALARM is declared in the manifest, so exact alarms are always allowed.
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
        )
    }

    fun cancel(context: Context) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
