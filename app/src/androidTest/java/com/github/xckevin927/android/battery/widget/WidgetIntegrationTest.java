package com.github.xckevin927.android.battery.widget;

import android.app.UiAutomation;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.github.xckevin927.android.battery.widget.appwidget.BatteryWidget;
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * API 36 emulator integration tests. The rendering test needs shell-granted widget-host binding;
 * it deliberately skips on images where the platform does not permit that grant.
 */
@RunWith(AndroidJUnit4.class)
public class WidgetIntegrationTest {
    private static final String PREF_NAME = "BATTERY_PREF";
    private static final String INSTANCE_PREFIX = "BATTERY_PREF_WIDGET_";
    private static final int FIRST_ID = 70_001;
    private static final int SECOND_ID = 70_002;
    private static final int RESTORED_ID = 70_003;
    private static final int HOST_ID = 0xB477;

    private Context context;

    @Before
    public void clearWidgetPreferences() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @After
    public void clearWidgetPreferencesAfterTest() {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void preferencesArePerWidgetAndMissingIdFallsBackToLegacyDefault() {
        BatteryWidgetPref legacy = new BatteryWidgetPref();
        legacy.setBackgroundColor(0xff101010);
        legacy.setWidgetStyle(BatteryWidgetPref.STYLE_COMBINED);
        BatteryWidgetPrefHelper.saveBatteryWidgetPref(context, legacy);

        BatteryWidgetPref first = legacy.copy();
        first.setBackgroundColor(0xff112233);
        first.setSelectedDeviceAddresses(java.util.Arrays.asList("AA:BB"));
        BatteryWidgetPref second = legacy.copy();
        second.setBackgroundColor(0xff445566);
        second.setSelectedDeviceAddresses(java.util.Arrays.asList("CC:DD"));
        BatteryWidgetPrefHelper.saveBatteryWidgetPref(context, FIRST_ID, first);
        BatteryWidgetPrefHelper.saveBatteryWidgetPref(context, SECOND_ID, second);

        assertEquals(0xff101010,
                BatteryWidgetPrefHelper.getBatteryWidgetPref(context, 79_999).getBackgroundColor());
        assertEquals(0xff112233,
                BatteryWidgetPrefHelper.getBatteryWidgetPref(context, FIRST_ID).getBackgroundColor());
        assertEquals(java.util.Arrays.asList("AA:BB"),
                BatteryWidgetPrefHelper.getBatteryWidgetPref(context, FIRST_ID).getSelectedDeviceAddresses());
        assertEquals(0xff445566,
                BatteryWidgetPrefHelper.getBatteryWidgetPref(context, SECOND_ID).getBackgroundColor());
        assertEquals(java.util.Arrays.asList("CC:DD"),
                BatteryWidgetPrefHelper.getBatteryWidgetPref(context, SECOND_ID).getSelectedDeviceAddresses());
    }

    @Test
    public void migratedLegacyWidgetKeepsItsAppearanceWhenDefaultsChange() {
        BatteryWidgetPref legacy = new BatteryWidgetPref();
        legacy.setBackgroundColor(0xff202020);
        BatteryWidgetPrefHelper.saveBatteryWidgetPref(context, legacy);
        assertEquals(0xff202020, BatteryWidgetPrefHelper.getBatteryWidgetPref(context, FIRST_ID).getBackgroundColor());
        legacy.setBackgroundColor(0xffeeeeee);
        BatteryWidgetPrefHelper.saveBatteryWidgetPref(context, legacy);
        assertEquals(0xff202020, BatteryWidgetPrefHelper.getBatteryWidgetPref(context, FIRST_ID).getBackgroundColor());
        assertEquals(0xffeeeeee, BatteryWidgetPrefHelper.getBatteryWidgetPref(context, SECOND_ID).getBackgroundColor());
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(INSTANCE_PREFIX + RESTORED_ID, "{\"backgroundColor\":-1}").commit();
        assertTrue(BatteryWidgetPrefHelper.getBatteryWidgetPref(context, RESTORED_ID).isShowAllVisibleDevices());
    }

    @Test
    public void discardedDraftDoesNotMutatePersistedInstanceAndRestoreMovesKey() {
        BatteryWidgetPref persisted = new BatteryWidgetPref();
        persisted.setBackgroundColor(0xff0a0b0c);
        persisted.setSelectedDeviceAddresses(java.util.Arrays.asList("11:22"));
        BatteryWidgetPrefHelper.saveBatteryWidgetPref(context, FIRST_ID, persisted);

        // Simulates editing then cancelling: no save method is invoked for the independent draft.
        BatteryWidgetPref draft = BatteryWidgetPrefHelper.getBatteryWidgetPref(context, FIRST_ID);
        draft.setBackgroundColor(0xfffefefe);
        draft.setSelectedDeviceAddresses(java.util.Arrays.asList("33:44"));
        assertEquals(0xff0a0b0c,
                BatteryWidgetPrefHelper.getBatteryWidgetPref(context, FIRST_ID).getBackgroundColor());
        assertEquals(java.util.Arrays.asList("11:22"),
                BatteryWidgetPrefHelper.getBatteryWidgetPref(context, FIRST_ID).getSelectedDeviceAddresses());

        BatteryWidgetPrefHelper.restoreBatteryWidgetPref(context, FIRST_ID, RESTORED_ID);
        assertEquals(0xff0a0b0c,
                BatteryWidgetPrefHelper.getBatteryWidgetPref(context, RESTORED_ID).getBackgroundColor());
        assertFalse(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .contains(INSTANCE_PREFIX + FIRST_ID));

        BatteryWidgetPrefHelper.removeBatteryWidgetPref(context, RESTORED_ID);
        assertFalse(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .contains(INSTANCE_PREFIX + RESTORED_ID));
    }

    @Test
    public void boundHostAcceptsPhoneAndCombinedUpdatesAtNarrowAndWideSizes() throws Exception {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        AppWidgetHost host = new AppWidgetHost(context, HOST_ID);
        int widgetId = host.allocateAppWidgetId();
        try {
            grantWidgetBinding();
            ComponentName provider = new ComponentName(context, BatteryWidget.class);
            boolean bound = manager.bindAppWidgetIdIfAllowed(widgetId, provider);
            assertTrue("Test image must allow shell-granted AppWidget binding", bound);

            AppWidgetProviderInfo info = manager.getAppWidgetInfo(widgetId);
            assertNotNull(info);
            final AppWidgetHostView[] hostView = new AppWidgetHostView[1];
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                host.startListening();
                hostView[0] = host.createView(context, widgetId, info);
            });
            assertEquals(widgetId, hostView[0].getAppWidgetId());

            BatteryWidgetPref combined = new BatteryWidgetPref();
            combined.setWidgetStyle(BatteryWidgetPref.STYLE_COMBINED);
            BatteryWidgetPrefHelper.saveBatteryWidgetPref(context, widgetId, combined);

            Bundle narrow = new Bundle();
            narrow.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 100);
            narrow.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 100);
            manager.updateAppWidgetOptions(widgetId, narrow);
            new BatteryWidget().onAppWidgetOptionsChanged(context, manager, widgetId, narrow);

            Bundle wide = new Bundle();
            wide.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240);
            wide.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120);
            manager.updateAppWidgetOptions(widgetId, wide);
            new BatteryWidget().onAppWidgetOptionsChanged(context, manager, widgetId, wide);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotNull(hostView[0]);
        } finally {
            try {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(host::stopListening);
            } catch (RuntimeException ignored) {
                // Host was never started when the platform skipped binding.
            }
            host.deleteAppWidgetId(widgetId);
        }
    }

    private void grantWidgetBinding() throws IOException {
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        ParcelFileDescriptor output = automation.executeShellCommand(
                "appwidget grantbind --package " + context.getPackageName() + " --user 0");
        if (output != null) {
            try (java.io.InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(output)) {
                byte[] buffer = new byte[1024];
                while (stream.read(buffer) != -1) { /* Wait for the grant command to finish. */ }
            }
        }
    }
}
