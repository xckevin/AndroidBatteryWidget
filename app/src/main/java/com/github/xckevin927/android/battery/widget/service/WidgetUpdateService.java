package com.github.xckevin927.android.battery.widget.service;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.xckevin927.android.battery.widget.receiver.BatteryChangeReceiver;
import com.github.xckevin927.android.battery.widget.receiver.BatteryWidget;
import com.github.xckevin927.android.battery.widget.receiver.PhoneStatusChangeReceiver;

public class WidgetUpdateService extends Service {

    private static final String TAG = "WidgetUpdateService";

    private static final int ALARM_DURATION = 15 * 60 * 1000; // service 自启间隔
    private static final int UPDATE_DURATION = 3 * 60 * 1000;     // Widget 更新间隔
    private static final int MESSAGE_SCHEDULE = 1000;
    private static final int MESSAGE_UPDATE = 1001;

    public static final String COMMAND_KEY = "extra_command";
    public static final String COMMAND_UPDATE = "command_update";

    private static boolean isServiceAlive = false;

    private static IUpdateServiceAidlInterface updateServiceAidlInterface;

    private UpdateHandler updateHandler;

    private BatteryChangeReceiver batteryChangeReceiver;
    private BroadcastReceiver statusChangeReceiver;

    public static void start(Context context) {
        start(context, true);
    }

    public static void start(Context context, boolean needUpdateWidget) {
        Intent intent = new Intent(context, WidgetUpdateService.class);
        if (needUpdateWidget) {
            intent.putExtra(COMMAND_KEY, COMMAND_UPDATE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (updateServiceAidlInterface == null) {
                context.bindService(intent, new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        updateServiceAidlInterface = IUpdateServiceAidlInterface.Stub.asInterface(service);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        updateServiceAidlInterface = null;
                    }
                }, BIND_AUTO_CREATE);
            } else {
                if (needUpdateWidget) {
                    try {
                        updateServiceAidlInterface.updateRemoteWidget();
                    } catch (RemoteException e) {
                        Log.e(TAG, "remote update widget error", e);
                    }
                }
            }
        } else {
            ContextCompat.startForegroundService(context, intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(0, new Notification());

        updateHandler = new UpdateHandler();
        postUpdateMsg();
        postScheduleMsg();

        batteryChangeReceiver = new BatteryChangeReceiver();
        registerReceiver(batteryChangeReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        statusChangeReceiver = new PhoneStatusChangeReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(statusChangeReceiver, intentFilter);

        ensureServiceAlive();
        isServiceAlive = true;
        Log.e(TAG, "onCreate: ");
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private void ensureServiceAlive() {
        // 每个 ALARM_DURATION 自启一次
        AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent alarmIntent = new Intent(getBaseContext(), WidgetUpdateService.class);
        PendingIntent pendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getService(getBaseContext(), 3077,
                    alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        } else {
            pendingIntent = PendingIntent.getService(getBaseContext(), 3077,
                    alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        manager.setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + ALARM_DURATION, ALARM_DURATION, pendingIntent);
    }

    private void postScheduleMsg() {
        if (updateHandler == null) {
            return;
        }
        updateHandler.removeMessages(MESSAGE_SCHEDULE);
        Message message = updateHandler.obtainMessage();
        message.what = MESSAGE_SCHEDULE;
        updateHandler.sendMessageDelayed(message, UPDATE_DURATION);
    }

    private void postUpdateMsg() {
        if (updateHandler == null) {
            return;
        }
        updateHandler.removeMessages(MESSAGE_UPDATE);
        Message message = updateHandler.obtainMessage();
        message.what = MESSAGE_UPDATE;
        updateHandler.sendMessageDelayed(message, 1000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && TextUtils.equals(COMMAND_UPDATE, intent.getStringExtra(COMMAND_KEY))) {
            updateWidget();
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        ensureServiceAlive();
    }

    private void updateWidget() {
        Log.i(TAG, "updateWidget");
        postUpdateMsg();
        postScheduleMsg();
    }

    private void doUpdateWidget() {
        Log.i(TAG, "doUpdateWidget: ");
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getApplicationContext());
//        appWidgetManager.updateAppWidget(new ComponentName(getApplicationContext(), MyWidgetProvider.class), remoteViews);

        Intent updateIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(getApplicationContext(), BatteryWidget.class));
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        sendBroadcast(updateIntent);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        ensureServiceAlive();
        unregisterReceiver(batteryChangeReceiver);
        unregisterReceiver(statusChangeReceiver);
        updateHandler.removeCallbacks(null);
        updateHandler = null;
        isServiceAlive = false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new IUpdateServiceAidlInterface.Stub() {
            @Override
            public void updateRemoteWidget() throws RemoteException {
                updateWidget();
            }
        };
    }

    private final class UpdateHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_SCHEDULE) {
                updateWidget();
            } else if (msg.what == MESSAGE_UPDATE) {
                doUpdateWidget();
            }
        }
    }

}
