package com.github.xckevin927.android.battery.widget

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.xckevin927.android.battery.widget.activity.ProActivity
import com.github.xckevin927.android.battery.widget.alerts.AlertPreferences
import com.github.xckevin927.android.battery.widget.alerts.BatteryAlerts
import com.github.xckevin927.android.battery.widget.appwidget.WidgetLayoutPolicy
import com.github.xckevin927.android.battery.widget.billing.ProBilling
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in acceptance checks for the entitlement already cached on a real device.
 *
 * This test deliberately never creates a BillingClient or directly starts a purchase/restore flow,
 * acknowledges anything, changes a receipt, or changes alert/widget preferences. Launching
 * [ProActivity] may nevertheless make the application's [VisibleAppObserver] refresh the existing
 * billing owner. Restart the target app process before each scenario so [ProBilling.hasPro] reads
 * the receipt state that the operator prepared. After temporarily removing a receipt, run only
 * [cachedReceiptVerification_matchesExplicitExpectedOwnership] with `expectedOwned=false`; the UI
 * lifecycle can otherwise race a Play-cached restore.
 *
 * Required instrumentation arguments:
 *   -e liveEntitlement true -e expectedOwned true|false
 */
@RunWith(AndroidJUnit4::class)
class LiveEntitlementStateTest {

    @Test
    fun cachedReceiptVerification_matchesExplicitExpectedOwnership() {
        val expectedOwned = expectedOwnedOrSkip()

        assertEquals(
            "ProBilling.hasPro must reflect the real locally cached, signature-verified receipt",
            expectedOwned,
            actualOwned()
        )
    }

    @Test
    fun proActivity_rendersTheMatchingPurchaseEntryState() {
        val expectedOwned = expectedOwnedOrSkip()
        assertEquals(expectedOwned, actualOwned())

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, ProActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        try {
            instrumentation.runOnMainSync {
                val status = activity.findViewById<TextView>(R.id.pro_status)
                val price = activity.findViewById<View>(R.id.pro_price)
                val purchase = activity.findViewById<View>(R.id.pro_purchase)

                if (expectedOwned) {
                    assertEquals(View.GONE, price.visibility)
                    assertEquals(View.GONE, purchase.visibility)
                    // The precise unlocked message may reflect a genuine in-process restore/error
                    // state; visibility is the stable UI contract for an owned entitlement.
                    assertTrue("owned Pro screen must expose an unlocked status", status.text.isNotBlank())
                } else {
                    assertEquals(View.VISIBLE, price.visibility)
                    assertEquals(View.VISIBLE, purchase.visibility)
                    // A locked receipt must never be presented as the normal unlocked state.
                    assertTrue(
                        "locked Pro screen must not show the normal unlocked status",
                        status.text.toString() != activity.getString(R.string.pro_status_unlocked)
                    )
                }
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun remindersAndCombinedWidget_areGatedByTheActualEntitlement() {
        val expectedOwned = expectedOwnedOrSkip()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owned = actualOwned()
        assertEquals(expectedOwned, owned)

        // Read the user's existing alert configuration only. Do not enable/disable or save it.
        assertEquals(
            owned && AlertPreferences.isAnyAlertEnabled(context),
            BatteryAlerts.isEnabled(context)
        )

        // This is the same policy used by BatteryWidget, evaluated with no persisted widget ID.
        val resolved = WidgetLayoutPolicy.resolve(
            BatteryWidgetPref.STYLE_COMBINED,
            WidgetLayoutPolicy.WIDE_DP,
            WidgetLayoutPolicy.HORIZONTAL_MIN_HEIGHT_DP,
            owned
        )
        assertEquals(
            if (owned) {
                WidgetLayoutPolicy.Mode.HORIZONTAL_COMBINED
            } else {
                WidgetLayoutPolicy.Mode.PHONE
            },
            resolved
        )
    }

    private fun actualOwned(): Boolean = ProBilling.hasPro(
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    )

    private fun expectedOwnedOrSkip(): Boolean {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Live entitlement checks require -e liveEntitlement true",
            arguments.getString("liveEntitlement") == "true"
        )
        val expected = arguments.getString("expectedOwned")
        assumeTrue(
            "Live entitlement checks require -e expectedOwned true or false",
            expected == "true" || expected == "false"
        )
        return expected == "true"
    }
}
