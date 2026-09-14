package com.github.xckevin927.android.battery.widget;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.viewpager2.widget.ViewPager2;

import com.github.xckevin927.android.battery.widget.activity.TabActivity;
import com.github.xckevin927.android.battery.widget.appwidget.WidgetConstants;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/** Exercises real Activity intents without a cross-package ActivityScenario helper on OEM devices. */
@RunWith(AndroidJUnit4.class)
public class TabActivityDeviceNavigationTest {
    private static final String DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF";
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test
    public void coldDeviceIntentOpensBluetoothTab() {
        Context context = instrumentation.getTargetContext();
        Intent intent = new Intent(context, TabActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(WidgetConstants.EXTRA_DEVICE_ADDRESS, DEVICE_ADDRESS);
        TabActivity activity = (TabActivity) instrumentation.startActivitySync(intent);
        try {
            instrumentation.waitForIdleSync();
            assertBluetoothTabSelected(activity);
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    @Test
    public void warmDeviceIntentOpensBluetoothTab() {
        Context context = instrumentation.getTargetContext();
        TabActivity activity = (TabActivity) instrumentation.startActivitySync(
                new Intent(context, TabActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            instrumentation.runOnMainSync(() -> activity.startActivity(
                    new Intent(activity, TabActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra(WidgetConstants.EXTRA_DEVICE_ADDRESS, DEVICE_ADDRESS)));
            instrumentation.waitForIdleSync();
            assertBluetoothTabSelected(activity);
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    private void assertBluetoothTabSelected(TabActivity activity) {
        instrumentation.runOnMainSync(() -> assertEquals(
                2, ((ViewPager2) activity.findViewById(R.id.view_pager)).getCurrentItem()));
    }
}
