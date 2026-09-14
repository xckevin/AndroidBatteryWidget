package com.github.xckevin927.android.battery.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RadioGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.github.xckevin927.android.battery.widget.activity.MainActivity;
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ConfigurationFlowTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final int WIDGET_ID = 81001;

    private Intent configureIntent() {
        return new Intent(context, MainActivity.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WIDGET_ID);
    }

    @Test public void saveReturnsWidgetIdAndSurvivesRecreation() {
        BatteryWidgetPrefHelper.removeBatteryWidgetPref(context, WIDGET_ID);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launchActivityForResult(configureIntent())) {
            scenario.onActivity(activity -> ((RadioGroup) activity.findViewById(R.id.widget_style_group))
                    .check(R.id.widget_style_devices));
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(R.id.widget_style_devices,
                        ((RadioGroup) activity.findViewById(R.id.widget_style_group)).getCheckedRadioButtonId());
                activity.findViewById(R.id.save_pref).performClick();
            });
            assertEquals(Activity.RESULT_OK, scenario.getResult().getResultCode());
            assertEquals(WIDGET_ID, scenario.getResult().getResultData()
                    .getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1));
            assertEquals(BatteryWidgetPref.STYLE_DEVICES,
                    BatteryWidgetPrefHelper.getBatteryWidgetPref(context, WIDGET_ID).getWidgetStyle());
        } finally {
            BatteryWidgetPrefHelper.removeBatteryWidgetPref(context, WIDGET_ID);
        }
    }

    @Test public void cancelReturnsCancelledAndDoesNotPersistDraft() {
        BatteryWidgetPref baseline = new BatteryWidgetPref();
        baseline.setWidgetStyle(BatteryWidgetPref.STYLE_PHONE);
        BatteryWidgetPrefHelper.saveBatteryWidgetPref(context, WIDGET_ID, baseline);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launchActivityForResult(configureIntent())) {
            scenario.onActivity(activity -> {
                ((RadioGroup) activity.findViewById(R.id.widget_style_group)).check(R.id.widget_style_devices);
                activity.findViewById(R.id.add_widget).performClick();
            });
            assertEquals(Activity.RESULT_CANCELED, scenario.getResult().getResultCode());
            assertEquals(BatteryWidgetPref.STYLE_PHONE,
                    BatteryWidgetPrefHelper.getBatteryWidgetPref(context, WIDGET_ID).getWidgetStyle());
        } finally {
            BatteryWidgetPrefHelper.removeBatteryWidgetPref(context, WIDGET_ID);
        }
    }
}
