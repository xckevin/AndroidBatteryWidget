package com.github.xckevin927.android.battery.widget.activity;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;

import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.activity.fragment.phone.BatteryWidgetConfigFragment;
import com.github.xckevin927.android.battery.widget.appwidget.BatteryWidget;
import com.github.xckevin927.android.battery.widget.appwidget.BtWidget;

public class MainActivity extends BaseActivity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    public int getAppWidgetId() {
        return appWidgetId;
    }

    public boolean isWidgetConfiguration() {
        return appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID;
    }

    public boolean isBtWidgetConfiguration() {
        if (!isWidgetConfiguration()) return false;
        android.appwidget.AppWidgetProviderInfo info =
                AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId);
        return info != null && new android.content.ComponentName(this, BtWidget.class).equals(info.provider);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        // Android treats a configure Activity as cancelled unless it explicitly changes this.
        if (isWidgetConfiguration()) {
            setResult(Activity.RESULT_CANCELED);
        }
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.id_content_activity_main,
                            BatteryWidgetConfigFragment.newInstance(appWidgetId))
                    .commit();
        }
    }

    /** Completes the launcher configure contract only after the first update has been requested. */
    public void completeWidgetConfiguration() {
        if (!isWidgetConfiguration()) return;
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    /** The same editor configures either provider; launchers pass only the instance ID. */
    public void updateConfiguredWidget() {
        if (!isWidgetConfiguration()) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (isBtWidgetConfiguration()) {
            new BtWidget().updateAppWidget(this, manager, appWidgetId);
        } else {
            BatteryWidget.updateWidget(this, appWidgetId);
        }
    }
}
