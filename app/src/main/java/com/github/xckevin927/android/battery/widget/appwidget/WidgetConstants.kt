package com.github.xckevin927.android.battery.widget.appwidget

import com.github.xckevin927.android.battery.widget.BuildConfig

interface WidgetConstants {

    companion object {
        const val EXTRA_TYPE = "${BuildConfig.APPLICATION_ID}.extra_type"
        const val TYPE_PHONE_BATTERY = 1
        const val TYPE_BT_BATTERY = 2
        const val EXTRA_DEVICE_ADDRESS = "${BuildConfig.APPLICATION_ID}.extra_device_address"
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_BLUETOOTH = "bluetooth"
    }

}
