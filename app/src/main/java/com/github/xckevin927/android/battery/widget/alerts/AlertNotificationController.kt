package com.github.xckevin927.android.battery.widget.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.github.xckevin927.android.battery.widget.R
import com.github.xckevin927.android.battery.widget.activity.AlertsActivity

object AlertNotificationController {
    const val CHANNEL_ID = "battery_alerts"
    private const val PHONE_NOTIFICATION_ID = 13_365
    private const val BLUETOOTH_NOTIFICATION_BASE = 20_000

    @JvmStatic
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alert_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alert_notification_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    @JvmStatic
    fun canPostNotifications(context: Context): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            val channel = manager.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    /** Returns delivery keys for notifications accepted by NotificationManager. */
    @SuppressLint("MissingPermission")
    internal fun post(context: Context, alerts: List<BatteryAlert>): Set<String> {
        if (alerts.isEmpty()) return emptySet()
        createChannel(context)
        if (!canPostNotifications(context)) return emptySet()
        val manager = NotificationManagerCompat.from(context)
        val delivered = linkedSetOf<String>()
        alerts.forEach { alert ->
            try {
                manager.notify(notificationId(alert), buildNotification(context, alert))
                delivered += alert.deliveryKey
            } catch (_: SecurityException) {
                // Runtime permission or AppOps changed between the check and notify().
            } catch (_: RuntimeException) {
                // Keep the event armed; a later fresh evaluation may retry delivery.
            }
        }
        return delivered
    }

    @JvmStatic
    fun openNotificationSettings(context: Context) {
        createChannel(context)
        val channelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        channelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(channelIntent)
        } catch (_: RuntimeException) {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    private fun buildNotification(context: Context, alert: BatteryAlert): Notification {
        val title: String
        val content: String
        when (alert) {
            is BatteryAlert.PhoneChargeReached -> {
                title = context.getString(R.string.alert_phone_notification_title)
                content = context.getString(
                    R.string.alert_phone_notification_content,
                    alert.level,
                    alert.threshold
                )
            }
            is BatteryAlert.BluetoothLow -> {
                title = context.getString(
                    R.string.alert_bluetooth_notification_title,
                    alert.displayName
                )
                content = context.getString(
                    R.string.alert_bluetooth_notification_content,
                    alert.level,
                    alert.threshold
                )
            }
        }
        val id = notificationId(alert)
        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, AlertsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun notificationId(alert: BatteryAlert): Int = when (alert) {
        is BatteryAlert.PhoneChargeReached -> PHONE_NOTIFICATION_ID
        is BatteryAlert.BluetoothLow ->
            BLUETOOTH_NOTIFICATION_BASE + (alert.deviceId.hashCode() and 0x0fffffff)
    }
}
