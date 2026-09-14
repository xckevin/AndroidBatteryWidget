package com.github.xckevin927.android.battery.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.github.xckevin927.android.battery.widget.activity.MainActivity;
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper;

import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Ensures the configuration action is physically reachable through the scroll container. */
@RunWith(AndroidJUnit4.class)
public class WidgetReachabilityTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void portraitSaveIsReachableByScrolling() {
        assertSaveReachable(81010, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Test public void landscapeSaveIsReachableByScrolling() {
        assertSaveReachable(81011, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    private void assertSaveReachable(int widgetId, int orientation) {
        BatteryWidgetPrefHelper.removeBatteryWidgetPref(context, widgetId);
        // Make this independent of any legacy default saved by another test or user session.
        BatteryWidgetPrefHelper.saveBatteryWidgetPref(context, widgetId, new BatteryWidgetPref());
        Intent intent = new Intent(context, MainActivity.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launchActivityForResult(intent)) {
            int expected = orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
            AtomicReference<MainActivity> initialActivity = new AtomicReference<>();
            AtomicInteger initialConfiguration = new AtomicInteger();
            scenario.onActivity(activity -> {
                initialActivity.set(activity);
                initialConfiguration.set(activity.getResources().getConfiguration().orientation);
                activity.setRequestedOrientation(orientation);
            });
            waitForRequestedOrientation(scenario, initialActivity.get(), initialConfiguration.get(), expected);

            // scrollTo is essential: a direct performClick would bypass the inaccessible-screen bug.
            onView(withId(R.id.save_pref))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()))
                    .perform(click());

            assertEquals(Activity.RESULT_OK, scenario.getResult().getResultCode());
            assertEquals(widgetId, scenario.getResult().getResultData()
                    .getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1));
        } finally {
            BatteryWidgetPrefHelper.removeBatteryWidgetPref(context, widgetId);
        }
    }

    private void waitForRequestedOrientation(ActivityScenario<MainActivity> scenario,
                                             MainActivity initialActivity,
                                             int initialConfiguration,
                                             int expectedConfiguration) {
        boolean requiresRecreation = initialConfiguration != expectedConfiguration;
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        AtomicReference<MainActivity> currentActivity = new AtomicReference<>();
        AtomicInteger currentConfiguration = new AtomicInteger();
        AtomicReference<View> root = new AtomicReference<>();
        boolean ready = false;
        do {
            scenario.onActivity(activity -> {
                currentActivity.set(activity);
                currentConfiguration.set(activity.getResources().getConfiguration().orientation);
                root.set(activity.findViewById(R.id.id_container_activity_main));
            });
            View currentRoot = root.get();
            ready = currentConfiguration.get() == expectedConfiguration
                    && (!requiresRecreation || currentActivity.get() != initialActivity)
                    && currentRoot != null
                    && currentRoot.isLaidOut()
                    && currentRoot.getWidth() > 0
                    && currentRoot.getHeight() > 0;
            if (!ready) SystemClock.sleep(50);
        } while (!ready && SystemClock.elapsedRealtime() < deadline);

        assertTrue("Timed out waiting for the requested orientation and its laid-out Activity instance", ready);
        assertEquals(expectedConfiguration, currentConfiguration.get());
        // A recreated View can be ready while the system is still transforming its window.
        // Wait for window/accessibility events to settle before injecting screen coordinates.
        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(500, 5_000);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("The rotated window did not settle before the touch test", e);
        }
    }
}
