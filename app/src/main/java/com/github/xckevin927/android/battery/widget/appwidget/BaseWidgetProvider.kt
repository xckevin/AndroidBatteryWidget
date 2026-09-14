package com.github.xckevin927.android.battery.widget.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper

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
        if (context != null) {
            appWidgetIds?.forEach { BatteryWidgetPrefHelper.removeBatteryWidgetPref(context, it) }
        }
        WidgetUpdateService.start(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Re-evaluate the compact/combined layout after a launcher resize.
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onRestored(
        context: Context,
        oldWidgetIds: IntArray,
        newWidgetIds: IntArray
    ) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        oldWidgetIds.zip(newWidgetIds).forEach { (oldId, newId) ->
            BatteryWidgetPrefHelper.restoreBatteryWidgetPref(context, oldId, newId)
            updateAppWidget(context, appWidgetManager, newId)
        }
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
