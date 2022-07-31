package com.github.xckevin927.android.battery.widget.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService;

public class BtConnectReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        WidgetUpdateService.start(context.getApplicationContext());
    }
}