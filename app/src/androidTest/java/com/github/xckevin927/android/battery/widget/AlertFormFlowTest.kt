package com.github.xckevin927.android.battery.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isFocused
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.xckevin927.android.battery.widget.activity.AlertsActivity
import com.github.xckevin927.android.battery.widget.alerts.AlertPreferences
import com.github.xckevin927.android.battery.widget.alerts.AlertSettings
import com.github.xckevin927.android.battery.widget.alerts.QuietHours
import com.github.xckevin927.android.battery.widget.billing.ProBilling
import com.google.android.material.chip.Chip
import com.google.android.material.timepicker.MaterialTimePicker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.allOf

/**
 * Locked-package UI coverage. Enabled reminders are deliberately blocked by the Pro gate, so the
 * paid-only numeric boundary checks live with the isolated QA test instead of faking an unlock.
 */
@RunWith(AndroidJUnit4::class)
class AlertFormFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var alertsBefore: Map<String, *>

    @Before fun snapshotAlerts() {
        assertFalse("This suite must use the ordinary non-QA package", context.packageName.endsWith(".qa"))
        assertFalse("This suite must not bypass a real purchase", ProBilling.hasPro(context))
        alertsBefore = HashMap(context.getSharedPreferences(ALERT_PREFERENCES, Context.MODE_PRIVATE).all)
        restoreAlerts(AlertSettings())
    }

    @After fun restoreAlerts() {
        if (!::alertsBefore.isInitialized) return
        val editor = context.getSharedPreferences(ALERT_PREFERENCES, Context.MODE_PRIVATE).edit().clear()
        alertsBefore.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
    }

    @Test fun lockedPhoneDraftSurvivesRecreationButCannotBeSaved() {
        val baseline = AlertSettings(phoneEnabled = false, phoneThreshold = 80)
        restoreAlerts(baseline)
        ActivityScenario.launch<AlertsActivity>(Intent(context, AlertsActivity::class.java)).use { scenario ->
            onView(withId(R.id.phoneAlertSwitch)).perform(scrollTo()).check(matches(isDisplayed())).perform(click())
            onView(withId(R.id.phoneThresholdCustom)).perform(scrollTo()).check(matches(isDisplayed())).perform(click())
            replaceVisibleText(R.id.phoneCustomThreshold, "0")

            scenario.recreate()
            waitForWindowToSettle()

            onView(withId(R.id.phoneAlertSwitch)).perform(scrollTo()).check(matches(isDisplayed())).check(matches(isChecked()))
            onView(withId(R.id.phoneThresholdCustom)).perform(scrollTo()).check(matches(isDisplayed())).check(matches(isChecked()))
            onView(withId(R.id.phoneCustomThreshold)).perform(scrollTo()).check(matches(isDisplayed())).check(matches(withText("0")))

            onView(withId(R.id.saveAlertsButton)).perform(scrollTo()).check(matches(isDisplayed())).perform(click())

            // The actual Save button must not persist either an enabled reminder or its bad draft.
            assertEquals(baseline, AlertPreferences.getSettings(context))
        }
    }

    @Test fun savingPhoneAlertOffUsesTheVisibleSaveButtonAndRetainsItsThreshold() {
        val enabled = AlertSettings(
            phoneEnabled = true,
            phoneThreshold = 73,
            quietHours = QuietHours(enabled = true, startMinuteOfDay = 1320, endMinuteOfDay = 420)
        )
        restoreAlerts(enabled)
        ActivityScenario.launch<AlertsActivity>(Intent(context, AlertsActivity::class.java)).use {
            onView(withId(R.id.phoneAlertSwitch)).perform(scrollTo()).check(matches(isDisplayed())).check(matches(isChecked()))
                .perform(click())
            onView(withId(R.id.saveAlertsButton)).perform(scrollTo()).check(matches(isDisplayed())).perform(click())
        }

        assertEquals(enabled.copy(phoneEnabled = false), AlertPreferences.getSettings(context))
    }

    @Test fun quietTimePickerDraftsSurviveRecreationAndCancelKeepsTheConfirmedTime() {
        ActivityScenario.launch<AlertsActivity>(Intent(context, AlertsActivity::class.java)).use { scenario ->
            onView(withId(R.id.quietHoursSwitch)).perform(scrollTo()).check(matches(isDisplayed())).perform(click())

            openQuietPicker(R.id.quietStartButton)
            enterPickerMinute("30")
            scenario.recreate()
            waitForWindowToSettle()
            assertRestoredPickerMinute(scenario, QUIET_START_PICKER, 30)
            confirmPicker()
            onView(withId(R.id.quietStartButton)).perform(scrollTo()).check(matches(isDisplayed()))
                .check(matches(withText(org.hamcrest.Matchers.containsString("30"))))

            openQuietPicker(R.id.quietEndButton)
            enterPickerMinute("45")
            scenario.recreate()
            waitForWindowToSettle()
            assertRestoredPickerMinute(scenario, QUIET_END_PICKER, 45)
            confirmPicker()
            onView(withId(R.id.quietEndButton)).perform(scrollTo()).check(matches(isDisplayed()))
                .check(matches(withText(org.hamcrest.Matchers.containsString("45"))))

            openQuietPicker(R.id.quietStartButton)
            enterPickerMinute("15")
            onView(withId(com.google.android.material.R.id.material_timepicker_cancel_button))
                .check(matches(isDisplayed())).perform(click())
            waitForWindowToSettle()
            onView(withId(R.id.quietStartButton)).perform(scrollTo()).check(matches(isDisplayed()))
                .check(matches(withText(org.hamcrest.Matchers.containsString("30"))))
        }
    }

    private fun restoreAlerts(settings: AlertSettings) {
        AlertPreferences.saveSettings(context, settings)
    }

    private fun replaceVisibleText(id: Int, value: String) {
        onView(withId(id)).perform(scrollTo())
        waitForWindowToSettle()
        onView(withId(id)).check(matches(isDisplayed())).perform(click())
        waitForWindowToSettle()
        onView(withId(id)).perform(scrollTo()).check(matches(isDisplayed())).check(matches(isFocused()))
            .perform(replaceText(value), closeSoftKeyboard())
        waitForWindowToSettle()
    }

    private fun openQuietPicker(buttonId: Int) {
        onView(withId(buttonId)).perform(scrollTo()).check(matches(isDisplayed())).perform(click())
        waitForWindowToSettle()
        onView(withId(com.google.android.material.R.id.material_timepicker_mode_button))
            .check(matches(isDisplayed())).perform(click())
        waitForWindowToSettle()
    }

    private fun enterPickerMinute(value: String) {
        // Material's text mode starts with a visible Chip inside the minute combo. The descendant
        // EditText itself starts INVISIBLE, so tapping the chip is the real user path to select it.
        onView(allOf(
            isAssignableFrom(Chip::class.java),
            isDescendantOfA(withId(com.google.android.material.R.id.material_minute_text_input))
        )).check(matches(isDisplayed())).perform(click())
        waitForWindowToSettle()
        onView(allOf(
            isAssignableFrom(android.widget.EditText::class.java),
            isDescendantOfA(withId(com.google.android.material.R.id.material_minute_text_input))
        )).check(matches(isDisplayed())).check(matches(isFocused()))
            .perform(replaceText(value), closeSoftKeyboard())
        waitForWindowToSettle()
    }

    private fun assertRestoredPickerMinute(
        scenario: ActivityScenario<AlertsActivity>,
        tag: String,
        expectedMinute: Int
    ) {
        scenario.onActivity { activity ->
            val picker = activity.supportFragmentManager.findFragmentByTag(tag)
            assertTrue("Expected restored time picker for $tag", picker is MaterialTimePicker)
            assertEquals(expectedMinute, (picker as MaterialTimePicker).minute)
        }
    }

    private fun confirmPicker() {
        onView(withId(com.google.android.material.R.id.material_timepicker_ok_button))
            .check(matches(isDisplayed())).perform(click())
        waitForWindowToSettle()
    }

    private fun waitForWindowToSettle() {
        try {
            instrumentation.uiAutomation.waitForIdle(500, 5_000)
        } catch (e: java.util.concurrent.TimeoutException) {
            throw AssertionError("IME/window did not settle before alert-form input", e)
        }
    }

    private companion object {
        const val ALERT_PREFERENCES = "battery_alerts"
        const val QUIET_START_PICKER = "quiet-start"
        const val QUIET_END_PICKER = "quiet-end"
    }
}
