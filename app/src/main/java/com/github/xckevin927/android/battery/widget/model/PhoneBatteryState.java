package com.github.xckevin927.android.battery.widget.model;

public class PhoneBatteryState {

    private boolean isUsbCharge;
    private boolean isAcCharge;
    private boolean isWirelessCharge;

    private boolean isInPowerSaveMode;
    private int level = -1;
    private int status = 1; // BatteryManager.BATTERY_STATUS_UNKNOWN
    private long checkedAtMillis;
    private boolean plugged;

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public long getCheckedAtMillis() { return checkedAtMillis; }
    public void setCheckedAtMillis(long value) { checkedAtMillis = value; }
    public boolean isCharging() { return status == 2; }
    public boolean isPlugged() { return plugged || isUsbCharge || isAcCharge || isWirelessCharge; }
    public void setPlugged(boolean value) { plugged = value; }

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
