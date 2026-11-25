package com.automatelinux.pt.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.automatelinux.pt.notify.PtSound

// Departure reminders via AlarmManager so they survive the app being killed.
//
// Any number can be armed at once, one per ride (see RouteLeg.reminderKey): a rider
// choosing between a 64 and a 65 arms both and takes whichever comes first. Each has
// its own alarm (the request code is the key's hash) and its own notification, so one
// firing neither replaces nor cancels another. The armed set is kept in preferences —
// the alarms outlive the process, so the buttons that cancel them must too.
object ReminderScheduler {
    const val CHANNEL_ID = "departure_reminders_chime"
    const val EXTRA_TITLE = "reminder_title"
    const val EXTRA_TEXT = "reminder_text"
    const val EXTRA_CHANNEL_NAME = "reminder_channel_name"
    const val EXTRA_KEY = "reminder_key"
    private const val PREFS = "departure_reminders"
    private const val ARMED = "armed"
    private const val SEP = '\u0001'

    /** Alarm request code and notification id of the reminder for [key]. */
    fun idOf(key: String): Int = key.hashCode()

    fun ensureChannel(context: Context, channelName: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            PtSound.apply(
                context,
                NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH)
            )
        )
        PtSound.retireOldChannels(manager)
    }

    fun schedule(
        context: Context,
        key: String,
        triggerAtMillis: Long,
        title: String,
        text: String,
        channelName: String
    ) {
        ensureChannel(context, channelName)
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_TEXT, text)
            .putExtra(EXTRA_CHANNEL_NAME, channelName)
            .putExtra(EXTRA_KEY, key)
        val pendingIntent = PendingIntent.getBroadcast(
            context, idOf(key), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        // USE_EXACT_ALARM is declared in the manifest, so exact alarms are always allowed.
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
        )
        write(context, read(context) + (key to triggerAtMillis))
    }

    fun cancel(context: Context, key: String) {
        write(context, read(context) - key)
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, idOf(key), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /** The reminders still waiting to fire, each with the moment it will. */
    fun armed(context: Context, nowMillis: Long = System.currentTimeMillis()): Map<String, Long> {
        val all = read(context)
        val waiting = all.filterValues { it > nowMillis }
        if (waiting.size != all.size) write(context, waiting)
        return waiting
    }

    private fun read(context: Context): Map<String, Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(ARMED, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val at = entry.substringBefore(SEP).toLongOrNull() ?: return@mapNotNull null
                entry.substringAfter(SEP) to at
            }
            .toMap()

    private fun write(context: Context, armed: Map<String, Long>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(ARMED, armed.map { (key, at) -> "$at$SEP$key" }.toSet())
            .apply()
    }
}
