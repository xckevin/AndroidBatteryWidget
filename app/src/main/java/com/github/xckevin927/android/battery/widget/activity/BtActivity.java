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

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.id_content_activity_bt, BtDeviceFragment.newInstance(getResources().getConfiguration().screenWidthDp >= 600 ? 2 : 1))
                    .commit();
        }
    }
}