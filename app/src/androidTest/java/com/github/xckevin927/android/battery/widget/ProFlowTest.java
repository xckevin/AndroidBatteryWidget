package com.github.xckevin927.android.battery.widget;

import android.app.Activity;
import android.app.Instrumentation;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.RadioGroup;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.github.xckevin927.android.battery.widget.activity.MainActivity;
import com.github.xckevin927.android.battery.widget.activity.ProActivity;
import com.github.xckevin927.android.battery.widget.billing.ProBilling;
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper;

import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** No paid account or production entitlement bypass: these exercise real locked UI paths. */
@RunWith(AndroidJUnit4.class)
public class ProFlowTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private static final int WIDGET_ID = 81002;

    @Test public void unavailableCatalogHasNoBuyablePriceAndSurvivesRecreation() {
        assertFalse("Requires the isolated non-buyer image", ProBilling.hasPro(context));
        try (ActivityScenario<ProActivity> scenario = ActivityScenario.launch(
                new Intent(context, ProActivity.class))) {
            long deadline = SystemClock.elapsedRealtime() + 20_000;
            while (ProBilling.getState().getBusy() && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(100);
            }
            assertFalse("Unavailable Play must eventually expose a retry action", ProBilling.getState().getBusy());
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertFalse(ProBilling.hasPro(context));
                assertFalse(activity.findViewById(R.id.pro_purchase).isEnabled());
                assertNotNull(activity.findViewById(R.id.pro_restore));
                assertNotNull(activity.findViewById(R.id.pro_status));
                activity.finish();
            });
        }
    }

    @Test public void combinedSaveOpensProWithoutPersistingOrLosingDraft() {
        assertFalse("Requires the isolated non-buyer image", ProBilling.hasPro(context));
        BatteryWidgetPref baseline = new BatteryWidgetPref();
        baseline.setWidgetStyle(BatteryWidgetPref.STYLE_PHONE);
        BatteryWidgetPrefHelper.saveBatteryWidgetPref(context, WIDGET_ID, baseline);
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                ProActivity.class.getName(), null, false);
        Intent intent = new Intent(context, MainActivity.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WIDGET_ID);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                ((RadioGroup) activity.findViewById(R.id.widget_style_group))
                        .check(R.id.widget_style_combined);
                activity.findViewById(R.id.save_pref).performClick();
            });
            Activity pro = instrumentation.waitForMonitorWithTimeout(monitor, 5_000);
            assertNotNull("Save must open the upgrade page", pro);
            assertEquals(BatteryWidgetPref.STYLE_PHONE,
                    BatteryWidgetPrefHelper.getBatteryWidgetPref(context, WIDGET_ID).getWidgetStyle());
            instrumentation.runOnMainSync(pro::finish);
            instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals(R.id.widget_style_combined,
                        ((RadioGroup) activity.findViewById(R.id.widget_style_group)).getCheckedRadioButtonId());
                activity.finish();
            });
        } finally {
            instrumentation.removeMonitor(monitor);
            BatteryWidgetPrefHelper.removeBatteryWidgetPref(context, WIDGET_ID);
        }
    }
}
