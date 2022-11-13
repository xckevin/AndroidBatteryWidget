package com.github.xckevin927.android.battery.widget.appwidget;

import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.model.PhoneBatteryState;
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo;
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper;
import com.github.xckevin927.android.battery.widget.utils.Utils;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

/**
 * Implementation of App Widget functionality.
 */
public class BatteryWidget extends BaseWidgetProvider {

    private static final String TAG = "BatteryWidget";

    public BatteryWidget() {
        super(WidgetConstants.TYPE_PHONE_BATTERY);
    }

    @Override
    public void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {

        PhoneBatteryState batteryState = BatteryRepo.INSTANCE.getBatteryState();
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.battery_widget);

        Bitmap b = Utils.generateBatteryBitmap(context, batteryState, BatteryWidgetPrefHelper.getBatteryWidgetPref(context));

        views.setImageViewBitmap(R.id.appwidget_progress, b);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

}