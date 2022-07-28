package com.github.xckevin927.android.battery.widget.activity;

import androidx.appcompat.app.ActionBar;

import android.os.Bundle;

import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.activity.fragment.BtDeviceFragment;

public class BtActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bt);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.id_content_activity_bt, new BtDeviceFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }
}