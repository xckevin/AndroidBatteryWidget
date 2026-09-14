package com.github.xckevin927.android.battery.widget.activity

import android.Manifest
import android.content.res.ColorStateList
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.github.xckevin927.android.battery.widget.R
import com.github.xckevin927.android.battery.widget.alerts.AlertEngine
import com.github.xckevin927.android.battery.widget.alerts.AlertNotificationController
import com.github.xckevin927.android.battery.widget.alerts.AlertPreferences
import com.github.xckevin927.android.battery.widget.alerts.AlertSettings
import com.github.xckevin927.android.battery.widget.alerts.QuietHours
import com.github.xckevin927.android.battery.widget.billing.ProBilling
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService
import com.github.xckevin927.android.battery.widget.utils.DevicePreferences
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar

class AlertsActivity : BaseActivity() {
    private lateinit var proAccessCard: MaterialCardView
    private lateinit var proAccessTitle: TextView
    private lateinit var proAccessStatus: TextView
    private lateinit var proAccessButton: MaterialButton
    private lateinit var notificationStatusCard: MaterialCardView
    private lateinit var notificationStatusText: TextView
    private lateinit var notificationSettingsButton: MaterialButton
    private lateinit var monitoringStatusText: TextView
    private lateinit var monitoringSettingsButton: MaterialButton
    private lateinit var phoneAlertSwitch: SwitchMaterial
    private lateinit var phoneOptions: ViewGroup
    private lateinit var phoneThresholdGroup: RadioGroup
    private lateinit var phoneCustomThresholdLayout: TextInputLayout
    private lateinit var phoneCustomThreshold: TextInputEditText
    private lateinit var bluetoothAlertSwitch: SwitchMaterial
    private lateinit var bluetoothOptions: ViewGroup
    private lateinit var bluetoothThresholdLayout: TextInputLayout
    private lateinit var bluetoothThreshold: TextInputEditText
    private lateinit var bluetoothEmptyText: TextView
    private lateinit var bluetoothDeviceList: LinearLayout
    private lateinit var quietHoursSwitch: SwitchMaterial
    private lateinit var quietHoursOptions: ViewGroup
    private lateinit var quietStartButton: MaterialButton
    private lateinit var quietEndButton: MaterialButton

    private var quietStartMinute = 22 * 60
    private var quietEndMinute = 7 * 60
    private val draftSelectedDeviceIds = linkedSetOf<String>()
    private val repoListener = Runnable { runOnUiThread(::renderBluetoothDevices) }
    private val proBillingListener = Runnable {
        if (!isFinishing && !isDestroyed) updateProAccessUi()
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // The runtime prompt can no longer appear after a permanent denial; keep a working
            // recovery path by taking the user to this app's notification settings.
            AlertNotificationController.openNotificationSettings(this)
        }
        updateNotificationStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alerts)
        title = getString(R.string.alerts_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        bindViews()
        installProAccessBanner()
        AlertNotificationController.createChannel(this)
        loadSettings()
        if (savedInstanceState != null) restoreDraft(savedInstanceState)
        bindActions()
        // FragmentManager restores the picker across recreation, but its listeners are not saved.
        listOf(QUIET_START_PICKER, QUIET_END_PICKER).forEach { tag ->
            (supportFragmentManager.findFragmentByTag(tag) as? MaterialTimePicker)?.let {
                bindTimePickerResult(it, tag)
            }
        }
        renderBluetoothDevices()
        updateNotificationStatus()
        updateMonitoringStatus()
        updateProAccessUi()
    }

    override fun onStart() {
        super.onStart()
        ProBilling.addListener(proBillingListener)
        ProBilling.initialize(applicationContext)
        BatteryRepo.addListener(repoListener)
        BatteryRepo.refresh("alerts-screen")
    }

    override fun onStop() {
        BatteryRepo.removeListener(repoListener)
        ProBilling.removeListener(proBillingListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateNotificationStatus()
        updateMonitoringStatus()
        updateProAccessUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PHONE_ENABLED, phoneAlertSwitch.isChecked)
        outState.putInt(STATE_PHONE_THRESHOLD_MODE, phoneThresholdGroup.checkedRadioButtonId)
        outState.putString(STATE_PHONE_CUSTOM_THRESHOLD, phoneCustomThreshold.text?.toString())
        outState.putBoolean(STATE_BLUETOOTH_ENABLED, bluetoothAlertSwitch.isChecked)
        outState.putString(STATE_BLUETOOTH_THRESHOLD, bluetoothThreshold.text?.toString())
        outState.putStringArrayList(STATE_SELECTED_DEVICES, ArrayList(draftSelectedDeviceIds))
        outState.putBoolean(STATE_QUIET_ENABLED, quietHoursSwitch.isChecked)
        outState.putInt(STATE_QUIET_START, quietStartMinute)
        outState.putInt(STATE_QUIET_END, quietEndMinute)
        super.onSaveInstanceState(outState)
    }

    private fun bindViews() {
        notificationStatusCard = findViewById(R.id.notificationStatusCard)
        notificationStatusText = findViewById(R.id.notificationStatusText)
        notificationSettingsButton = findViewById(R.id.notificationSettingsButton)
        monitoringStatusText = findViewById(R.id.monitoringStatusText)
        monitoringSettingsButton = findViewById(R.id.monitoringSettingsButton)
        phoneAlertSwitch = findViewById(R.id.phoneAlertSwitch)
        phoneOptions = findViewById(R.id.phoneOptions)
        phoneThresholdGroup = findViewById(R.id.phoneThresholdGroup)
        phoneCustomThresholdLayout = findViewById(R.id.phoneCustomThresholdLayout)
        phoneCustomThreshold = findViewById(R.id.phoneCustomThreshold)
        bluetoothAlertSwitch = findViewById(R.id.bluetoothAlertSwitch)
        bluetoothOptions = findViewById(R.id.bluetoothOptions)
        bluetoothThresholdLayout = findViewById(R.id.bluetoothThresholdLayout)
        bluetoothThreshold = findViewById(R.id.bluetoothThreshold)
        bluetoothEmptyText = findViewById(R.id.bluetoothEmptyText)
        bluetoothDeviceList = findViewById(R.id.bluetoothDeviceList)
        quietHoursSwitch = findViewById(R.id.quietHoursSwitch)
        quietHoursOptions = findViewById(R.id.quietHoursOptions)
        quietStartButton = findViewById(R.id.quietStartButton)
        quietEndButton = findViewById(R.id.quietEndButton)
    }

    private fun loadSettings() {
        val settings = AlertPreferences.getSettings(this)
        phoneAlertSwitch.isChecked = settings.phoneEnabled
        when (settings.phoneThreshold) {
            80 -> phoneThresholdGroup.check(R.id.phoneThreshold80)
            100 -> phoneThresholdGroup.check(R.id.phoneThreshold100)
            else -> {
                phoneThresholdGroup.check(R.id.phoneThresholdCustom)
                phoneCustomThreshold.setText(settings.phoneThreshold.toString())
            }
        }
        if (phoneCustomThreshold.text.isNullOrBlank()) {
            phoneCustomThreshold.setText(settings.phoneThreshold.toString())
        }
        bluetoothAlertSwitch.isChecked = settings.bluetoothEnabled
        bluetoothThreshold.setText(settings.bluetoothThreshold.toString())
        draftSelectedDeviceIds.clear()
        draftSelectedDeviceIds += settings.selectedBluetoothDeviceIds
        quietHoursSwitch.isChecked = settings.quietHours.enabled
        quietStartMinute = settings.quietHours.startMinuteOfDay
        quietEndMinute = settings.quietHours.endMinuteOfDay
        updateOptionVisibility()
        updateTimeButtons()
    }

    private fun restoreDraft(state: Bundle) {
        phoneAlertSwitch.isChecked = state.getBoolean(
            STATE_PHONE_ENABLED,
            phoneAlertSwitch.isChecked
        )
        val thresholdMode = state.getInt(
            STATE_PHONE_THRESHOLD_MODE,
            phoneThresholdGroup.checkedRadioButtonId
        )
        if (thresholdMode != View.NO_ID) phoneThresholdGroup.check(thresholdMode)
        state.getString(STATE_PHONE_CUSTOM_THRESHOLD)?.let { phoneCustomThreshold.setText(it) }
        bluetoothAlertSwitch.isChecked = state.getBoolean(
            STATE_BLUETOOTH_ENABLED,
            bluetoothAlertSwitch.isChecked
        )
        state.getString(STATE_BLUETOOTH_THRESHOLD)?.let { bluetoothThreshold.setText(it) }
        state.getStringArrayList(STATE_SELECTED_DEVICES)?.let { selected ->
            draftSelectedDeviceIds.clear()
            draftSelectedDeviceIds += selected
        }
        quietHoursSwitch.isChecked = state.getBoolean(
            STATE_QUIET_ENABLED,
            quietHoursSwitch.isChecked
        )
        quietStartMinute = state.getInt(STATE_QUIET_START, quietStartMinute)
        quietEndMinute = state.getInt(STATE_QUIET_END, quietEndMinute)
        updateOptionVisibility()
        updateTimeButtons()
    }

    private fun bindActions() {
        phoneAlertSwitch.setOnCheckedChangeListener { _, _ -> updateOptionVisibility() }
        bluetoothAlertSwitch.setOnCheckedChangeListener { _, _ -> updateOptionVisibility() }
        quietHoursSwitch.setOnCheckedChangeListener { _, _ -> updateOptionVisibility() }
        phoneThresholdGroup.setOnCheckedChangeListener { _, _ -> updateOptionVisibility() }
        quietStartButton.setOnClickListener {
            showTimePicker(quietStartMinute, R.string.alert_quiet_start_title, QUIET_START_PICKER)
        }
        quietEndButton.setOnClickListener {
            showTimePicker(quietEndMinute, R.string.alert_quiet_end_title, QUIET_END_PICKER)
        }
        notificationSettingsButton.setOnClickListener { recoverNotificationAccess() }
        monitoringSettingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        proAccessButton.setOnClickListener {
            startActivity(Intent(this, ProActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.saveAlertsButton).setOnClickListener { saveSettings() }
    }

    private fun updateOptionVisibility() {
        setChildrenEnabled(phoneOptions, phoneAlertSwitch.isChecked)
        phoneCustomThresholdLayout.visibility = if (
            phoneAlertSwitch.isChecked &&
            phoneThresholdGroup.checkedRadioButtonId == R.id.phoneThresholdCustom
        ) View.VISIBLE else View.GONE
        setChildrenEnabled(bluetoothOptions, bluetoothAlertSwitch.isChecked)
        setChildrenEnabled(quietHoursOptions, quietHoursSwitch.isChecked)
    }

    private fun setChildrenEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                setChildrenEnabled(view.getChildAt(index), enabled)
            }
        }
    }

    private fun renderBluetoothDevices() {
        if (!::bluetoothDeviceList.isInitialized || isFinishing || isDestroyed) return
        val devices = DevicePreferences.allDevices(this, BatteryRepo.getBtSnapshot())
        bluetoothDeviceList.removeAllViews()
        var displayed = 0
        devices.forEach { state ->
            val id = AlertPreferences.deviceId(state) ?: return@forEach
            displayed++
            val checkBox = CheckBox(this).apply {
                text = deviceLabel(state)
                isChecked = id in draftSelectedDeviceIds
                isEnabled = bluetoothAlertSwitch.isChecked
                setOnCheckedChangeListener { _, checked ->
                    if (checked) draftSelectedDeviceIds += id else draftSelectedDeviceIds -= id
                }
            }
            bluetoothDeviceList.addView(checkBox)
        }
        bluetoothEmptyText.visibility = if (displayed == 0) View.VISIBLE else View.GONE
    }

    private fun deviceLabel(state: BtDeviceState): String {
        val name = DevicePreferences.displayName(this, state)
        val now = System.currentTimeMillis()
        val checkedFresh = state.lastCheckedAt > 0 &&
            state.lastCheckedAt >= now - AlertEngine.READING_MAX_AGE_MILLIS
        val gattFresh = state.lastSuccessfulReadAt > 0 &&
            state.lastSuccessfulReadAt >= now - AlertEngine.READING_MAX_AGE_MILLIS
        if (
            state.isConnected && state.status == AlertEngine.STATUS_AVAILABLE &&
            state.batteryLevel in 0..100 && checkedFresh && gattFresh
        ) {
            return getString(R.string.alert_device_level, name, state.batteryLevel)
        }
        val status = when {
            !state.isConnected || state.status == "disconnected" -> R.string.alert_status_disconnected
            state.status == "permission_denied" -> R.string.alert_status_permission_denied
            state.status == "unsupported" -> R.string.alert_status_unsupported
            state.status == "cached" -> R.string.alert_status_cached
            state.status == "stale" || !checkedFresh || !gattFresh -> R.string.alert_status_stale
            state.status == "available" -> R.string.alert_status_available
            else -> R.string.alert_status_unknown
        }
        return getString(R.string.alert_device_status, name, getString(status))
    }

    private fun saveSettings() {
        if ((phoneAlertSwitch.isChecked || bluetoothAlertSwitch.isChecked) &&
            !ProBilling.hasPro(applicationContext)
        ) {
            updateProAccessUi()
            Toast.makeText(this, R.string.alert_pro_required, Toast.LENGTH_LONG).show()
            proAccessStatus.announceForAccessibility(getString(R.string.alert_pro_required))
            return
        }
        phoneCustomThresholdLayout.error = null
        bluetoothThresholdLayout.error = null
        val previous = AlertPreferences.getSettings(this)
        val phoneThreshold = if (!phoneAlertSwitch.isChecked) {
            previous.phoneThreshold
        } else {
            when (phoneThresholdGroup.checkedRadioButtonId) {
                R.id.phoneThreshold80 -> 80
                R.id.phoneThreshold100 -> 100
                else -> parseThreshold(phoneCustomThreshold, phoneCustomThresholdLayout) ?: return
            }
        }
        val btThreshold = if (!bluetoothAlertSwitch.isChecked) {
            previous.bluetoothThreshold
        } else {
            parseThreshold(
                bluetoothThreshold,
                bluetoothThresholdLayout,
                AlertSettings.MAX_BLUETOOTH_THRESHOLD
            ) ?: return
        }
        if (bluetoothAlertSwitch.isChecked && draftSelectedDeviceIds.isEmpty()) {
            Toast.makeText(this, R.string.alert_select_at_least_one_device, Toast.LENGTH_LONG).show()
            return
        }
        val settings = AlertSettings(
            phoneEnabled = phoneAlertSwitch.isChecked,
            phoneThreshold = phoneThreshold,
            bluetoothEnabled = bluetoothAlertSwitch.isChecked,
            bluetoothThreshold = btThreshold,
            selectedBluetoothDeviceIds = draftSelectedDeviceIds.toSet(),
            quietHours = QuietHours(
                enabled = quietHoursSwitch.isChecked,
                startMinuteOfDay = quietStartMinute,
                endMinuteOfDay = quietEndMinute
            )
        )
        if (!AlertPreferences.saveSettings(this, settings)) {
            Toast.makeText(this, R.string.alert_save_failed, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.alert_saved, Toast.LENGTH_SHORT).show()
        BatteryRepo.refresh("alerts-settings-saved")
        updateMonitoringStatus()
        if ((settings.phoneEnabled || settings.bluetoothEnabled) &&
            !AlertNotificationController.canPostNotifications(this)
        ) {
            showNotificationRecoveryDialog()
        }
    }

    private fun parseThreshold(
        input: TextInputEditText,
        layout: TextInputLayout,
        maximum: Int = 100
    ): Int? {
        val value = input.text?.toString()?.trim()?.toIntOrNull()
        if (value == null || value !in 1..maximum) {
            layout.error = getString(R.string.alert_invalid_threshold, maximum)
            input.requestFocus()
            return null
        }
        return value
    }

    private fun updateNotificationStatus() {
        if (!::notificationStatusText.isInitialized) return
        val enabled = AlertNotificationController.canPostNotifications(this)
        notificationStatusText.setText(
            if (enabled) R.string.alert_notifications_enabled
            else R.string.alert_notifications_disabled
        )
        notificationSettingsButton.visibility = if (enabled) View.GONE else View.VISIBLE
        notificationStatusCard.isClickable = false
    }

    private fun updateMonitoringStatus() {
        if (!::monitoringStatusText.isInitialized) return
        monitoringStatusText.setText(
            if (WidgetUpdateService.isMonitoringRunning(this)) {
                R.string.alert_monitoring_enabled
            } else {
                R.string.alert_monitoring_disabled
            }
        )
    }

    private fun installProAccessBanner() {
        val content = findViewById<ViewGroup>(android.R.id.content)
        val scrollContent = content.getChildAt(0) as? ViewGroup
        val page = scrollContent?.getChildAt(0) as? LinearLayout
            ?: error("Alerts page content is missing")

        val bannerContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        proAccessTitle = TextView(this).apply {
            setTextAppearance(this@AlertsActivity, R.style.TextAppearance_Battery_Section)
            setTextColor(ContextCompat.getColor(this@AlertsActivity, R.color.ui_text))
            ViewCompat.setAccessibilityHeading(this, true)
        }
        proAccessStatus = TextView(this).apply {
            id = R.id.pro_alerts_status
            setTextAppearance(this@AlertsActivity, R.style.TextAppearance_Battery_Body)
            setTextColor(ContextCompat.getColor(this@AlertsActivity, R.color.ui_text_secondary))
        }
        proAccessButton = MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            id = R.id.pro_alerts_upgrade
            minHeight = dp(48)
            cornerRadius = dp(16)
            strokeWidth = dp(1)
            strokeColor = ColorStateList.valueOf(
                ContextCompat.getColor(this@AlertsActivity, R.color.ui_outline)
            )
            setTextColor(ContextCompat.getColor(this@AlertsActivity, R.color.ui_primary))
            setText(R.string.alert_pro_view)
        }
        bannerContent.addView(
            proAccessTitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        bannerContent.addView(
            proAccessStatus,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        )
        bannerContent.addView(
            proAccessButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        )
        proAccessCard = MaterialCardView(this).apply {
            id = R.id.pro_alerts_banner
            radius = dp(20).toFloat()
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(this@AlertsActivity, R.color.ui_outline)
            setCardBackgroundColor(
                ContextCompat.getColor(this@AlertsActivity, R.color.ui_primary_container)
            )
            cardElevation = 0f
            addView(
                bannerContent,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        page.addView(
            proAccessCard,
            0,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        )
    }

    private fun updateProAccessUi() {
        if (!::proAccessStatus.isInitialized) return
        val owned = ProBilling.hasPro(applicationContext)
        val pending = ProBilling.getState().pending
        when {
            owned -> {
                proAccessTitle.setText(R.string.alert_pro_unlocked_title)
                proAccessStatus.setText(R.string.alert_pro_unlocked_message)
                proAccessButton.visibility = View.GONE
            }
            pending -> {
                proAccessTitle.setText(R.string.alert_pro_locked_title)
                proAccessStatus.setText(R.string.alert_pro_pending_message)
                proAccessButton.visibility = View.VISIBLE
            }
            else -> {
                proAccessTitle.setText(R.string.alert_pro_locked_title)
                proAccessStatus.setText(R.string.alert_pro_locked_message)
                proAccessButton.visibility = View.VISIBLE
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun recoverNotificationAccess() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            AlertNotificationController.openNotificationSettings(this)
        }
    }

    private fun showNotificationRecoveryDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.alert_notification_required_title)
            .setMessage(R.string.alert_notification_required_message)
            .setPositiveButton(R.string.alert_open_notification_settings) { _, _ ->
                recoverNotificationAccess()
            }
            .setNegativeButton(R.string.alert_not_now, null)
            .show()
    }

    private fun showTimePicker(
        minuteOfDay: Int,
        titleRes: Int,
        tag: String
    ) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(
                if (android.text.format.DateFormat.is24HourFormat(this)) {
                    TimeFormat.CLOCK_24H
                } else {
                    TimeFormat.CLOCK_12H
                }
            )
            .setHour(minuteOfDay / 60)
            .setMinute(minuteOfDay % 60)
            .setTitleText(titleRes)
            .build()
        bindTimePickerResult(picker, tag)
        picker.show(supportFragmentManager, tag)
    }

    private fun bindTimePickerResult(picker: MaterialTimePicker, tag: String) {
        picker.clearOnPositiveButtonClickListeners()
        picker.addOnPositiveButtonClickListener {
            val minuteOfDay = picker.hour * 60 + picker.minute
            when (tag) {
                QUIET_START_PICKER -> quietStartMinute = minuteOfDay
                QUIET_END_PICKER -> quietEndMinute = minuteOfDay
            }
            updateTimeButtons()
        }
    }

    private fun updateTimeButtons() {
        quietStartButton.text = getString(
            R.string.alert_quiet_start,
            formatTime(quietStartMinute)
        )
        quietEndButton.text = getString(
            R.string.alert_quiet_end,
            formatTime(quietEndMinute)
        )
    }

    private fun formatTime(minuteOfDay: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return android.text.format.DateFormat.getTimeFormat(this).format(calendar.time)
    }

    companion object {
        private const val QUIET_START_PICKER = "quiet-start"
        private const val QUIET_END_PICKER = "quiet-end"
        private const val STATE_PHONE_ENABLED = "alerts.phone.enabled"
        private const val STATE_PHONE_THRESHOLD_MODE = "alerts.phone.threshold_mode"
        private const val STATE_PHONE_CUSTOM_THRESHOLD = "alerts.phone.custom_threshold"
        private const val STATE_BLUETOOTH_ENABLED = "alerts.bluetooth.enabled"
        private const val STATE_BLUETOOTH_THRESHOLD = "alerts.bluetooth.threshold"
        private const val STATE_SELECTED_DEVICES = "alerts.bluetooth.selected_devices"
        private const val STATE_QUIET_ENABLED = "alerts.quiet.enabled"
        private const val STATE_QUIET_START = "alerts.quiet.start"
        private const val STATE_QUIET_END = "alerts.quiet.end"
    }
}
