package com.github.xckevin927.android.battery.widget.model;

public class PhoneBatteryState {

    private boolean isUsbCharge;
    private boolean isAcCharge;
    private boolean isInPowerSaveMode;
    private int level;

    public static Builder builder() {
        return new Builder();
    }

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

    public static final class Builder {
        private boolean isUsbCharge;
        private boolean isAcCharge;
        private boolean isInPowerSaveMode;
        private int level;

        private Builder() {
        }


        public Builder withUsbCharge(boolean isUsbCharge) {
            this.isUsbCharge = isUsbCharge;
            return this;
        }

        public Builder withAcCharge(boolean isAcCharge) {
            this.isAcCharge = isAcCharge;
            return this;
        }

        public Builder withPowerSaveMode(boolean isInPowerSaveMode) {
            this.isInPowerSaveMode = isInPowerSaveMode;
            return this;
        }

        public Builder withLevel(int level) {
            this.level = level;
            return this;
        }

        public PhoneBatteryState build() {
            PhoneBatteryState batteryState = new PhoneBatteryState();
            batteryState.isAcCharge = this.isAcCharge;
            batteryState.isInPowerSaveMode = this.isInPowerSaveMode;
            batteryState.level = this.level;
            batteryState.isUsbCharge = this.isUsbCharge;
            return batteryState;
        }
    }
}
