package com.github.xckevin927.android.battery.widget

import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alibaba.fastjson.JSON
import com.github.xckevin927.android.battery.widget.activity.AlertsActivity
import com.github.xckevin927.android.battery.widget.activity.MainActivity
import com.github.xckevin927.android.battery.widget.activity.ProActivity
import com.github.xckevin927.android.battery.widget.alerts.AlertEngine
import com.github.xckevin927.android.battery.widget.alerts.AlertPreferences
import com.github.xckevin927.android.battery.widget.alerts.AlertSettings
import com.github.xckevin927.android.battery.widget.alerts.BatteryAlert
import com.github.xckevin927.android.battery.widget.alerts.BluetoothAlertInput
import com.github.xckevin927.android.battery.widget.alerts.QuietHours
import com.github.xckevin927.android.battery.widget.billing.ProBilling
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper
import com.github.xckevin927.android.battery.widget.utils.DevicePreferences
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * Opt-in acceptance checks for Bluetooth-only Pro functionality on a real device.
 *
 * This suite never creates, pairs, connects, disconnects, renames, or hides a Bluetooth device;
 * manually changes Bluetooth or notification system settings; or creates, restores, consumes, or
 * changes a Play receipt. Opening [AlertsActivity] may create its normal production alert channel
 * on a fresh install, but this test never changes a notification switch, permission, channel, or
 * posts a notification itself. Before each case it snapshots every business preference file it can
 * affect, then installs a temporary all-day quiet baseline so the refresh caused by the UI cannot
 * notify the user. [after] restores each file key-for-key.
 *
 * The widget case uses [TEST_WIDGET_ID], an independent, non-launcher ID. In particular, it never
 * reads or writes the user's real desktop widget IDs 7 or 8.
 *
 * Required instrumentation arguments:
 *   -e liveBluetoothPro true -e expectedOwned true|false
 */
@RunWith(AndroidJUnit4::class)
class LiveBluetoothProTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext.applicationContext
    private var expectedOwned = false
    private lateinit var alertsBefore: Map<String, *>
    private lateinit var widgetsBefore: Map<String, *>
    private lateinit var devicesBefore: Map<String, *>
    private var refreshStarted = false

    @Before
    fun prepareRealDeviceWithoutChangingUserConfiguration() {
        expectedOwned = expectedOwnedOrSkip()
        assertEquals(
            "The prepared locally verified Pro state changed before this live Bluetooth check",
            expectedOwned,
            ProBilling.hasPro(context)
        )
        alertsBefore = snapshot(alertPreferences())
        widgetsBefore = snapshot(widgetPreferences())
        devicesBefore = snapshot(devicePreferences())

        // The application evaluates alerts from BatteryRepo listeners. This baseline keeps any
        // normal refresh performed by the screens silent even if a real accessory is low.
        assertTrue(
            AlertPreferences.saveSettings(
                context,
                AlertSettings(
                    phoneEnabled = false,
                    bluetoothEnabled = false,
                    quietHours = QuietHours(enabled = true, startMinuteOfDay = 0, endMinuteOfDay = 0)
                )
            )
        )
        BatteryRepo.refresh("live_bluetooth_pro_test")
        refreshStarted = true
    }

    @After
    fun restoreBusinessPreferences() {
        if (!::alertsBefore.isInitialized) return

        // BatteryRepo's production GATT fallback may run for up to 15 seconds. Keep the quiet
        // baseline through that window so an in-flight callback cannot deliver a notification
        // because this test refreshed the real accessory state.
        if (refreshStarted) SystemClock.sleep(REPOSITORY_SETTLE_MILLIS)

        restore(alertPreferences(), alertsBefore)
        restore(widgetPreferences(), widgetsBefore)
        restore(devicePreferences(), devicesBefore)
    }

    @Test
    fun bluetoothReminderSave_isGatedByTheActualEntitlementForAnActualPairedDevice() {
        val safeBaseline = AlertPreferences.getSettings(context)
        val displayed = actualAlertDevices()
        val selectedEntry = displayed.withIndex().firstOrNull {
            it.value.isConnected && it.value.batteryLevel in 0..100
        }
        assumeTrue(
            "A real connected paired device with a reported 0-100% battery is required for this live save check",
            selectedEntry != null
        )
        val selected = requireNotNull(selectedEntry)
        val selectedState = selected.value
        val selectedId = requireNotNull(AlertPreferences.deviceId(selectedState))

        ActivityScenario.launch<AlertsActivity>(Intent(context, AlertsActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                // This is the actual user interaction order: enabling the reminder makes its
                // device checkboxes interactive before a real device can be selected.
                activity.findViewById<SwitchMaterial>(R.id.bluetoothAlertSwitch).isChecked = true
                val choices = activity.findViewById<LinearLayout>(R.id.bluetoothDeviceList)
                val rendered = actualAlertDevices()
                assertEquals(
                    "The live alert screen must render every real paired device with an address",
                    rendered.size,
                    choices.childCount
                )
                val renderedIndex = rendered.indexOfFirst {
                    AlertPreferences.deviceId(it) == selectedId
                }
                assertTrue("The selected real device must remain in the displayed list", renderedIndex >= 0)
                val renderedState = rendered[renderedIndex]
                assertTrue(
                    "The selected device must still be connected with its real 0-100% reading",
                    renderedState.isConnected && renderedState.batteryLevel in 0..100
                )
                val choice = choices.getChildAt(renderedIndex) as? CheckBox
                assertNotNull("The selected real device must be selectable in the alert form", choice)
                assertEquals(expectedAlertChoiceText(activity, renderedState), choice!!.text.toString())
                assertTrue("Enabling the Bluetooth reminder must enable its real device choice", choice.isEnabled)
                choice!!.performClick()
                assertTrue(choice.isChecked)

                activity.findViewById<TextInputEditText>(R.id.bluetoothThreshold).setText("1")
                activity.findViewById<View>(R.id.saveAlertsButton).performClick()

                if (expectedOwned) {
                    assertEquals(
                        safeBaseline.copy(
                            bluetoothEnabled = true,
                            bluetoothThreshold = 1,
                            selectedBluetoothDeviceIds = setOf(selectedId)
                        ),
                        AlertPreferences.getSettings(context)
                    )
                    assertTrue("Saved owned Bluetooth reminders must be active at the execution gate",
                        com.github.xckevin927.android.battery.widget.alerts.BatteryAlerts.isEnabled(context))
                } else {
                    assertEquals(safeBaseline, AlertPreferences.getSettings(context))
                    assertFalse(com.github.xckevin927.android.battery.widget.alerts.BatteryAlerts.isEnabled(context))
                }
            }
        }
    }

    @Test
    fun combinedWidgetSelectsAndSavesAnActualVisibleDevice_onlyWhenActuallyOwned() {
        val visible = actualVisibleDevices()
        val selectedEntry = visible.withIndex().firstOrNull {
            it.value.isConnected && it.value.batteryLevel in 0..100
        }
        assumeTrue(
            "A visible real connected device with a reported 0-100% battery is required for widget selection",
            selectedEntry != null
        )
        val selected = requireNotNull(selectedEntry)
        val selectedState = selected.value
        val selectedAddress = deviceAddress(selectedState)
        assumeTrue("The real device must expose an address to the widget editor", selectedAddress != null)
        val realSelectedAddress = requireNotNull(selectedAddress)

        val original = BatteryWidgetPref().apply {
            widgetStyle = BatteryWidgetPref.STYLE_PHONE
            selectedDeviceAddresses = emptyList()
            setShowAllVisibleDevices(false)
        }
        assertTrue(widgetPreferences().edit()
            .putString(widgetKey(TEST_WIDGET_ID), JSON.toJSONString(original))
            .commit())

        val monitor: Instrumentation.ActivityMonitor? = if (expectedOwned) null else {
            instrumentation.addMonitor(ProActivity::class.java.name, null, false)
        }
        try {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, TEST_WIDGET_ID)
            ActivityScenario.launchActivityForResult<MainActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    // Select the combined component before interacting with its real-device
                    // choices, matching the visible configuration path.
                    activity.findViewById<RadioGroup>(R.id.widget_style_group)
                        .check(R.id.widget_style_combined)
                    val choices = activity.findViewById<LinearLayout>(R.id.widget_devices_container)
                    val rendered = actualVisibleDevices()
                    assertEquals(rendered.size, choices.childCount)
                    val renderedIndex = rendered.indexOfFirst {
                        deviceAddress(it) == realSelectedAddress
                    }
                    assertTrue("The selected real device must remain in the visible list", renderedIndex >= 0)
                    val renderedState = rendered[renderedIndex]
                    assertTrue(
                        "The selected visible device must still be connected with its real 0-100% reading",
                        renderedState.isConnected && renderedState.batteryLevel in 0..100
                    )
                    val choice = choices.getChildAt(renderedIndex) as? MaterialCheckBox
                    assertNotNull("The actual visible device must appear in the widget editor", choice)
                    assertEquals(DevicePreferences.displayName(context, renderedState), choice!!.text.toString())
                    assertTrue("The actual visible device choice must be interactive", choice.isEnabled)
                    choice!!.performClick()
                    assertTrue(choice.isChecked)

                    activity.findViewById<View>(R.id.save_pref).performClick()
                }

                if (expectedOwned) {
                    assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
                    val saved = BatteryWidgetPrefHelper.getBatteryWidgetPref(context, TEST_WIDGET_ID)
                    assertEquals(BatteryWidgetPref.STYLE_COMBINED, saved.widgetStyle)
                    assertEquals(listOf(realSelectedAddress), saved.selectedDeviceAddresses)
                } else {
                    val pro = instrumentation.waitForMonitorWithTimeout(monitor, PRO_ACTIVITY_TIMEOUT_MS)
                    assertNotNull("Locked combined widget save must guide the user to Pro", pro)
                    val saved = BatteryWidgetPrefHelper.getBatteryWidgetPref(context, TEST_WIDGET_ID)
                    assertEquals(BatteryWidgetPref.STYLE_PHONE, saved.widgetStyle)
                    assertTrue(saved.selectedDeviceAddresses.isEmpty())
                    instrumentation.runOnMainSync { pro?.finish() }
                    instrumentation.waitForIdleSync()
                }
            }
        } finally {
            if (monitor != null) instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun realSnapshotReminderBoundaries_doNotTreatFullOrUnknownDevicesAsLowBattery() {
        val now = System.currentTimeMillis()
        val inputs = BatteryRepo.getBtSnapshot().mapNotNull(::toBluetoothAlertInput)
        val full = inputs.filter { it.isConnected && it.level == 100 }
        val unknown = inputs.filter { it.isConnected && it.level !in 0..100 }
        assumeTrue("The real connected 100% earphones are required for this boundary check", full.isNotEmpty())
        assumeTrue("The real device with an unknown battery reading is required for this boundary check", unknown.isNotEmpty())

        // This is a pure state-machine check over only live repository values. It deliberately
        // does not call BatteryAlerts.evaluate, AlertNotificationController, or NotificationManager.
        val evaluation = AlertEngine.evaluate(
            settings = AlertSettings(
                bluetoothEnabled = true,
                bluetoothThreshold = AlertSettings.MAX_BLUETOOTH_THRESHOLD,
                selectedBluetoothDeviceIds = inputs.mapTo(linkedSetOf()) { it.deviceId }
            ),
            previousState = com.github.xckevin927.android.battery.widget.alerts.AlertEngineState(),
            phone = null,
            bluetoothDevices = inputs,
            nowMillis = now,
            nowMinuteOfDay = currentMinuteOfDay()
        )
        val alertedDeviceIds = evaluation.alerts.filterIsInstance<BatteryAlert.BluetoothLow>()
            .mapTo(linkedSetOf()) { it.deviceId }
        assertTrue("A real 100% device is above the 95% maximum reminder threshold",
            full.none { it.deviceId in alertedDeviceIds })
        assertTrue("An unknown real battery reading must never create a BluetoothLow reminder",
            unknown.none { it.deviceId in alertedDeviceIds })

        val candidate = inputs.firstOrNull {
            it.level in 1..AlertSettings.MAX_BLUETOOTH_THRESHOLD &&
                AlertEngine.isUsableBluetoothReading(it, now)
        }
        if (candidate != null) {
            assertTrue(
                "An actual fresh <=95% reading may be proposed by the pure engine; this is not notification delivery",
                candidate.deviceId in alertedDeviceIds
            )
        }
    }

    private fun actualAlertDevices(): List<BtDeviceState> = DevicePreferences.allDevices(
        context,
        BatteryRepo.getBtSnapshot()
    ).filter { AlertPreferences.deviceId(it) != null }

    private fun actualVisibleDevices(): List<BtDeviceState> = DevicePreferences.visibleDevices(
        context,
        BatteryRepo.getBtSnapshot()
    ).filter { deviceAddress(it) != null }

    private fun toBluetoothAlertInput(state: BtDeviceState): BluetoothAlertInput? {
        val id = AlertPreferences.deviceId(state) ?: return null
        return BluetoothAlertInput(
            deviceId = id,
            displayName = DevicePreferences.displayName(context, state),
            level = state.batteryLevel,
            isConnected = state.isConnected,
            lastCheckedAtMillis = state.lastCheckedAt,
            lastSuccessfulReadAtMillis = state.lastSuccessfulReadAt,
            source = state.source,
            status = state.status
        )
    }

    private fun expectedAlertChoiceText(activity: AlertsActivity, state: BtDeviceState): String {
        val name = DevicePreferences.displayName(context, state)
        val now = System.currentTimeMillis()
        val checkedFresh = state.lastCheckedAt > 0 &&
            state.lastCheckedAt >= now - AlertEngine.READING_MAX_AGE_MILLIS
        val successfulReadFresh = state.lastSuccessfulReadAt > 0 &&
            state.lastSuccessfulReadAt >= now - AlertEngine.READING_MAX_AGE_MILLIS
        if (
            state.isConnected && state.status == AlertEngine.STATUS_AVAILABLE &&
            state.batteryLevel in 0..100 && checkedFresh && successfulReadFresh
        ) {
            return activity.getString(R.string.alert_device_level, name, state.batteryLevel)
        }
        val status = when {
            !state.isConnected || state.status == "disconnected" -> R.string.alert_status_disconnected
            state.status == "permission_denied" -> R.string.alert_status_permission_denied
            state.status == "unsupported" -> R.string.alert_status_unsupported
            state.status == "cached" -> R.string.alert_status_cached
            state.status == "stale" || !checkedFresh || !successfulReadFresh -> R.string.alert_status_stale
            state.status == AlertEngine.STATUS_AVAILABLE -> R.string.alert_status_available
            else -> R.string.alert_status_unknown
        }
        return activity.getString(R.string.alert_device_status, name, activity.getString(status))
    }

    private fun currentMinuteOfDay(): Int = Calendar.getInstance().let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }

    private fun deviceAddress(state: BtDeviceState): String? = try {
        state.bluetoothDevice.address?.takeIf { it.isNotBlank() }
    } catch (_: SecurityException) {
        null
    }

    private fun expectedOwnedOrSkip(): Boolean {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Live Bluetooth Pro checks require -e liveBluetoothPro true",
            arguments.getString("liveBluetoothPro") == "true"
        )
        val expected = arguments.getString("expectedOwned")
        assumeTrue(
            "Live Bluetooth Pro checks require -e expectedOwned true or false",
            expected == "true" || expected == "false"
        )
        return expected == "true"
    }

    private fun alertPreferences(): SharedPreferences =
        context.getSharedPreferences(ALERT_PREFERENCES, Context.MODE_PRIVATE)

    private fun widgetPreferences(): SharedPreferences =
        context.getSharedPreferences(WIDGET_PREFERENCES, Context.MODE_PRIVATE)

    private fun devicePreferences(): SharedPreferences =
        context.getSharedPreferences(DEVICE_PREFERENCES, Context.MODE_PRIVATE)

    private fun widgetKey(id: Int): String = "$WIDGET_KEY_PREFIX$id"

    private fun snapshot(preferences: SharedPreferences): Map<String, *> =
        HashMap<String, Any?>().also { copy ->
            preferences.all.forEach { (key, value) ->
                copy[key] = if (value is Set<*>) value.filterIsInstance<String>().toSet() else value
            }
        }

    private fun restore(preferences: SharedPreferences, snapshot: Map<String, *>) {
        val editor = preferences.edit().clear()
        snapshot.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        assertTrue("Could not restore $preferences after live Bluetooth Pro test", editor.commit())
    }

    private companion object {
        const val ALERT_PREFERENCES = "battery_alerts"
        const val WIDGET_PREFERENCES = "BATTERY_PREF"
        const val DEVICE_PREFERENCES = "bluetooth_device_preferences"
        const val WIDGET_KEY_PREFIX = "BATTERY_PREF_WIDGET_"
        const val TEST_WIDGET_ID = 91_073
        const val PRO_ACTIVITY_TIMEOUT_MS = 5_000L
        const val REPOSITORY_SETTLE_MILLIS = 16_000L
    }
}
