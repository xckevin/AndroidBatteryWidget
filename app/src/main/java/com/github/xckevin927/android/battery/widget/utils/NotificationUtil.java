package com.github.xckevin927.android.battery.widget.utils;

import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;

import com.github.xckevin927.android.battery.widget.Constants;
import com.github.xckevin927.android.battery.widget.R;

public class NotificationUtil {

    public static boolean isNotificationEnabled(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
            NotificationChannelCompat channelCompat = new NotificationChannelCompat.Builder(Constants.NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.notification_channel_title))
                    .setDescription(context.getString(R.string.notification_channel_desc))
                    .setShowBadge(false)
                    .build();
            notificationManagerCompat.createNotificationChannel(channelCompat);
        }
    }

    public static void openNotificationSetting(Context context) {
        Intent intent = new Intent();
        String packageName = context.getPackageName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
        } else {
            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
            intent.putExtra("app_package", packageName);
            intent.putExtra("app_uid", context.getApplicationInfo().uid);
        }
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException ignore) {
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setData(Uri.parse("package:" + packageName));
            context.startActivity(intent);
        }
    }
}
