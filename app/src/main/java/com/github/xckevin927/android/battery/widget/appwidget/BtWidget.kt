package com.github.xckevin927.android.battery.widget.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.github.xckevin927.android.battery.widget.R
import com.github.xckevin927.android.battery.widget.service.BtWidgetService

/**
 * Implementation of App Widget functionality.
 */
class BtWidget : BaseWidgetProvider(WidgetConstants.TYPE_BT_BATTERY) {
    private val TAG = "BtWidget"
    override fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val intent = Intent(context, BtWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        val views = RemoteViews(context.packageName, R.layout.bt_widget)
        views.setRemoteAdapter(R.id.id_grid_bt_widget, intent)

        views.setEmptyView(R.id.id_grid_bt_widget, R.id.empty_view)

        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.id_grid_bt_widget)
        appWidgetManager.updateAppWidget(appWidgetId, views)

    }
}