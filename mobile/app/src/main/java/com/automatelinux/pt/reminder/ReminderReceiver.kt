package com.automatelinux.pt.reminder

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.automatelinux.pt.MainActivity
import com.automatelinux.pt.R
import com.automatelinux.pt.notify.PtSound

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: return
        val text = intent.getStringExtra(ReminderScheduler.EXTRA_TEXT) ?: return
        // Idempotent — channels persist, but recreate in case the post outlives app state.
        ReminderScheduler.ensureChannel(
            context,
            intent.getStringExtra(ReminderScheduler.EXTRA_CHANNEL_NAME) ?: title
        )

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bus)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(Notification.DEFAULT_VIBRATE or Notification.DEFAULT_LIGHTS)
            .setSound(PtSound.uri(context))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        // Its own id per ride: two armed buses are two notifications, and the second
        // must not overwrite the first while the rider is still deciding between them.
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_KEY)
            ?.let(ReminderScheduler::idOf) ?: NOTIFICATION_ID
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
    }
}
