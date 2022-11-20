package com.github.xckevin927.android.battery.widget.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService

abstract class BaseWidgetProvider(val type: Int) : AppWidgetProvider() {

    private val TAG = "BaseWidgetProvider"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra(WidgetConstants.EXTRA_TYPE) && type != intent.getIntExtra(WidgetConstants.EXTRA_TYPE, -1)) {
            Log.e(TAG, "filtered action ${intent.action}")
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray?) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds?.forEach {
            updateAppWidget(context, appWidgetManager, it)
        }
    }

    abstract fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int)

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        WidgetUpdateService.start(context)
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
        WidgetUpdateService.start(context)
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
        WidgetUpdateService.start(context)
    }
}