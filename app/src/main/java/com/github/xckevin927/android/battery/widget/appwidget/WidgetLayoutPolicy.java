package com.github.xckevin927.android.battery.widget.appwidget;

import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;

/** Pure size policy so widgets remain readable instead of merely stretching a bitmap. */
public final class WidgetLayoutPolicy {
    public static final int WIDE_DP = 180;
    public static final int HORIZONTAL_MIN_HEIGHT_DP = 100;
    public static final int VERTICAL_MIN_HEIGHT_DP = 180;

    public enum Mode { PHONE, DEVICES, VERTICAL_COMBINED, HORIZONTAL_COMBINED }

    private WidgetLayoutPolicy() { }

    public static Mode resolve(String style, int widthDp, int heightDp) {
        return resolve(style, widthDp, heightDp, true);
    }

    /** Entitlement is an input so this policy stays deterministic and testable. */
    public static Mode resolve(String style, int widthDp, int heightDp, boolean hasPro) {
        if (requiresPro(style) && !hasPro) return Mode.PHONE;
        if (BatteryWidgetPref.STYLE_DEVICES.equals(style)) return Mode.DEVICES;
        if (!BatteryWidgetPref.STYLE_COMBINED.equals(style)) return Mode.PHONE;
        if (widthDp >= WIDE_DP && heightDp >= HORIZONTAL_MIN_HEIGHT_DP) return Mode.HORIZONTAL_COMBINED;
        if (widthDp < WIDE_DP && heightDp >= VERTICAL_MIN_HEIGHT_DP) return Mode.VERTICAL_COMBINED;
        return Mode.PHONE;
    }

    public static boolean requiresPro(String style) {
        return BatteryWidgetPref.STYLE_COMBINED.equals(style);
    }
}
