package com.github.xckevin927.android.battery.widget.activity;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.OnColorSelectedListener;
import com.flask.colorpicker.builder.ColorPickerClickListener;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;
import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;
import com.github.xckevin927.android.battery.widget.receiver.BatteryWidget;
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService;
import com.github.xckevin927.android.battery.widget.utils.BatteryUtil;
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper;
import com.github.xckevin927.android.battery.widget.utils.Utils;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> wallpaperPermissionLauncher;

    private BatteryWidgetPref widgetPref;

    private ImageView bgColorIndicatorView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        widgetPref = BatteryWidgetPrefHelper.getBatteryWidgetPref(this);

        wallpaperPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
            @Override
            public void onActivityResult(Boolean result) {
                if (result != null && result) {
                    renderWallpaper();
                }
            }
        });

        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WidgetUpdateService.start(getApplicationContext());
        renderBatteryWidget();
    }

    private void initViews() {
        renderWallpaper();
        setUpBatteryContent();

        MaterialCheckBox wallpaperCheckBox = findViewById(R.id.id_show_wallpaper_check_activity_main);
        wallpaperCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            widgetPref.setShowWallpaper(isChecked);
            if (isChecked) {
                requestRenderWallpaper();
            } else {
                removeWallpaper();
            }
        });
        wallpaperCheckBox.setChecked(widgetPref.isShowWallpaper());

        // background settings
        SwitchMaterial bgSwitch = findViewById(R.id.id_show_bg_switch__activity_main);
        bgSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            widgetPref.setShowBackground(isChecked);
            renderBatteryWidget();
        });
        bgSwitch.setChecked(widgetPref.isShowBackground());

        bgColorIndicatorView = findViewById(R.id.id_bg_color_indicator_activity_main);
        bgColorIndicatorView.setOnClickListener(v -> chooseColor());
        bgColorIndicatorView.setBackgroundColor(widgetPref.getBackgroundColor());

        RangeSlider roundSlider = findViewById(R.id.id_round_slide__activity_main);
        roundSlider.addOnChangeListener((slider, value, fromUser) -> {
            widgetPref.setRound((int) value);
            renderBatteryWidget();
        });
        roundSlider.setValues((float)widgetPref.getRound());

        // progress settings
        SwitchMaterial bgProgressSwitch = findViewById(R.id.id_show_bg_progress_switch__activity_main);
        bgProgressSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            widgetPref.setShowBackgroundProgress(isChecked);
            renderBatteryWidget();
        });
        bgProgressSwitch.setChecked(widgetPref.isShowBackgroundProgress());

        RangeSlider lineSlider = findViewById(R.id.id_stroke_slide_activity_main);
        lineSlider.addOnChangeListener((slider, value, fromUser) -> {
            widgetPref.setLineWidth((int) value);
            renderBatteryWidget();
        });
        lineSlider.setValues((float) widgetPref.getLineWidth());


        findViewById(R.id.add_widget).setOnClickListener(v -> requestToPinWidget());

    }

    private void setUpBatteryContent() {
//        Size size =  Utils.getScreenWidth(this);
//        int width = Math.min(size.getWidth(), size.getHeight()) / 2;
//        View view = findViewById(R.id.battery_container);
//        ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(width, width);
//        layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
//        layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
//        layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
//        view.setLayoutParams(layoutParams);
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
        Bitmap bitmap = Utils.generateBatteryBitmap(this, BatteryUtil.getBatteryState(this), widgetPref);
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

    private void saveData() {
    }

    private void chooseColor() {
        ColorPickerDialogBuilder
                .with(this)
                .setTitle("Choose color")
                .initialColor(widgetPref.getBackgroundColor())
                .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                .density(12)
                .setOnColorSelectedListener(new OnColorSelectedListener() {
                    @Override
                    public void onColorSelected(int selectedColor) {
//                        toast("onColorSelected: 0x" + Integer.toHexString(selectedColor));
                    }
                })
                .setPositiveButton("OK", new ColorPickerClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int selectedColor, Integer[] allColors) {
                        bgColorIndicatorView.setBackgroundColor(selectedColor);
                        widgetPref.setBackgroundColor(selectedColor);
                        renderBatteryWidget();
                    }
                })
                .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .build()
                .show();
    }
}