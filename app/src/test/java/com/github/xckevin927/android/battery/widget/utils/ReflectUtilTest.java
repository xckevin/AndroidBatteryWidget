package com.github.xckevin927.android.battery.widget.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ReflectUtilTest {
    @Test
    public void availableReadWorksWithoutBypassInitialization() {
        Integer level = ReflectUtil.invoke(new ReadableDevice(), "getBatteryLevel", new Class<?>[0]);
        assertEquals(Integer.valueOf(73), level);
    }

    @Test
    public void exactParameterTypesPreserveOverloadsAndNullArguments() {
        ReadableDevice device = new ReadableDevice();
        String text = ReflectUtil.invoke(device, "label", new Class<?>[] { String.class, int.class }, null, 2);
        String number = ReflectUtil.invoke(device, "label", new Class<?>[] { Integer.class, int.class }, null, 2);
        assertEquals("text:null:2", text);
        assertEquals("number:null:2", number);
    }

    @Test
    public void targetFailureReturnsUnknownWithoutExecutingTwice() {
        ReadableDevice device = new ReadableDevice();
        assertNull(ReflectUtil.invoke(device, "failingRead", new Class<?>[0]));
        assertEquals(1, device.attempts);
    }

    public static class ReadableDevice {
        int attempts;
        public int getBatteryLevel() { return 73; }
        public String label(String prefix, int index) { return "text:" + prefix + ":" + index; }
        public String label(Integer prefix, int index) { return "number:" + prefix + ":" + index; }
        public int failingRead() {
            attempts++;
            throw new SecurityException("Read denied");
        }
    }
}
