package com.github.xckevin927.android.battery.widget.utils;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothClass.Device;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Pair;

import androidx.core.content.ContextCompat;

import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.model.BtDeviceState;

public class BtUtil {

    public static final int PROFILE_HEADSET = 0;
    /** @hide */
    public static final int PROFILE_A2DP = 1;
    /** @hide */
    public static final int PROFILE_OPP = 2;
    /** @hide */
    public static final int PROFILE_HID = 3;
    /** @hide */
    public static final int PROFILE_PANU = 4;
    /** @hide */
    public static final int PROFILE_NAP = 5;
    /** @hide */
    public static final int PROFILE_A2DP_SINK = 6;


    public static final int PERIPHERAL_KEYBOARD = 0x0540;
    /**
     * @hide
     */
    public static final int PERIPHERAL_POINTING = 0x0580;
    /**
     * @hide
     */
    public static final int PERIPHERAL_KEYBOARD_POINTING = 0x05C0;

    public static Pair<Drawable, String> getBtClassDrawableWithDescription(Context context,
                                                                           BluetoothDevice cachedDevice) {
        @SuppressLint("MissingPermission") BluetoothClass btClass = cachedDevice.getBluetoothClass();
        if (btClass != null) {
            switch (btClass.getMajorDeviceClass()) {
                case BluetoothClass.Device.Major.COMPUTER:
                    return new Pair<>(getBluetoothDrawable(context,
                            R.drawable.ic_bt_laptop),
                            context.getString(R.string.bluetooth_talkback_computer));

                case BluetoothClass.Device.Major.PHONE:
                    return new Pair<>(
                            getBluetoothDrawable(context,
                                    R.drawable.ic_phone),
                            context.getString(R.string.bluetooth_talkback_phone));

                case BluetoothClass.Device.Major.PERIPHERAL:
                    return new Pair<>(
                            getBluetoothDrawable(context, getHidClassDrawable(btClass)),
                            context.getString(R.string.bluetooth_talkback_input_peripheral));

                case BluetoothClass.Device.Major.IMAGING:
                    return new Pair<>(
                            getBluetoothDrawable(context,
                                    R.drawable.ic_settings_print),
                            context.getString(R.string.bluetooth_talkback_imaging));

                default:
                    // unrecognized device class; continue
            }
        }

//        List<LocalBluetoothProfile> profiles = cachedDevice.getProfiles();
//        for (LocalBluetoothProfile profile : profiles) {
//            int resId = profile.getDrawableResource(btClass);
//            if (resId != 0) {
//                return new Pair<>(getBluetoothDrawable(context, resId), null);
//            }
//        }
        if (btClass != null) {
            if (doesClassMatch(btClass, PROFILE_HEADSET)) {
                return new Pair<>(
                        getBluetoothDrawable(context,
                                R.drawable.ic_bt_headset_hfp),
                        context.getString(R.string.bluetooth_talkback_headset));
            }
            if (doesClassMatch(btClass, PROFILE_A2DP)) {
                return new Pair<>(
                        getBluetoothDrawable(context,
                                R.drawable.ic_bt_headphones_a2dp),
                        context.getString(R.string.bluetooth_talkback_headphone));
            }
        }
        return new Pair<>(
                getBluetoothDrawable(context,
                        R.drawable.ic_settings_bluetooth).mutate(),
                context.getString(R.string.bluetooth_talkback_bluetooth));
    }

    private static Drawable getBluetoothDrawable(Context context, int res) {
        return ContextCompat.getDrawable(context, res);
    }

    public static int getHidClassDrawable(BluetoothClass btClass) {
        switch (btClass.getDeviceClass()) {
            case PERIPHERAL_KEYBOARD:
            case PERIPHERAL_KEYBOARD_POINTING:
                return R.drawable.ic_lockscreen_ime;
            case PERIPHERAL_POINTING:
                return R.drawable.ic_bt_pointing_hid;
            default:
                return R.drawable.ic_bt_misc_hid;
        }
    }

    public static boolean doesClassMatch(BluetoothClass bluetoothClass, int profile) {
        if (profile == PROFILE_A2DP) {
            if (bluetoothClass.hasService(BluetoothClass.Service.RENDER)) {
                return true;
            }
            // By the A2DP spec, sinks must indicate the RENDER service.
            // However we found some that do not (Chordette). So lets also
            // match on some other class bits.
            switch (bluetoothClass.getDeviceClass()) {
                case Device.AUDIO_VIDEO_HIFI_AUDIO:
                case Device.AUDIO_VIDEO_HEADPHONES:
                case Device.AUDIO_VIDEO_LOUDSPEAKER:
                case Device.AUDIO_VIDEO_CAR_AUDIO:
                    return true;
                default:
                    return false;
            }
        } else if (profile == PROFILE_A2DP_SINK) {
            if (bluetoothClass.hasService(BluetoothClass.Service.CAPTURE)) {
                return true;
            }
            // By the A2DP spec, srcs must indicate the CAPTURE service.
            // However if some device that do not, we try to
            // match on some other class bits.
            switch (bluetoothClass.getDeviceClass()) {
                case Device.AUDIO_VIDEO_HIFI_AUDIO:
                case Device.AUDIO_VIDEO_SET_TOP_BOX:
                case Device.AUDIO_VIDEO_VCR:
                    return true;
                default:
                    return false;
            }
        } else if (profile == PROFILE_HEADSET) {
            // The render service class is required by the spec for HFP, so is a
            // pretty good signal
            if (bluetoothClass.hasService(BluetoothClass.Service.RENDER)) {
                return true;
            }
            // Just in case they forgot the render service class
            switch (bluetoothClass.getDeviceClass()) {
                case Device.AUDIO_VIDEO_HANDSFREE:
                case Device.AUDIO_VIDEO_WEARABLE_HEADSET:
                case Device.AUDIO_VIDEO_CAR_AUDIO:
                    return true;
                default:
                    return false;
            }
        } else if (profile == PROFILE_OPP) {
            if (bluetoothClass.hasService(BluetoothClass.Service.OBJECT_TRANSFER)) {
                return true;
            }

            switch (bluetoothClass.getDeviceClass()) {
                case Device.COMPUTER_UNCATEGORIZED:
                case Device.COMPUTER_DESKTOP:
                case Device.COMPUTER_SERVER:
                case Device.COMPUTER_LAPTOP:
                case Device.COMPUTER_HANDHELD_PC_PDA:
                case Device.COMPUTER_PALM_SIZE_PC_PDA:
                case Device.COMPUTER_WEARABLE:
                case Device.PHONE_UNCATEGORIZED:
                case Device.PHONE_CELLULAR:
                case Device.PHONE_CORDLESS:
                case Device.PHONE_SMART:
                case Device.PHONE_MODEM_OR_GATEWAY:
                case Device.PHONE_ISDN:
                    return true;
                default:
                    return false;
            }
        } else if (profile == PROFILE_HID) {
            return bluetoothClass.getMajorDeviceClass() == Device.Major.PERIPHERAL;
        } else if (profile == PROFILE_PANU || profile == PROFILE_NAP) {
            // No good way to distinguish between the two, based on class bits.
            if (bluetoothClass.hasService(BluetoothClass.Service.NETWORKING)) {
                return true;
            }
            return bluetoothClass.getMajorDeviceClass() == Device.Major.NETWORKING;
        } else {
            return false;
        }
    }

    public static int getBtDeviceIndicatorColor(Context context, BtDeviceState deviceState) {
        if (deviceState.getBatteryLevel() >= 0) {
            return ContextCompat.getColor(context, R.color.bt_state_active);
        }
        if (deviceState.isConnected()) {
            return ContextCompat.getColor(context, R.color.bt_state_connected);
        }
        return ContextCompat.getColor(context, R.color.bt_state_unconnected);
    }
}
