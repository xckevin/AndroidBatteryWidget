package com.github.xckevin927.android.battery.widget;

import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Guards the draft/per-widget model invariants without requiring Android storage. */
public class BatteryWidgetPrefTest {
    @Test
    public void copyKeepsDraftIndependentAndDeduplicatesDeviceAddresses() {
        BatteryWidgetPref saved = new BatteryWidgetPref();
        saved.setWidgetStyle(BatteryWidgetPref.STYLE_COMBINED);
        saved.setSelectedDeviceAddresses(Arrays.asList("AA:BB", "AA:BB", "", "CC:DD"));

        BatteryWidgetPref draft = saved.copy();
        draft.setSelectedDeviceAddresses(Arrays.asList("CC:DD"));
        draft.setClickAction(BatteryWidgetPref.CLICK_DEVICE);

        assertEquals(Arrays.asList("AA:BB", "CC:DD"), saved.getSelectedDeviceAddresses());
        assertEquals(Arrays.asList("CC:DD"), draft.getSelectedDeviceAddresses());
        assertEquals(BatteryWidgetPref.CLICK_STATUS, saved.getClickAction());
        assertEquals(BatteryWidgetPref.CLICK_DEVICE, draft.getClickAction());
    }

    @Test
    public void invalidStyleAndActionFallBackToSafePhoneStatusDefaults() {
        BatteryWidgetPref pref = new BatteryWidgetPref();
        pref.setWidgetStyle("unexpected");
        pref.setClickAction("unexpected");
        assertEquals(BatteryWidgetPref.STYLE_PHONE, pref.getWidgetStyle());
        assertEquals(BatteryWidgetPref.CLICK_STATUS, pref.getClickAction());
        assertFalse(pref.getSelectedDeviceAddresses().contains(""));
        assertTrue(new BatteryWidgetPref().getSelectedDeviceAddresses().isEmpty());
    }
}
