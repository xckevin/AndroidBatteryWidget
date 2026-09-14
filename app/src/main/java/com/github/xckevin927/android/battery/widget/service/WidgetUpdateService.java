package com.github.xckevin927.android.battery.widget.service;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.github.xckevin927.android.battery.widget.Constants;
import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.activity.SettingsActivity;
import com.github.xckevin927.android.battery.widget.appwidget.BatteryWidget;
import com.github.xckevin927.android.battery.widget.appwidget.BtWidget;
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;
import com.github.xckevin927.android.battery.widget.model.PhoneBatteryState;
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo;
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper;
import com.github.xckevin927.android.battery.widget.utils.NotificationUtil;
import com.github.xckevin927.android.battery.widget.utils.Utils;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Optional, user-enabled continuous monitoring. Background jobs only refresh widgets. */
public class WidgetUpdateService extends Service {
    private static volatile boolean running;
    private static volatile String lastStartError;
    private static final String TAG = "WidgetUpdateService";
    private static final int NOTIFICATION_ID = 13364;
    private static final long UPDATE_INTERVAL_MS = 60 * 1000L;
    private static final String ACTION_STOP =
            "com.github.xckevin927.android.battery.widget.STOP_MONITORING";
    private static final String ACTION_BATTERY_LEVEL_CHANGED =
            "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";
    private static final String EXTRA_BATTERY_LEVEL =
            "android.bluetooth.device.extra.BATTERY_LEVEL";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered;
    private boolean bluetoothReceiverRegistered;
    private long lastRefreshElapsedRealtime;
    private int refreshCount;

    private final Runnable refresh = () -> {
        if (isMonitoringEnabled(this) && NotificationUtil.isNotificationEnabled(this)) {
            refreshWidgets(this);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                        .notify(NOTIFICATION_ID, createNotification(true));
            }
            lastRefreshElapsedRealtime = SystemClock.elapsedRealtime();
            refreshCount++;
        } else {
            stopMonitoring();
        }
    };

    private final Runnable periodicRefresh = new Runnable() {
        @Override
        public void run() {
            if (isScreenInteractive()) {
                requestRefresh();
                handler.postDelayed(this, UPDATE_INTERVAL_MS);
            }
        }
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isMonitoringEnabled(WidgetUpdateService.this)
                    || !NotificationUtil.isNotificationEnabled(WidgetUpdateService.this)) {
                stopMonitoring();
                return;
            }
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                handler.removeCallbacks(periodicRefresh);
                return;
            }
            requestRefresh();
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                schedulePeriodicRefresh();
            }
        }
    };

    // Bluetooth broadcasts can originate from the privileged Bluetooth process.
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_BATTERY_LEVEL_CHANGED.equals(intent.getAction())) {
                BatteryRepo.recordFrameworkBatteryReport(
                        reportedDevice(intent),
                        intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1));
            }
            requestRefresh();
        }
    };

    /** Safe for widget, battery and Bluetooth callbacks: never starts a background service. */
    public static void start(@Nullable Context context) {
        if (context != null) {
            refreshWidgets(context.getApplicationContext());
        }
    }

    /** Call from visible activities/user actions, or the permitted boot-completed event. */
    public static void syncMonitoring(Context context) {
        Context appContext = context.getApplicationContext();
        cancelLegacyAlarm(appContext);
        if (!isMonitoringEnabled(appContext) || !NotificationUtil.isNotificationEnabled(appContext)) {
            appContext.stopService(new Intent(appContext, WidgetUpdateService.class));
            ((NotificationManager) appContext.getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
            refreshWidgets(appContext);
            return;
        }
        try {
            ContextCompat.startForegroundService(appContext, new Intent(appContext, WidgetUpdateService.class));
        } catch (IllegalStateException | SecurityException error) {
            running = false;
            lastStartError = error.getClass().getSimpleName();
            // Background starts can be restricted even when permission was granted.
            // The unique periodic worker remains scheduled as a fallback.
            Log.w(TAG, "Monitoring cannot start here; refreshing widgets only", error);
            refreshWidgets(appContext);
        }
    }

    public static boolean isMonitoringEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(Constants.SettingsKey.KEY_SHOW_IN_STATUS_BAR, false);
    }

    public static boolean isMonitoringRunning(Context context) {
        return running && isMonitoringEnabled(context) && NotificationUtil.isNotificationEnabled(context);
    }

    public static String getLastStartError() { return lastStartError; }

    public static int getMonitoringStatusText(Context context) {
        if (!isMonitoringEnabled(context)) return R.string.monitor_disabled;
        if (!NotificationUtil.isNotificationEnabled(context)) return R.string.monitor_permission_blocked;
        return running ? R.string.monitor_running : R.string.monitor_degraded;
    }

    public static void refreshWidgets(Context context) {
        refreshWidgets(context, "widget_refresh");
    }

    public static void refreshWidgets(Context context, String reason) {
        BatteryRepo.refresh(reason);
    }

    /** Render the latest shared snapshot; never initiates another repository refresh. */
    public static void renderWidgets(Context context) {
        Context appContext = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        refreshProvider(appContext, manager, BatteryWidget.class);
        refreshProvider(appContext, manager, BtWidget.class);
    }

    private static void refreshProvider(Context context, AppWidgetManager manager, Class<?> provider) {
        ComponentName component = new ComponentName(context, provider);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids.length > 0) {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .setComponent(component)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            context.sendBroadcast(intent);
        }
    }

    /** Remove the self-restarting alarm left by releases before the SDK 36 migration. */
    public static void cancelLegacyAlarm(Context context) {
        int flags = PendingIntent.FLAG_NO_CREATE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent legacy = PendingIntent.getService(context, 3077,
                new Intent(context, WidgetUpdateService.class), flags);
        if (legacy != null) {
            ((AlarmManager) context.getSystemService(ALARM_SERVICE)).cancel(legacy);
            legacy.cancel();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationUtil.createNotificationChannel(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putBoolean(Constants.SettingsKey.KEY_SHOW_IN_STATUS_BAR, false).apply();
            stopMonitoring();
            return START_NOT_STICKY;
        }
        if (!isMonitoringEnabled(this) || !NotificationUtil.isNotificationEnabled(this)) {
            stopMonitoring();
            return START_NOT_STICKY;
        }

        // Promote before doing widget or Bluetooth work, including on repeated starts.
        Notification notification = createNotification(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        running = true;
        lastStartError = null;

        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            ContextCompat.registerReceiver(this, stateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        }
        if (!bluetoothReceiverRegistered) {
            IntentFilter bluetooth = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            bluetooth.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
            bluetooth.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            bluetooth.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            bluetooth.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            // Hidden platform broadcast complements framework reflection reads; periodic reads
            // still cover devices/ROMs that do not send this event.
            bluetooth.addAction(ACTION_BATTERY_LEVEL_CHANGED);
            ContextCompat.registerReceiver(this, bluetoothReceiver, bluetooth, ContextCompat.RECEIVER_EXPORTED);
            bluetoothReceiverRegistered = true;
        }
        requestRefresh();
        schedulePeriodicRefresh();
        return START_STICKY;
    }

    private boolean isScreenInteractive() {
        return ((PowerManager) getSystemService(POWER_SERVICE)).isInteractive();
    }

    private void schedulePeriodicRefresh() {
        handler.removeCallbacks(periodicRefresh);
        if (isScreenInteractive()) {
            handler.postDelayed(periodicRefresh, UPDATE_INTERVAL_MS);
        }
    }

    private void requestRefresh() {
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 150L);
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private static BluetoothDevice reportedDevice(Intent intent) {
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    }

    private Notification createNotification(boolean includeBattery) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent settings = PendingIntent.getActivity(this, 0,
                new Intent(this, SettingsActivity.class), flags);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, WidgetUpdateService.class).setAction(ACTION_STOP), flags);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_indicator_title))
                .setContentText(getString(R.string.monitoring_battery_changes))
                .setSmallIcon(R.drawable.ic_battery)
                .setContentIntent(settings)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop_monitoring), stop)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(true);
        // The first startForeground call must not wait for battery reads or bitmap rendering.
        if (includeBattery) {
            PhoneBatteryState battery = BatteryRepo.INSTANCE.getBatteryState();
            int level = battery.getLevel();
            int icon = getResources().getIdentifier("white_round_" + level, "drawable", getPackageName());
            BatteryWidgetPref pref = BatteryWidgetPrefHelper.getDefaultWidgetPref();
            pref.setShowBackground(false);
            pref.setShowBackgroundProgress(false);
            builder.setSmallIcon(icon == 0 ? R.drawable.ic_battery : icon)
                    .setContentText(com.github.xckevin927.android.battery.widget.utils.BatteryStatusText.phone(this, battery))
                    .setLargeIcon(Utils.generateBatteryBitmap(this, battery, pref));
        }
        return builder.build();
    }

    private void stopMonitoring() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        stopForeground(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            unregisterReceiver(stateReceiver);
        }
        if (bluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothReceiver);
        }
        stopForeground(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("monitoringEnabled=" + isMonitoringEnabled(this));
        writer.println("monitoringRunning=" + isMonitoringRunning(this));
        writer.println("lastStartError=" + lastStartError);
        writer.println("screenInteractive=" + isScreenInteractive());
        writer.println("refreshCount=" + refreshCount);
        writer.println("lastRefreshElapsedRealtime=" + lastRefreshElapsedRealtime);
        writer.println("batteryLevel=" + BatteryRepo.INSTANCE.getBatteryState().getLevel());
    }
}
