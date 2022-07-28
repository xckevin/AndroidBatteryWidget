package com.github.xckevin927.android.battery.widget.utils;

import android.widget.Toast;

import com.github.xckevin927.android.battery.widget.App;

public class ToastUtil {

    public static void toast(CharSequence text) {
        Toast.makeText(App.getAppContext(), text, Toast.LENGTH_SHORT).show();
    }
}
