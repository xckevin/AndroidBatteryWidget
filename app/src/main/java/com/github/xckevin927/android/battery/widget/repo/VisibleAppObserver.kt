package com.github.xckevin927.android.battery.widget.repo

import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService
import com.github.xckevin927.android.battery.widget.billing.ProBilling

/** Live pages work even when the user has chosen not to run a foreground monitor. */
class VisibleAppObserver(private val app: Application) : Application.ActivityLifecycleCallbacks {
    private var visibleActivities = 0
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!WidgetUpdateService.isMonitoringRunning(context)) {
                BatteryRepo.refresh(intent.action ?: "visible_event")
            }
        }
    }
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_BATTERY_LEVEL_CHANGED) {
                BatteryRepo.recordFrameworkBatteryReport(
                    reportedDevice(intent),
                    intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                )
            }
            receiver.onReceive(context, intent)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        visibleActivities++
        if (registered) return
        registered = true
        ProBilling.refresh()
        ContextCompat.registerReceiver(app, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(app, bluetoothReceiver, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
        }, ContextCompat.RECEIVER_EXPORTED)
        BatteryRepo.refresh("app_visible")
    }
    override fun onActivityStopped(activity: Activity) {
        visibleActivities = (visibleActivities - 1).coerceAtLeast(0)
        if (visibleActivities == 0 && registered) {
            app.unregisterReceiver(receiver)
            app.unregisterReceiver(bluetoothReceiver)
            registered = false
        }
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    @Suppress("DEPRECATION")
    private fun reportedDevice(intent: Intent): BluetoothDevice? =
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    companion object {
        private const val ACTION_BATTERY_LEVEL_CHANGED =
            "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }
}
