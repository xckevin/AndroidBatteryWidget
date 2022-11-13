package com.github.xckevin927.android.battery.widget.appwidget

import com.github.xckevin927.android.battery.widget.BuildConfig

interface WidgetConstants {

    companion object {
        const val EXTRA_TYPE = "${BuildConfig.APPLICATION_ID}.extra_type"
        const val TYPE_PHONE_BATTERY = 1
        const val TYPE_BT_BATTERY = 2
    }

}