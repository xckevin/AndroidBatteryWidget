package com.github.xckevin927.android.battery.widget.appwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.activity.SettingsActivity;
import com.github.xckevin927.android.battery.widget.activity.TabActivity;
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;
import com.github.xckevin927.android.battery.widget.model.BtDeviceState;
import com.github.xckevin927.android.battery.widget.model.PhoneBatteryState;
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo;
import com.github.xckevin927.android.battery.widget.service.BtWidgetService;
import com.github.xckevin927.android.battery.widget.utils.BatteryStatusText;
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper;
import com.github.xckevin927.android.battery.widget.utils.DevicePreferences;
import com.github.xckevin927.android.battery.widget.utils.Utils;
import com.github.xckevin927.android.battery.widget.billing.ProBilling;

import java.util.List;

/** Phone widget. It expands to show selected Bluetooth devices when space permits. */
public class BatteryWidget extends BaseWidgetProvider {
    public BatteryWidget() { super(WidgetConstants.TYPE_PHONE_BATTERY); }

    public static void updateWidget(Context context, int appWidgetId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        new BatteryWidget().updateAppWidget(context, manager, appWidgetId);
    }

    @Override public void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        BatteryWidgetPref pref = BatteryWidgetPrefHelper.getBatteryWidgetPref(context, appWidgetId);
        PhoneBatteryState batteryState = BatteryRepo.INSTANCE.getBatteryState();
        WidgetLayoutPolicy.Mode layoutMode = WidgetLayoutPolicy.resolve(pref.getWidgetStyle(),
                option(appWidgetManager.getAppWidgetOptions(appWidgetId), AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                option(appWidgetManager.getAppWidgetOptions(appWidgetId), AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                ProBilling.hasPro(context));
        RemoteViews views = new RemoteViews(context.getPackageName(),
                layoutMode == WidgetLayoutPolicy.Mode.HORIZONTAL_COMBINED
                        ? R.layout.battery_widget_horizontal : R.layout.battery_widget);
        Bitmap bitmap = Utils.generateBatteryBitmap(context, batteryState, pref);
        views.setImageViewBitmap(R.id.appwidget_progress, bitmap);
        views.setViewVisibility(R.id.widget_preview_devices, View.GONE);
        String phoneStatus = BatteryStatusText.phone(context, batteryState);

        boolean devicesOnly = layoutMode == WidgetLayoutPolicy.Mode.DEVICES;
        boolean showDevices = devicesOnly || layoutMode == WidgetLayoutPolicy.Mode.VERTICAL_COMBINED
                || layoutMode == WidgetLayoutPolicy.Mode.HORIZONTAL_COMBINED;
        List<BtDeviceState> widgetItems = showDevices
                ? BtWidgetService.getWidgetItems(context, pref) : java.util.Collections.emptyList();
        // A collection item has wrap-content height. Render one device directly so its card gets
        // precisely the same inner bounds and centerInside bitmap sizing as phone_area.
        boolean showSingleDevice = !devicesOnly && widgetItems.size() == 1;
        views.setViewVisibility(R.id.phone_area, devicesOnly ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.widget_devices_container_widget, showDevices ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_single_device_area, showSingleDevice ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_devices, showDevices && !showSingleDevice ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_devices_empty, showDevices && !showSingleDevice ? View.VISIBLE : View.GONE);
        if (showSingleDevice) {
            BtDeviceState state = widgetItems.get(0);
            views.setImageViewBitmap(R.id.widget_single_device_indicator,
                    Utils.generateBtBitmap(context, state, pref));
            String name = DevicePreferences.displayName(context, state);
            views.setContentDescription(R.id.widget_single_device_indicator, context.getString(
                    R.string.widget_device_content, name,
                    BatteryStatusText.bluetooth(context, state) + ". " + BatteryStatusText.freshness(context, state)));
            String address = deviceAddress(state);
            if (address != null) {
                PendingIntent intent = deviceIntent(context, appWidgetId, address);
                views.setOnClickPendingIntent(R.id.widget_single_device_area, intent);
                views.setOnClickPendingIntent(R.id.widget_single_device_indicator, intent);
            }
        } else if (showDevices) {
            Intent adapter = new Intent(context, BtWidgetService.class)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    .putExtra(WidgetConstants.EXTRA_TYPE, WidgetConstants.TYPE_PHONE_BATTERY)
                    .setData(Uri.parse("battery-widget://" + appWidgetId));
            views.setRemoteAdapter(R.id.widget_devices, adapter);
            views.setEmptyView(R.id.widget_devices, R.id.widget_devices_empty);
            views.setPendingIntentTemplate(R.id.widget_devices, deviceTemplate(context, appWidgetId));
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_devices);
        }
        boolean opensSelectedDevice = BatteryWidgetPref.CLICK_DEVICE.equals(pref.getClickAction())
                && !pref.getSelectedDeviceAddresses().isEmpty();
        if (opensSelectedDevice) {
            String address = pref.getSelectedDeviceAddresses().get(0);
            views.setOnClickPendingIntent(R.id.phone_area, deviceIntent(context, appWidgetId, address));
            views.setContentDescription(R.id.appwidget_progress, context.getString(
                    R.string.widget_phone_content_device, phoneStatus, selectedDeviceName(context, address)));
        } else {
            views.setOnClickPendingIntent(R.id.phone_area, statusIntent(context, appWidgetId));
            views.setContentDescription(R.id.appwidget_progress,
                    context.getString(R.string.widget_phone_content, phoneStatus));
        }
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static int option(Bundle options, String key) {
        return options == null ? 0 : options.getInt(key, 0);
    }

    private static String selectedDeviceName(Context context, String address) {
        for (com.github.xckevin927.android.battery.widget.model.BtDeviceState state
                : BatteryRepo.INSTANCE.getBtSnapshot()) {
            try {
                if (address.equals(state.getBluetoothDevice().getAddress())) {
                    return DevicePreferences.displayName(context, state);
                }
            } catch (SecurityException ignored) {
                // The pending intent remains valid even when the user revokes device-name permission.
            }
        }
        return context.getString(R.string.widget_selected_device);
    }

    private static String deviceAddress(BtDeviceState state) {
        try {
            return state.getBluetoothDevice().getAddress();
        } catch (SecurityException ignored) {
            return null;
        }
    }

    static PendingIntent statusIntent(Context context, int widgetId) {
        Intent intent = new Intent(context, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP).setAction("widget-status-" + widgetId);
        return PendingIntent.getActivity(context, widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent deviceTemplate(Context context, int widgetId) {
        Intent intent = new Intent(context, TabActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(WidgetConstants.EXTRA_OPEN_TAB, WidgetConstants.TAB_BLUETOOTH)
                .setData(Uri.parse("battery-device-template://" + widgetId));
        return PendingIntent.getActivity(context, widgetId + 10000, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }
    static PendingIntent deviceIntent(Context context, int widgetId, String address) {
        Intent intent = new Intent(context, TabActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(WidgetConstants.EXTRA_OPEN_TAB, WidgetConstants.TAB_BLUETOOTH)
                .putExtra(WidgetConstants.EXTRA_DEVICE_ADDRESS, address)
                .setAction("widget-device-" + widgetId + "-" + address);
        return PendingIntent.getActivity(context, widgetId + 20000, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
