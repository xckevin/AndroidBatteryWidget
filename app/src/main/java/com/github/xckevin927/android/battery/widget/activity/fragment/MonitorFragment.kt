package com.github.xckevin927.android.battery.widget.activity.fragment

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.xckevin927.android.battery.widget.R
import com.github.xckevin927.android.battery.widget.activity.AlertsActivity
import com.github.xckevin927.android.battery.widget.activity.SettingsActivity
import com.github.xckevin927.android.battery.widget.activity.TabActivity
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService
import com.github.xckevin927.android.battery.widget.utils.BatteryStatusText
import com.github.xckevin927.android.battery.widget.utils.NotificationUtil

class MonitorFragment : Fragment() {
    private lateinit var status: TextView
    private lateinit var explanation: TextView
    private lateinit var level: TextView
    private lateinit var progress: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var phone: TextView
    private lateinit var checked: TextView
    private lateinit var bluetooth: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val render = Runnable { renderStatus() }
    private val clockTick = object : Runnable {
        override fun run() { renderStatus(); handler.postDelayed(this, 15_000) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val page = inflater.inflate(R.layout.fragment_monitor, container, false)
        status = page.findViewById(R.id.overview_monitor_status)
        explanation = page.findViewById(R.id.overview_monitor_explanation)
        phone = page.findViewById(R.id.overview_phone_status)
        level = page.findViewById(R.id.overview_level)
        progress = page.findViewById(R.id.overview_battery_progress)
        checked = page.findViewById(R.id.overview_checked)
        bluetooth = page.findViewById(R.id.overview_bluetooth)
        page.findViewById<View>(R.id.overview_refresh).setOnClickListener {
            WidgetUpdateService.refreshWidgets(context, "manual")
            Toast.makeText(context, R.string.monitor_refresh_requested, Toast.LENGTH_SHORT).show()
        }
        page.findViewById<View>(R.id.overview_monitor_settings).setOnClickListener {
            startActivity(Intent(context, SettingsActivity::class.java))
        }
        page.findViewById<View>(R.id.overview_devices).setOnClickListener {
            (activity as? TabActivity)?.showTab("bluetooth")
        }
        page.findViewById<View>(R.id.overview_widgets).setOnClickListener {
            (activity as? TabActivity)?.showTab("widget")
        }
        page.findViewById<View>(R.id.overview_alerts).setOnClickListener {
            startActivity(Intent(context, AlertsActivity::class.java))
        }
        page.findViewById<View>(R.id.overview_diagnostics).setOnClickListener { shareDiagnostics() }
        return page
    }

    override fun onResume() {
        super.onResume()
        BatteryRepo.addListener(render)
        BatteryRepo.refresh("overview")
        handler.post(clockTick)
    }

    override fun onPause() {
        BatteryRepo.removeListener(render)
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun renderStatus() {
        val context = context ?: return
        if (view == null) return
        val running = WidgetUpdateService.isMonitoringRunning(context)
        status.setText(WidgetUpdateService.getMonitoringStatusText(context))
        explanation.setText(if (running) R.string.monitor_running_explanation else R.string.monitor_fallback_explanation)
        val reading = BatteryRepo.getBatteryState()
        val known = reading.level in 0..100
        level.text = if (known) "${reading.level}%" else "—"
        level.contentDescription = BatteryStatusText.phone(context, reading)
        phone.text = BatteryStatusText.phoneStatus(context, reading)
        progress.visibility = if (known) View.VISIBLE else View.GONE
        if (known) progress.setProgressCompat(reading.level, false)
        checked.text = getString(R.string.monitor_last_check, BatteryStatusText.age(context, BatteryRepo.lastPhoneReadAt))
        bluetooth.text = bluetoothSummary(context)
    }

    private fun bluetoothSummary(context: Context): String {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            return getString(R.string.monitor_bluetooth_permission)
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return getString(R.string.monitor_bluetooth_unavailable)
        val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
        return if (!enabled) getString(R.string.monitor_bluetooth_off)
        else getString(R.string.monitor_bluetooth_count, BatteryRepo.getBtSnapshot().count { it.isConnected })
    }

    private fun shareDiagnostics() {
        val context = requireContext()
        val states = BatteryRepo.getBtSnapshot()
        val summary = buildString {
            appendLine("Battery Widget ${com.github.xckevin927.android.battery.widget.BuildConfig.VERSION_NAME}")
            appendLine("Android API: ${Build.VERSION.SDK_INT}")
            appendLine("Monitoring requested: ${WidgetUpdateService.isMonitoringEnabled(context)}")
            appendLine("Monitoring running: ${WidgetUpdateService.isMonitoringRunning(context)}")
            appendLine("Notifications allowed: ${NotificationUtil.isNotificationEnabled(context)}")
            appendLine("Last start error: ${WidgetUpdateService.getLastStartError() ?: "none"}")
            appendLine("Last refresh request: ${BatteryRepo.lastRefreshReason}, ${BatteryRepo.lastRefreshAt}")
            appendLine("Last phone system read: ${BatteryRepo.lastPhoneReadAt}")
            appendLine("Bluetooth statuses: ${states.groupingBy { it.status }.eachCount()}")
            appendLine("Bluetooth sources: ${states.groupingBy { it.source }.eachCount()}")
            appendLine("Widget redraw requests do not confirm a new peripheral report or launcher render.")
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.monitor_diagnostics_title))
            putExtra(Intent.EXTRA_TEXT, summary)
        }, getString(R.string.monitor_diagnostics)))
    }
}
