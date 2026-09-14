package com.github.xckevin927.android.battery.widget;

import android.app.Application;
import android.content.Context;

import com.github.xckevin927.android.battery.widget.receiver.BatteryWorker;
import com.github.xckevin927.android.battery.widget.utils.ReflectUtil;

import com.github.xckevin927.android.battery.widget.utils.NotificationUtil;
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo;
import com.github.xckevin927.android.battery.widget.repo.VisibleAppObserver;
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService;
import com.github.xckevin927.android.battery.widget.alerts.BatteryAlerts;
import com.github.xckevin927.android.battery.widget.billing.ProBilling;

public class App extends Application {

    private static App sApp;
    private boolean lastProEntitlement;

    public static App getAppContext() {
        return sApp;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sApp = this;
        ProBilling.initialize(this);
        lastProEntitlement = ProBilling.hasPro(this);
        ProBilling.addListener(() -> {
            boolean owned = ProBilling.hasPro(this);
            if (owned != lastProEntitlement) {
                lastProEntitlement = owned;
                WidgetUpdateService.renderWidgets(this);
                if (owned) BatteryAlerts.evaluate(this, BatteryRepo.INSTANCE.getBatteryState(),
                        BatteryRepo.getBtSnapshot());
            }
        });
        NotificationUtil.createNotificationChannel(this);
        registerActivityLifecycleCallbacks(new VisibleAppObserver(this));
        BatteryRepo.addListener(() -> {
            WidgetUpdateService.renderWidgets(this);
            BatteryAlerts.evaluate(this, BatteryRepo.INSTANCE.getBatteryState(), BatteryRepo.getBtSnapshot());
        });

        BatteryWorker.Companion.start(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        ReflectUtil.init();
    }
}
