package com.github.xckevin927.android.battery.widget.model;

public class BtDeviceState {

    private String addr;
    private String name;
    private String alias;
    private int type;
    private int batteryLevel;
    private boolean bond;

    private BtDeviceState(String addr, String name, String alias, int type, int batteryLevel, boolean bond) {
        this.addr = addr;
        this.name = name;
        this.alias = alias;
        this.type = type;
        this.batteryLevel = batteryLevel;
        this.bond = bond;
    }

    public String getAddr() {
        return addr;
    }

    public String getName() {
        return name;
    }

    public String getAlias() {
        return alias;
    }

    public int getType() {
        return type;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public boolean isBond() {
        return bond;
    }

    @Override
    public String toString() {
        return "BtDeviceState{" +
                "addr='" + addr + '\'' +
                ", name='" + name + '\'' +
                ", alias='" + alias + '\'' +
                ", type=" + Integer.toHexString(type) +
                ", batteryLevel=" + batteryLevel +
                ", bond=" + bond +
                '}';
    }

    public static BtDeviceStateBuilder builder() {
        return new BtDeviceStateBuilder();
    }

    public static final class BtDeviceStateBuilder {
        private String addr;
        private String name;
        private String alias;
        private int type;
        private int batteryLevel;
        private boolean bond;

        private BtDeviceStateBuilder() {
        }



        public BtDeviceStateBuilder withAddr(String addr) {
            this.addr = addr;
            return this;
        }

        public BtDeviceStateBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public BtDeviceStateBuilder withAlias(String alias) {
            this.alias = alias;
            return this;
        }

        public BtDeviceStateBuilder withType(int type) {
            this.type = type;
            return this;
        }

        public BtDeviceStateBuilder withBond(boolean bond) {
            this.bond = bond;
            return this;
        }

        public BtDeviceStateBuilder withBatteryLevel(int batteryLevel) {
            this.batteryLevel = batteryLevel;
            return this;
        }

        public BtDeviceState build() {
            return new BtDeviceState(addr, name, alias, type, batteryLevel, bond);
        }
    }
}
