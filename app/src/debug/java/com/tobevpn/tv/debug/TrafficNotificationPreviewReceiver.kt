package com.tobevpn.tv.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tobevpn.tv.MainActivity
import com.tobevpn.tv.R

/** Debug-only preview of the production traffic-balance notification. */
class TrafficNotificationPreviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val remainingGib = intent.getStringExtra(EXTRA_REMAINING_GIB) ?: "6"
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.traffic_limit_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val description = context.getString(
            R.string.traffic_limit_notification_description,
            remainingGib,
        )
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.traffic_limit_notification_title))
                .setContentText(description)
                .setStyle(Notification.BigTextStyle().bigText(description))
                .setSmallIcon(R.drawable.ic_notification_tobevpn)
                .setColor(context.getColor(R.color.notification_icon_color))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build(),
        )
    }

    private companion object {
        const val EXTRA_REMAINING_GIB = "remaining_gib"
        const val CHANNEL_ID = "traffic_limit_alerts"
        const val NOTIFICATION_ID = 2003
    }
}
