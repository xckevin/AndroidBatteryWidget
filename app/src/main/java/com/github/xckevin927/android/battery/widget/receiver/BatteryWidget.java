package com.github.xckevin927.android.battery.widget.receiver;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.widget.RemoteViews;

import com.github.xckevin927.android.battery.widget.PhoneBatteryState;
import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.activity.BatteryWidgetConfigureActivity;
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService;
import com.github.xckevin927.android.battery.widget.utils.BatteryUtil;
import com.github.xckevin927.android.battery.widget.utils.Utils;

/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in {@link BatteryWidgetConfigureActivity BatteryWidgetConfigureActivity}
 */
public class BatteryWidget extends AppWidgetProvider {

    private static final String TAG = "BatteryWidget";

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                       int appWidgetId) {

        PhoneBatteryState batteryState = BatteryUtil.getBatteryState(context);
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.battery_widget);

        Bitmap b = Utils.generateBatteryBitmap(context, batteryState);

        views.setImageViewBitmap(R.id.appwidget_progress, b);

//        Bitmap bitmap = getBackground(width, height);
//
//        views.setImageViewBitmap(R.id.appwidget_background, bitmap);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }



    private static Bitmap getBackground(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(width/3F);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);

        c.drawRoundRect(0, 0, width, height, 8, 8, paint);

        return bitmap;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetUpdateService.start(context.getApplicationContext());
        Log.e(TAG, "onUpdate: ");
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // When the user deletes the widget, delete the preference associated with it.
        WidgetUpdateService.start(context.getApplicationContext());
        for (int appWidgetId : appWidgetIds) {
            BatteryWidgetConfigureActivity.deleteTitlePref(context, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {
        WidgetUpdateService.start(context.getApplicationContext());
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}