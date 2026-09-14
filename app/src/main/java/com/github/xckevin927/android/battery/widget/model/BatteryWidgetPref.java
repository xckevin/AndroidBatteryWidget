package com.github.xckevin927.android.battery.widget.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class BatteryWidgetPref implements Serializable {
    private static final long serialVersionUID = 7328661497078024390L;

    private boolean showWallpaper = false;

    private boolean showBackground = true;

    // Kept as literals so this configuration model remains usable in host-side unit tests.
    private int backgroundColor = 0xddffffff;

    private int backgroundColorInDarkMode = 0xdd333333;

    private int round = 16;

    private boolean showBackgroundProgress = false;

    private int lineWidth = 4;

    /** Phone remains the default; combined and device-only layouts are explicitly chosen. */
    private String widgetStyle = STYLE_PHONE;

    /** Addresses are deliberately stored, rather than names, because names are mutable. */
    private List<String> selectedDeviceAddresses = new ArrayList<>();

    /** Kept true for existing serialized widgets, which historically displayed every visible device. */
    private boolean showAllVisibleDevices = true;

    /** STATUS opens the monitoring screen. DEVICE opens the first selected device. */
    private String clickAction = CLICK_STATUS;

    public static final String STYLE_PHONE = "phone";
    public static final String STYLE_DEVICES = "devices";
    public static final String STYLE_COMBINED = "combined";
    public static final String CLICK_STATUS = "status";
    public static final String CLICK_DEVICE = "device";

    public BatteryWidgetPref() {
    }

    /** Creates an editing draft.  Widget previews must never mutate the persisted instance. */
    public BatteryWidgetPref(BatteryWidgetPref source) {
        if (source == null) return;
        showWallpaper = source.showWallpaper;
        showBackground = source.showBackground;
        backgroundColor = source.backgroundColor;
        backgroundColorInDarkMode = source.backgroundColorInDarkMode;
        round = source.round;
        showBackgroundProgress = source.showBackgroundProgress;
        lineWidth = source.lineWidth;
        widgetStyle = source.widgetStyle;
        selectedDeviceAddresses = new ArrayList<>(source.getSelectedDeviceAddresses());
        showAllVisibleDevices = source.showAllVisibleDevices;
        clickAction = source.clickAction;
    }

    public BatteryWidgetPref copy() {
        return new BatteryWidgetPref(this);
    }

    public boolean isShowWallpaper() {
        return showWallpaper;
    }

    public void setShowWallpaper(boolean showWallpaper) {
        this.showWallpaper = showWallpaper;
    }

    public boolean isShowBackground() {
        return showBackground;
    }

    public void setShowBackground(boolean showBackground) {
        this.showBackground = showBackground;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public int getBackgroundColorInDarkMode() {
        return backgroundColorInDarkMode;
    }

    public void setBackgroundColorInDarkMode(int backgroundColorInDarkMode) {
        this.backgroundColorInDarkMode = backgroundColorInDarkMode;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public boolean isShowBackgroundProgress() {
        return showBackgroundProgress;
    }

    public void setShowBackgroundProgress(boolean showBackgroundProgress) {
        this.showBackgroundProgress = showBackgroundProgress;
    }

    public int getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(int lineWidth) {
        this.lineWidth = lineWidth;
    }

    public String getWidgetStyle() {
        if (STYLE_PHONE.equals(widgetStyle) || STYLE_DEVICES.equals(widgetStyle)
                || STYLE_COMBINED.equals(widgetStyle)) return widgetStyle;
        return STYLE_PHONE;
    }

    public void setWidgetStyle(String widgetStyle) {
        this.widgetStyle = (STYLE_PHONE.equals(widgetStyle) || STYLE_DEVICES.equals(widgetStyle)
                || STYLE_COMBINED.equals(widgetStyle))
                ? widgetStyle : STYLE_PHONE;
    }

    public List<String> getSelectedDeviceAddresses() {
        return selectedDeviceAddresses == null ? new ArrayList<>() : new ArrayList<>(selectedDeviceAddresses);
    }

    public void setSelectedDeviceAddresses(List<String> addresses) {
        selectedDeviceAddresses = new ArrayList<>();
        if (addresses == null) return;
        for (String address : addresses) {
            if (address != null && !address.trim().isEmpty() && !selectedDeviceAddresses.contains(address)) {
                selectedDeviceAddresses.add(address);
            }
        }
    }

    public boolean isShowAllVisibleDevices() {
        return showAllVisibleDevices;
    }

    public void setShowAllVisibleDevices(boolean showAllVisibleDevices) {
        this.showAllVisibleDevices = showAllVisibleDevices;
    }

    public String getClickAction() {
        return CLICK_DEVICE.equals(clickAction) ? CLICK_DEVICE : CLICK_STATUS;
    }

    public void setClickAction(String clickAction) {
        this.clickAction = CLICK_DEVICE.equals(clickAction) ? CLICK_DEVICE : CLICK_STATUS;
    }
}
