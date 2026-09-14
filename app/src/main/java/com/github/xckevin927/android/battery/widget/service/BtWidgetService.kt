package com.github.xckevin927.android.battery.widget.service

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.github.xckevin927.android.battery.widget.App
import com.github.xckevin927.android.battery.widget.R
import com.github.xckevin927.android.battery.widget.appwidget.WidgetConstants
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo
import com.github.xckevin927.android.battery.widget.utils.BatteryStatusText
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper
import com.github.xckevin927.android.battery.widget.utils.DevicePreferences
import com.github.xckevin927.android.battery.widget.utils.Utils

/**
 * One factory serves both widget providers. The input list is a cached repository snapshot:
 * RemoteViews rendering never starts a Bluetooth scan or connection.
 */
class BtWidgetService : RemoteViewsService() {
    companion object {
        /**
         * The exact device list rendered by a widget instance. Keeping this selection here
         * lets the single-device card and the collection renderer make the same decision.
         */
        @JvmStatic
        fun getWidgetItems(context: Context, pref: BatteryWidgetPref): List<BtDeviceState> {
            val orderedVisible = DevicePreferences.visibleDevices(context, BatteryRepo.getBtSnapshot())
            if (pref.isShowAllVisibleDevices) return orderedVisible

            val selected = pref.selectedDeviceAddresses.toSet()
            return orderedVisible.filter { state ->
                addressOf(state)?.let(selected::contains) == true
            }
        }

        @SuppressLint("MissingPermission")
        private fun addressOf(state: BtDeviceState): String? = try {
            state.bluetoothDevice.address
        } catch (_: SecurityException) {
            null
        }
    }

    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        return BtRemoteViewsFactory(App.getAppContext(), id)
    }

    class BtRemoteViewsFactory(private val context: Context, private val widgetId: Int) : RemoteViewsFactory {
        private var widgetItems: List<BtDeviceState> = emptyList()
        private var pref = BatteryWidgetPref()
        override fun onCreate() {
            refreshItems()
        }
        override fun onDestroy() = Unit
        override fun onDataSetChanged() { refreshItems() }
        private fun refreshItems() {
            pref = BatteryWidgetPrefHelper.getBatteryWidgetPref(context, widgetId)
            widgetItems = BtWidgetService.getWidgetItems(context, pref)
        }
        override fun getCount() = widgetItems.size

        @SuppressLint("MissingPermission")
        override fun getViewAt(position: Int): RemoteViews {
            val state = widgetItems.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.bt_widget_img)
            return RemoteViews(context.packageName, R.layout.bt_widget_img).apply {
                val bitmap: Bitmap = Utils.generateBtBitmap(context, state, pref)
                setImageViewBitmap(R.id.appwidget_bt_indicator, bitmap)
                val name = DevicePreferences.displayName(context, state)
                setContentDescription(R.id.appwidget_bt_indicator, context.getString(
                    R.string.widget_device_content, name,
                    BatteryStatusText.bluetooth(context, state) + ". " + BatteryStatusText.freshness(context, state)))
                val address = addressOf(state)
                if (address != null) {
                    setOnClickFillInIntent(R.id.appwidget_bt_indicator, Intent()
                        .putExtra(WidgetConstants.EXTRA_DEVICE_ADDRESS, address))
                }
            }
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds() = false
        private fun addressOf(state: BtDeviceState): String? = try {
            state.bluetoothDevice.address
        } catch (_: SecurityException) {
            null
        }
    }
}
