package com.github.xckevin927.android.battery.widget.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService;

public class BatteryChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "BatteryChangeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e(TAG, "onReceive: " + intent.getAction());
        WidgetUpdateService.start(context.getApplicationContext());
    }
}