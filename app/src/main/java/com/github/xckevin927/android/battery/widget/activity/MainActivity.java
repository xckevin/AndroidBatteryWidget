package com.github.xckevin927.android.battery.widget.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.receiver.BatteryWidget;
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WidgetUpdateService.start(getApplicationContext());

        setContentView(R.layout.activity_main);
        findViewById(R.id.add_widget).setOnClickListener(v -> requestToPinWidget());
    }

    private void requestToPinWidget(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager appWidgetManager = getSystemService(AppWidgetManager.class);
            ComponentName myProvider = new ComponentName(this, BatteryWidget.class);
            assert appWidgetManager != null;
            if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                Intent pinnedWidgetCallbackIntent = new Intent(this, MainActivity.class);
                PendingIntent successCallback = PendingIntent.getBroadcast(this, 0,
                        pinnedWidgetCallbackIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                appWidgetManager.requestPinAppWidget(myProvider, null, successCallback);
            }
        } else {
            // not supported
//            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
//
//            startActivity(intent);
        }
    }
}