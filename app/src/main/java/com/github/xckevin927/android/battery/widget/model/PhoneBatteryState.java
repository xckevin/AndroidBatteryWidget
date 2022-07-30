package com.github.xckevin927.android.battery.widget.model;

public class PhoneBatteryState {

    private boolean isUsbCharge;
    private boolean isAcCharge;
    private boolean isWirelessCharge;

    private boolean isInPowerSaveMode;
    private int level;

    public boolean isUsbCharge() {
        return isUsbCharge;
    }

    public boolean isAcCharge() {
        return isAcCharge;
    }

    public boolean isInPowerSaveMode() {
        return isInPowerSaveMode;
    }

    public int getLevel() {
        return level;
    }

    public boolean isWirelessCharge() {
        return isWirelessCharge;
    }

    public void setUsbCharge(boolean usbCharge) {
        isUsbCharge = usbCharge;
    }

    public void setAcCharge(boolean acCharge) {
        isAcCharge = acCharge;
    }

    public void setWirelessCharge(boolean wirelessCharge) {
        isWirelessCharge = wirelessCharge;
    }

    public void setInPowerSaveMode(boolean inPowerSaveMode) {
        isInPowerSaveMode = inPowerSaveMode;
    }

    public void setLevel(int level) {
        this.level = level;
    }
}
