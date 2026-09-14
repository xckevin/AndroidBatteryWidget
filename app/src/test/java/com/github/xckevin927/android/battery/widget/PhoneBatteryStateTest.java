package com.github.xckevin927.android.battery.widget;

import com.github.xckevin927.android.battery.widget.model.PhoneBatteryState;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhoneBatteryStateTest {
    @Test public void pluggedButPausedIsNotCharging() {
        PhoneBatteryState value = new PhoneBatteryState();
        value.setUsbCharge(true);
        value.setStatus(4); // NOT_CHARGING
        assertTrue(value.isPlugged());
        assertFalse(value.isCharging());
    }
    @Test public void fullAndUnknownAreNotActivelyCharging() {
        PhoneBatteryState value = new PhoneBatteryState();
        assertEquals(-1, value.getLevel());
        assertFalse(value.isCharging());
        value.setStatus(5);
        assertFalse(value.isCharging());
        value.setStatus(2);
        assertTrue(value.isCharging());
    }
}
