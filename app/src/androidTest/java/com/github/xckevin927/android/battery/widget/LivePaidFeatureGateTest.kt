package com.github.xckevin927.android.battery.widget

import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alibaba.fastjson.JSON
import com.github.xckevin927.android.battery.widget.activity.AlertsActivity
import com.github.xckevin927.android.battery.widget.activity.MainActivity
import com.github.xckevin927.android.battery.widget.activity.ProActivity
import com.github.xckevin927.android.battery.widget.alerts.AlertPreferences
import com.github.xckevin927.android.battery.widget.alerts.AlertSettings
import com.github.xckevin927.android.battery.widget.alerts.QuietHours
import com.github.xckevin927.android.battery.widget.billing.ProBilling
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper
import com.google.android.material.switchmaterial.SwitchMaterial
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in real-device checks for paid feature save gates.
 *
 * No entitlement is fabricated: every branch uses the existing locally verified
 * [ProBilling.hasPro] result. The test does not create a BillingClient, buy, restore, acknowledge,
 * refund, change receipts, create Bluetooth devices, or change Bluetooth system settings. Normal
 * activity lifecycle refreshes still run. To avoid turning this into a connected-accessory test,
 * each case skips when the repository's no-I/O snapshot reports a connected device.
 *
 * Both SharedPreferences files are copied before each case and restored key-for-key in [after].
 * The widget case uses [TEST_WIDGET_ID], never a launcher-assigned widget ID (including ID 8).
 *
 * Required instrumentation arguments:
 *   -e livePaidFeatures true -e expectedOwned true|false
 */
@RunWith(AndroidJUnit4::class)
class LivePaidFeatureGateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var expectedOwned = false
    private lateinit var alertsBefore: Map<String, *>
    private lateinit var widgetsBefore: Map<String, *>

    @Before
    fun snapshotPreferencesAndRequireExplicitLiveState() {
        expectedOwned = expectedOwnedOrSkip()
        assertEquals(
            "The prepared device receipt state changed before this live save check",
            expectedOwned,
            ProBilling.hasPro(context)
        )
        alertsBefore = HashMap(alertPreferences().all)
        widgetsBefore = HashMap(widgetPreferences().all)
    }

    @After
    fun after() {
        if (::alertsBefore.isInitialized) restore(alertPreferences(), alertsBefore)
        if (::widgetsBefore.isInitialized) restore(widgetPreferences(), widgetsBefore)
    }

    @Test
    fun phoneAlertSave_isAllowedOnlyForTheActualEntitlement() {
        skipIfConnectedBluetoothDeviceIsPresent()
        val baseline = AlertSettings(
            phoneEnabled = false,
            phoneThreshold = 100,
            // Suppresses delivery should the normal post-save repository refresh run.
            quietHours = QuietHours(enabled = true, startMinuteOfDay = 0, endMinuteOfDay = 0)
        )
        assertTrue(AlertPreferences.saveSettings(context, baseline))

        ActivityScenario.launch<AlertsActivity>(Intent(context, AlertsActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val phone = activity.findViewById<SwitchMaterial>(R.id.phoneAlertSwitch)
                phone.isChecked = true
                activity.findViewById<View>(R.id.saveAlertsButton).performClick()

                if (expectedOwned) {
                    assertEquals(baseline.copy(phoneEnabled = true), AlertPreferences.getSettings(context))
                } else {
                    assertEquals(baseline, AlertPreferences.getSettings(context))
                    assertEquals(
                        activity.getString(R.string.alert_pro_locked_message),
                        activity.findViewById<TextView>(R.id.pro_alerts_status).text.toString()
                    )
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.pro_alerts_upgrade).visibility)
                }
            }
        }
    }

    @Test
    fun combinedWidgetSave_isAllowedOnlyForTheActualEntitlement() {
        skipIfConnectedBluetoothDeviceIsPresent()
        val original = BatteryWidgetPref().apply { widgetStyle = BatteryWidgetPref.STYLE_PHONE }
        // Seed only the independent fake-ID key without touching global/default widget preferences.
        widgetPreferences().edit()
            .putString(widgetKey(TEST_WIDGET_ID), JSON.toJSONString(original))
            .commit()

        val monitor: Instrumentation.ActivityMonitor? = if (expectedOwned) null else {
            instrumentation.addMonitor(ProActivity::class.java.name, null, false)
        }
        try {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, TEST_WIDGET_ID)
            ActivityScenario.launchActivityForResult<MainActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    activity.findViewById<RadioGroup>(R.id.widget_style_group)
                        .check(R.id.widget_style_combined)
                    activity.findViewById<View>(R.id.save_pref).performClick()
                }

                if (expectedOwned) {
                    assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
                    assertEquals(
                        BatteryWidgetPref.STYLE_COMBINED,
                        BatteryWidgetPrefHelper.getBatteryWidgetPref(context, TEST_WIDGET_ID).widgetStyle
                    )
                } else {
                    val pro = instrumentation.waitForMonitorWithTimeout(monitor, PRO_ACTIVITY_TIMEOUT_MS)
                    assertNotNull("Locked combined save must guide the user to Pro", pro)
                    assertEquals(
                        BatteryWidgetPref.STYLE_PHONE,
                        BatteryWidgetPrefHelper.getBatteryWidgetPref(context, TEST_WIDGET_ID).widgetStyle
                    )
                    instrumentation.runOnMainSync { pro?.finish() }
                    instrumentation.waitForIdleSync()
                }
            }
        } finally {
            if (monitor != null) instrumentation.removeMonitor(monitor)
        }
    }

    private fun skipIfConnectedBluetoothDeviceIsPresent() {
        assumeTrue(
            "Live paid-feature UI checks skip while a connected Bluetooth device is in the cached snapshot",
            BatteryRepo.getBtSnapshot().none { it.isConnected }
        )
    }

    private fun expectedOwnedOrSkip(): Boolean {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Live paid-feature checks require -e livePaidFeatures true",
            arguments.getString("livePaidFeatures") == "true"
        )
        val expected = arguments.getString("expectedOwned")
        assumeTrue(
            "Live paid-feature checks require -e expectedOwned true or false",
            expected == "true" || expected == "false"
        )
        return expected == "true"
    }

    private fun alertPreferences(): SharedPreferences =
        context.getSharedPreferences(ALERT_PREFERENCES, Context.MODE_PRIVATE)

    private fun widgetPreferences(): SharedPreferences =
        context.getSharedPreferences(WIDGET_PREFERENCES, Context.MODE_PRIVATE)

    private fun widgetKey(id: Int): String = "$WIDGET_KEY_PREFIX$id"

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
        assertTrue("Could not restore $preferences after live gate test", editor.commit())
    }

    private companion object {
        const val ALERT_PREFERENCES = "battery_alerts"
        const val WIDGET_PREFERENCES = "BATTERY_PREF"
        const val WIDGET_KEY_PREFIX = "BATTERY_PREF_WIDGET_"
        const val TEST_WIDGET_ID = 91_072
        const val PRO_ACTIVITY_TIMEOUT_MS = 5_000L
    }
}
