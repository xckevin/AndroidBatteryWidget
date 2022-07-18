package com.github.xckevin927.android.battery.widget.activity;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.receiver.BatteryWidget;
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService;
import com.github.xckevin927.android.battery.widget.utils.BatteryUtil;
import com.github.xckevin927.android.battery.widget.utils.Utils;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WidgetUpdateService.start(getApplicationContext());

        setContentView(R.layout.activity_main);

        initViews();
    }

    private void initViews() {
        requestRenderWallpaper();
        setUpBatteryContent();
        renderBatteryWidget();

        findViewById(R.id.add_widget).setOnClickListener(v -> requestToPinWidget());
    }

    private void setUpBatteryContent() {
        Size size =  Utils.getScreenWidth(this);
        int width = Math.min(size.getWidth(), size.getHeight()) / 2;
        View view = findViewById(R.id.battery_container);
        ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(width, width);
        layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        view.setLayoutParams(layoutParams);
    }

    private void requestRenderWallpaper() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
                @Override
                public void onActivityResult(Boolean result) {
                    renderWallpaper();
                }
            }).launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            renderWallpaper();
        }
    }

    private void renderWallpaper() {
        WallpaperManager wallpaperManager = (WallpaperManager) getSystemService(WALLPAPER_SERVICE);
        @SuppressLint("MissingPermission")
        Drawable drawable = wallpaperManager.getFastDrawable();
        findViewById(R.id.id_container_activity_main).setBackground(drawable);
    }

    private void renderBatteryWidget() {
        Bitmap bitmap = Utils.generateBatteryBitmap(this, BatteryUtil.getBatteryState(this));
        ImageView imageView = findViewById(R.id.appwidget_progress);
        imageView.setImageBitmap(bitmap);
    }

    private void requestToPinWidget(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager appWidgetManager = getSystemService(AppWidgetManager.class);
            ComponentName myProvider = new ComponentName(this, BatteryWidget.class);
            assert appWidgetManager != null;
            if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                Intent pinnedWidgetCallbackIntent = new Intent(this, MainActivity.class);
                PendingIntent successCallback = PendingIntent.getBroadcast(this, 0,
                        pinnedWidgetCallbackIntent, PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                appWidgetManager.requestPinAppWidget(myProvider, null, successCallback);
            }
        } else {
            // not supported
//            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
//
//            startActivity(intent);
        }
    }
}