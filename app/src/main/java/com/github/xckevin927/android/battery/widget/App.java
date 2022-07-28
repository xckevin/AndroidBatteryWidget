package com.github.xckevin927.android.battery.widget;

import android.app.Application;

public class App extends Application {

    private static App sApp;

    public static App getAppContext() {
        return sApp;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sApp = this;
    }
}
