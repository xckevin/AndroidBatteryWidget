package com.github.xckevin927.android.battery.widget;

import com.github.xckevin927.android.battery.widget.appwidget.WidgetLayoutPolicy;
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Documents the compact/vertical/horizontal breakpoints without an Android runtime. */
public class WidgetLayoutPolicyTest {
    @Test
    public void resolvesWideNarrowAndTooShortCombinedWidgets() {
        assertEquals(WidgetLayoutPolicy.Mode.HORIZONTAL_COMBINED,
                WidgetLayoutPolicy.resolve(BatteryWidgetPref.STYLE_COMBINED, 180, 100));
        assertEquals(WidgetLayoutPolicy.Mode.VERTICAL_COMBINED,
                WidgetLayoutPolicy.resolve(BatteryWidgetPref.STYLE_COMBINED, 120, 180));
        assertEquals(WidgetLayoutPolicy.Mode.PHONE,
                WidgetLayoutPolicy.resolve(BatteryWidgetPref.STYLE_COMBINED, 120, 120));
        assertEquals(WidgetLayoutPolicy.Mode.PHONE,
                WidgetLayoutPolicy.resolve(BatteryWidgetPref.STYLE_COMBINED, 240, 80));
    }

    @Test
    public void explicitPhoneAndDevicesStylesIgnoreCombinedBreakpoints() {
        assertEquals(WidgetLayoutPolicy.Mode.PHONE,
                WidgetLayoutPolicy.resolve(BatteryWidgetPref.STYLE_PHONE, 500, 500));
        assertEquals(WidgetLayoutPolicy.Mode.DEVICES,
                WidgetLayoutPolicy.resolve(BatteryWidgetPref.STYLE_DEVICES, 40, 40));
    }

    @Test
    public void combinedStyleFallsBackToPhoneWithoutProWhileFreeStylesRemainAvailable() {
        assertEquals(WidgetLayoutPolicy.Mode.PHONE,
                WidgetLayoutPolicy.resolve(BatteryWidgetPref.STYLE_COMBINED, 300, 300, false));
        assertEquals(WidgetLayoutPolicy.Mode.PHONE,
                WidgetLayoutPolicy.resolve(BatteryWidgetPref.STYLE_PHONE, 300, 300, false));
        assertEquals(WidgetLayoutPolicy.Mode.DEVICES,
                WidgetLayoutPolicy.resolve(BatteryWidgetPref.STYLE_DEVICES, 300, 300, false));
    }
}
