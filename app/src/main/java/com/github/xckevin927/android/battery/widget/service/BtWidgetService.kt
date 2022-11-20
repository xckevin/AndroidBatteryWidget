package com.github.xckevin927.android.battery.widget.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.github.xckevin927.android.battery.widget.App
import com.github.xckevin927.android.battery.widget.R
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper
import com.github.xckevin927.android.battery.widget.utils.BtUtil
import com.github.xckevin927.android.battery.widget.utils.UiUtil
import com.github.xckevin927.android.battery.widget.utils.Utils

class BtWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        return BtRemoteViewsFactory(App.getAppContext())
    }

    class BtRemoteViewsFactory(private val context: Context) : RemoteViewsFactory {

        private var widgetItems: List<BtDeviceState> = listOf()

        override fun onCreate() {
            widgetItems = BatteryRepo.getBtDeviceStates().filter {
                it.isConnected
            }
        }

        override fun onDataSetChanged() {
            widgetItems = BatteryRepo.getBtDeviceStates().filter {
                it.isConnected
            }
        }

        override fun onDestroy() {
        }

        override fun getCount(): Int {
            return widgetItems.size
        }

        @SuppressLint("MissingPermission")
        override fun getViewAt(position: Int): RemoteViews {
            // Construct a remote views item based on the widget item XML file,
            // and set the text based on the position.
            return RemoteViews(context.packageName, R.layout.bt_widget_img).apply {

                try {
                    val deviceState = widgetItems[position]

                    val b = Utils.generateBtBitmap(context, deviceState, BatteryWidgetPrefHelper.getBatteryWidgetPref(context))

                    setImageViewBitmap(R.id.appwidget_bt_indicator, b)

                    //
                    // setTextViewText(R.id.name, widgetItems[position].bluetoothDevice.name)
                    //
                    // if (deviceState.batteryLevel >= 0) {
                    //     setTextViewText(R.id.level, "${widgetItems[position].batteryLevel}%")
                    //     setTextViewCompoundDrawables(R.id.level, R.drawable.ic_battery, 0, 0, 0)
                    // } else {
                    //     setTextViewText(R.id.level, "-")
                    //     setTextViewCompoundDrawables(R.id.level, 0, 0, 0, 0)
                    // }
                    // val info = BtUtil.getBtClassDrawableWithDescription(context, deviceState.bluetoothDevice)
                    // setImageViewBitmap(R.id.img, Bitmap.createBitmap(UiUtil.drawableToBitmap(info.first)))
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
            }
        }

        override fun getLoadingView(): RemoteViews? {
            return null
        }

        override fun getViewTypeCount(): Int {
            return 1
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun hasStableIds(): Boolean {
            return true
        }
    }


}