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

    private static BatteryWidgetPref batteryWidgetPref;

    public static void saveBatteryWidgetPref(Context context, BatteryWidgetPref batteryWidgetPref) {
        if (batteryWidgetPref == null) {
            return;
        }
        persistBatteryWidgetPref(context, batteryWidgetPref);
        BatteryWidgetPrefHelper.batteryWidgetPref = batteryWidgetPref;
        WidgetUpdateService.start(context.getApplicationContext());
    }

    public static BatteryWidgetPref getBatteryWidgetPref(Context context) {
        if (batteryWidgetPref == null) {
            batteryWidgetPref = readLocalBatteryWidgetPref(context);
        }
        return batteryWidgetPref;
    }

    private static BatteryWidgetPref readLocalBatteryWidgetPref(Context context) {
        String config = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(BATTERY_PREF_KEY, null);
        if (TextUtils.isEmpty(config)) {
            return new BatteryWidgetPref();
        }
        return JSON.parseObject(config, BatteryWidgetPref.class);
    }

    private static void persistBatteryWidgetPref(Context context, BatteryWidgetPref batteryWidgetPref) {
        String configStr = JSON.toJSONString(batteryWidgetPref);
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(BATTERY_PREF_KEY, configStr)
                .apply();
    }
}
