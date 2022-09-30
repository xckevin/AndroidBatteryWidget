package com.github.xckevin927.android.battery.widget.activity;

import android.os.Bundle;

import androidx.appcompat.app.ActionBar;

import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.activity.fragment.phone.BatteryWidgetConfigFragment;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.id_content_activity_main, new BatteryWidgetConfigFragment())
                    .commit();
        }
    }
}