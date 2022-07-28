package com.github.xckevin927.android.battery.widget.activity;

import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;

import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.activity.fragment.BtDeviceFragment;
import com.github.xckevin927.android.battery.widget.utils.ToastUtil;

public class BtActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bt);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            ToastUtil.toast("your app do not support bluetooth");
            return;
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.id_content_activity_bt, BtDeviceFragment.newInstance(1))
                    .commit();
        }
    }
}