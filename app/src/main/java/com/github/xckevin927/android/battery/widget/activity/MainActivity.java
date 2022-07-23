package com.github.xckevin927.android.battery.widget.activity;

import android.Manifest;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;
import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;
import com.github.xckevin927.android.battery.widget.receiver.BatteryWidget;
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService;
import com.github.xckevin927.android.battery.widget.utils.AFunc1;
import com.github.xckevin927.android.battery.widget.utils.BatteryUtil;
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper;
import com.github.xckevin927.android.battery.widget.utils.Utils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class MainActivity extends AppCompatActivity {

    private final Handler handler = new Handler();
    private ActivityResultLauncher<String> wallpaperPermissionLauncher;

    private BatteryWidgetPref widgetPref;

    private ImageView widgetPreviewImage;

    private MaterialCheckBox wallpaperCheckBox;
    private SwitchMaterial bgSwitch;
    private ImageView bgColorIndicatorView;
    private ImageView darkBgColorIndicatorView;
    private RangeSlider roundSlider;
    private SwitchMaterial bgProgressSwitch;
    private RangeSlider lineSlider;

    private final Runnable renderTask = () -> {
        Bitmap bitmap = Utils.generateBatteryBitmap(this, BatteryUtil.getBatteryState(this), widgetPref);
        widgetPreviewImage.setImageBitmap(bitmap);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        widgetPref = BatteryWidgetPrefHelper.getBatteryWidgetPref(this);

        wallpaperPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
            if (result != null && result) {
                renderWallpaper();
            }
        });

        initViews();
        setUpViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WidgetUpdateService.start(getApplicationContext());
        renderBatteryWidget();
    }

    private void initViews() {
        widgetPreviewImage = findViewById(R.id.appwidget_progress);

        wallpaperCheckBox = findViewById(R.id.id_show_wallpaper_check_activity_main);
        wallpaperCheckBox.setTextColor(Color.BLACK);
        wallpaperCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            widgetPref.setShowWallpaper(isChecked);
            if (isChecked) {
                requestRenderWallpaper();
            } else {
                removeWallpaper();
            }
        });

        // background settings
        bgSwitch = findViewById(R.id.id_show_bg_switch__activity_main);
        bgSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            widgetPref.setShowBackground(isChecked);
            renderBatteryWidget();
        });

        bgColorIndicatorView = findViewById(R.id.id_bg_color_indicator_activity_main);
        bgColorIndicatorView.setOnClickListener(v -> chooseColor(widgetPref.getBackgroundColor(), c -> {
            widgetPref.setBackgroundColor(c);
            bgColorIndicatorView.setImageDrawable(new ColorDrawable(widgetPref.getBackgroundColor()));
            renderBatteryWidget();
        }));

        darkBgColorIndicatorView = findViewById(R.id.id_bg_color_in_dark_indicator_activity_main);
        darkBgColorIndicatorView.setOnClickListener(v -> chooseColor(widgetPref.getBackgroundColorInDarkMode(), c -> {
            widgetPref.setBackgroundColorInDarkMode(c);
            darkBgColorIndicatorView.setImageDrawable(new ColorDrawable(widgetPref.getBackgroundColorInDarkMode()));
            renderBatteryWidget();
        }));

        roundSlider = findViewById(R.id.id_round_slide_activity_main);
        roundSlider.addOnChangeListener((slider, value, fromUser) -> {
            widgetPref.setRound((int) value);
            renderBatteryWidget();
        });

        // progress settings
        bgProgressSwitch = findViewById(R.id.id_show_bg_progress_switch_activity_main);
        bgProgressSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            widgetPref.setShowBackgroundProgress(isChecked);
            renderBatteryWidget();
        });

        lineSlider = findViewById(R.id.id_stroke_slide_activity_main);
        lineSlider.addOnChangeListener((slider, value, fromUser) -> {
            widgetPref.setLineWidth((int) value);
            renderBatteryWidget();
        });

        MaterialButton saveBtn = findViewById(R.id.save_pref);
        saveBtn.setOnClickListener(v -> BatteryWidgetPrefHelper.saveBatteryWidgetPref(this, widgetPref));

        MaterialButton addBtn = findViewById(R.id.add_widget);
        addBtn.setVisibility(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? View.VISIBLE : View.INVISIBLE);
        addBtn.setOnClickListener(v -> {
            BatteryWidgetPrefHelper.saveBatteryWidgetPref(MainActivity.this, widgetPref);
            requestToPinWidget();
        });
    }

    private void setUpViews() {
        wallpaperCheckBox.setChecked(widgetPref.isShowWallpaper());
        bgSwitch.setChecked(widgetPref.isShowBackground());
        bgColorIndicatorView.setImageDrawable(new ColorDrawable(widgetPref.getBackgroundColor()));
        darkBgColorIndicatorView.setImageDrawable(new ColorDrawable(widgetPref.getBackgroundColorInDarkMode()));
        roundSlider.setValues((float) widgetPref.getRound());
        bgProgressSwitch.setChecked(widgetPref.isShowBackgroundProgress());
        lineSlider.setValues((float) widgetPref.getLineWidth());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.id_restore_default) {
            widgetPref = new BatteryWidgetPref();
            setUpViews();
            renderBatteryWidget();
        }
        return super.onOptionsItemSelected(item);
    }

    private void requestRenderWallpaper() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
            } else {
                wallpaperPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        } else {
            renderWallpaper();
        }
    }

    private void renderWallpaper() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            WallpaperManager wallpaperManager = (WallpaperManager) getSystemService(WALLPAPER_SERVICE);
            Drawable drawable = wallpaperManager.getFastDrawable();
            findViewById(R.id.battery_container_wrapper).setBackground(drawable);
        }
    }

    private void removeWallpaper() {
        findViewById(R.id.battery_container_wrapper).setBackground(null);
    }

    private void renderBatteryWidget() {
        handler.removeCallbacks(renderTask);
        handler.post(renderTask);
    }

    private void requestToPinWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager appWidgetManager = getSystemService(AppWidgetManager.class);
            ComponentName myProvider = new ComponentName(this, BatteryWidget.class);
            assert appWidgetManager != null;
            if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                Intent pinnedWidgetCallbackIntent = new Intent(this, MainActivity.class);
                PendingIntent successCallback = PendingIntent.getBroadcast(this, 0,
                        pinnedWidgetCallbackIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                appWidgetManager.requestPinAppWidget(myProvider, null, successCallback);
            }
        } else {
            // not supported
//            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
//
//            startActivity(intent);
        }
    }

    private void chooseColor(@ColorInt int initColor, @NonNull AFunc1<Integer> chooseCallback) {
        ColorPickerDialogBuilder
                .with(this)
                .setTitle(getString(R.string.choose_color))
                .initialColor(initColor)
                .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                .density(12)
                .setOnColorSelectedListener(selectedColor -> {
//                        toast("onColorSelected: 0x" + Integer.toHexString(selectedColor));
                })
                .setPositiveButton(android.R.string.ok, (dialog, selectedColor, allColors) -> {
                    chooseCallback.call(selectedColor);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                })
                .build()
                .show();
    }
}