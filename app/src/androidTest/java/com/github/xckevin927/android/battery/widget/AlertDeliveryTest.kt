package com.github.xckevin927.android.battery.widget

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.xckevin927.android.battery.widget.alerts.AlertPreferences
import com.github.xckevin927.android.battery.widget.alerts.AlertSettings
import com.github.xckevin927.android.battery.widget.alerts.BatteryAlerts
import com.github.xckevin927.android.battery.widget.alerts.AlertEngine
import com.github.xckevin927.android.battery.widget.alerts.AlertNotificationController
import com.github.xckevin927.android.battery.widget.alerts.PhoneAlertInput
import com.github.xckevin927.android.battery.widget.billing.ProBilling
import com.github.xckevin927.android.battery.widget.model.PhoneBatteryState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Actual NotificationManager delivery plus persisted deduplication on an isolated API 36 image. */
@RunWith(AndroidJUnit4::class)
class AlertDeliveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @Before fun prepare() {
        context.getSharedPreferences("battery_alerts", Context.MODE_PRIVATE).edit().clear().commit()
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        notifications.cancelAll()
    }

    @After fun clear() {
        context.getSharedPreferences("battery_alerts", Context.MODE_PRIVATE).edit().clear().commit()
        notifications.cancelAll()
    }

    @Test fun thresholdPostsOnceUntilARealUnplugStartsANewCycle() {
        AlertPreferences.saveSettings(context, AlertSettings(phoneEnabled = true, phoneThreshold = 80))
        evaluateDelivery(phone(79, true))
        assertEquals(0, count())
        evaluateDelivery(phone(80, true))
        waitForCount(1)
        val content = notifications.activeNotifications.single().notification.extras
            .getCharSequence("android.text").toString()
        assertTrue(content.contains("80"))
        notifications.cancelAll()
        waitForCount(0)
        // Delivery state is reloaded from SharedPreferences on each evaluate call.
        evaluateDelivery(phone(81, true))
        SystemClock.sleep(150)
        assertEquals(0, count())
        evaluateDelivery(phone(81, false))
        evaluateDelivery(phone(82, true))
        waitForCount(1)
    }

    @Test fun staleAndUnknownPhoneReadingsDoNotProduceAlerts() {
        AlertPreferences.saveSettings(context, AlertSettings(phoneEnabled = true, phoneThreshold = 80))
        evaluateDelivery(phone(-1, true))
        evaluateDelivery(phone(100, true).apply {
            checkedAtMillis = System.currentTimeMillis() - 180_000
        })
        SystemClock.sleep(150)
        assertEquals(0, count())
    }

    @Test fun realEntryPointDoesNotDeliverWithoutVerifiedProPurchase() {
        assertFalse("Run only on the isolated non-buyer test image", ProBilling.hasPro(context))
        AlertPreferences.saveSettings(context, AlertSettings(phoneEnabled = true, phoneThreshold = 80))
        BatteryAlerts.evaluate(context, phone(90, true), emptyList())
        SystemClock.sleep(150)
        assertFalse(BatteryAlerts.isEnabled(context))
        assertEquals(0, count())
        assertTrue(AlertPreferences.getSettings(context).phoneEnabled)
    }

    // Exercise the delivery components without adding a paid-entitlement bypass to production.
    private fun evaluateDelivery(phone: PhoneBatteryState) {
        val now = System.currentTimeMillis()
        val evaluation = AlertEngine.evaluate(
            AlertPreferences.getSettings(context), AlertPreferences.getEngineState(context),
            PhoneAlertInput(phone.level, phone.isCharging, phone.isPlugged, phone.checkedAtMillis),
            emptyList(), now, 12 * 60
        )
        val delivered = AlertNotificationController.post(context, evaluation.alerts)
        AlertPreferences.saveEngineState(context,
            AlertEngine.acknowledgeDelivered(evaluation.state, evaluation.alerts, delivered))
    }

    private fun phone(level: Int, plugged: Boolean) = PhoneBatteryState().apply {
        setLevel(level)
        setPlugged(plugged)
        setStatus(if (plugged) 2 else 3)
        checkedAtMillis = System.currentTimeMillis()
    }
    private fun count() = notifications.activeNotifications.count { it.notification.channelId == "battery_alerts" }
    private fun waitForCount(expected: Int) {
        val deadline = SystemClock.elapsedRealtime() + 2500
        while (count() != expected && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertEquals(expected, count())
    }
}
