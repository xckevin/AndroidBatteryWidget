package com.github.xckevin927.android.battery.widget.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }
        Log.e(TAG, "onReceive: " + intent.getAction());
        BatteryWorker.Companion.start(context);
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            WidgetUpdateService.syncMonitoring(context);
        } else {
            WidgetUpdateService.refreshWidgets(context);
        }
    }
}
