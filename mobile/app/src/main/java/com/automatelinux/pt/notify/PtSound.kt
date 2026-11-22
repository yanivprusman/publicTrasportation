package com.automatelinux.pt.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import com.automatelinux.pt.R

/**
 * PT's own notification sound. Without it every PT channel played the phone's
 * default notification sound — the same one WhatsApp uses — so a "get off at the
 * next stop" alert was indistinguishable from a chat message.
 *
 * Android freezes a channel's sound when the channel is first created, so changing
 * it means a new channel id. [RETIRED_CHANNELS] are the ids that were created with
 * the default sound; they are deleted so they stop showing in the app's settings.
 */
object PtSound {
    private val RETIRED_CHANNELS = listOf("journey_alerts", "departure_reminders")

    fun uri(context: Context): Uri = Uri.parse(
        "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.pt_chime}"
    )

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun apply(context: Context, channel: NotificationChannel): NotificationChannel =
        channel.apply { setSound(uri(context), attributes) }

    fun retireOldChannels(manager: NotificationManager) {
        RETIRED_CHANNELS.forEach { manager.deleteNotificationChannel(it) }
    }
}
