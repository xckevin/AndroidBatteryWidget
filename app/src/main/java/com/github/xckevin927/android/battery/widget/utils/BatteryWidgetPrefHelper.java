package com.github.xckevin927.android.battery.widget.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService;

public class BatteryWidgetPrefHelper {

    private static final String PREF_NAME = "BATTERY_PREF";

    private static final String BATTERY_PREF_KEY = "BATTERY_PREF_CONFIG_KEY";
    private static final String WIDGET_PREF_PREFIX = "BATTERY_PREF_WIDGET_";

    private static BatteryWidgetPref batteryWidgetPref;

    public static BatteryWidgetPref getDefaultWidgetPref() {
        return new BatteryWidgetPref();
    }

    public static synchronized void saveBatteryWidgetPref(Context context, BatteryWidgetPref batteryWidgetPref) {
        if (batteryWidgetPref == null) {
            return;
        }
        persistBatteryWidgetPref(context, batteryWidgetPref);
        // Never cache a caller-owned draft. A subsequent preview must not alter saved state.
        BatteryWidgetPrefHelper.batteryWidgetPref = batteryWidgetPref.copy();
        WidgetUpdateService.start(context.getApplicationContext());
    }

    public static synchronized BatteryWidgetPref getBatteryWidgetPref(Context context) {
        if (batteryWidgetPref == null) {
            batteryWidgetPref = readLocalBatteryWidgetPref(context);
        }
        return batteryWidgetPref.copy();
    }

    /** Gets an independent configuration. Existing widgets fall back to the old shared preference. */
    public static synchronized BatteryWidgetPref getBatteryWidgetPref(Context context, int appWidgetId) {
        if (appWidgetId == android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
            return getBatteryWidgetPref(context);
        }
        String json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(widgetKey(appWidgetId), null);
        if (TextUtils.isEmpty(json)) {
            // Freeze the old shared appearance for this instance during its first render.
            // Later edits to defaults must only affect widgets added afterwards.
            BatteryWidgetPref migrated = getBatteryWidgetPref(context);
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                    .putString(widgetKey(appWidgetId), JSON.toJSONString(migrated)).apply();
            return migrated;
        }
        BatteryWidgetPref pref = JSON.parseObject(json, BatteryWidgetPref.class);
        return pref == null ? getBatteryWidgetPref(context) : pref.copy();
    }

    public static synchronized void saveBatteryWidgetPref(Context context, int appWidgetId,
                                                           BatteryWidgetPref pref) {
        if (pref == null || appWidgetId == android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
            saveBatteryWidgetPref(context, pref);
            return;
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(widgetKey(appWidgetId), JSON.toJSONString(pref)).apply();
        WidgetUpdateService.start(context.getApplicationContext());
    }

    public static void removeBatteryWidgetPref(Context context, int appWidgetId) {
        if (context != null && appWidgetId != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().remove(widgetKey(appWidgetId)).apply();
        }
    }

    /** Carries an instance's appearance and device choices across launcher backup/restore ID changes. */
    public static synchronized void restoreBatteryWidgetPref(Context context, int oldAppWidgetId,
                                                             int newAppWidgetId) {
        if (context == null || newAppWidgetId == android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) return;
        BatteryWidgetPref old = getBatteryWidgetPref(context, oldAppWidgetId);
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(widgetKey(newAppWidgetId), JSON.toJSONString(old))
                .remove(widgetKey(oldAppWidgetId))
                .apply();
    }

    private static String widgetKey(int appWidgetId) {
        return WIDGET_PREF_PREFIX + appWidgetId;
    }

    private static BatteryWidgetPref readLocalBatteryWidgetPref(Context context) {
        String config = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(BATTERY_PREF_KEY, null);
        if (TextUtils.isEmpty(config)) {
            return new BatteryWidgetPref();
        }
        BatteryWidgetPref pref = JSON.parseObject(config, BatteryWidgetPref.class);
        return pref == null ? new BatteryWidgetPref() : pref;
    }

    private static void persistBatteryWidgetPref(Context context, BatteryWidgetPref batteryWidgetPref) {
        String configStr = JSON.toJSONString(batteryWidgetPref);
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(BATTERY_PREF_KEY, configStr)
                .apply();
    }
}
