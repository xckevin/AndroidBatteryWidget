package com.github.xckevin927.android.battery.widget.activity.fragment.phone;

import android.Manifest;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;
import com.github.xckevin927.android.battery.widget.App;
import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.activity.MainActivity;
import com.github.xckevin927.android.battery.widget.activity.SettingsActivity;
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;
import com.github.xckevin927.android.battery.widget.receiver.BatteryWidget;
import com.github.xckevin927.android.battery.widget.utils.AFunc1;
import com.github.xckevin927.android.battery.widget.utils.BatteryUtil;
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper;
import com.github.xckevin927.android.battery.widget.utils.ShareUtil;
import com.github.xckevin927.android.battery.widget.utils.Utils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class BatteryWidgetConfigFragment extends Fragment {

    private final Handler handler = new Handler();
    private ActivityResultLauncher<String> wallpaperPermissionLauncher;

    private BatteryWidgetPref widgetPref;

    private ImageView widgetPreviewImage;

    private MaterialCheckBox wallpaperCheckBox;
    private SwitchMaterial bgSwitch;
    private TextView bgColorTitleTv;
    private ImageView bgColorIndicatorView;
    private TextView dardBgColorTitleTv;
    private ImageView darkBgColorIndicatorView;
    private RangeSlider roundSlider;
    private SwitchMaterial bgProgressSwitch;
    private RangeSlider lineSlider;

    private View wallpaperBgView;

    private final BroadcastReceiver batteryChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            renderBatteryWidget();
        }
    };

    private final Runnable renderTask = () -> {
        Bitmap bitmap = Utils.generateBatteryBitmap(App.getAppContext(), BatteryUtil.getBatteryState(App.getAppContext()), widgetPref);
        widgetPreviewImage.setImageBitmap(bitmap);
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        widgetPref = BatteryWidgetPrefHelper.getBatteryWidgetPref(App.getAppContext());

        wallpaperPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
            if (result != null && result) {
                renderWallpaper();
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_battery_widget_config, container, false);

        initViews(view);
        setUpViews();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        renderBatteryWidget();
        App.getAppContext().registerReceiver(batteryChangedReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    public void onPause() {
        super.onPause();
        App.getAppContext().unregisterReceiver(batteryChangedReceiver);
    }

    private void initViews(View view) {
        widgetPreviewImage = view.findViewById(R.id.appwidget_progress);

        wallpaperBgView = view.findViewById(R.id.battery_container_wrapper);
        wallpaperCheckBox = view.findViewById(R.id.id_show_wallpaper_check_activity_main);
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
        bgSwitch = view.findViewById(R.id.id_show_bg_switch__activity_main);
        bgSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            widgetPref.setShowBackground(isChecked);
            renderBatteryWidget();
        });

        bgColorTitleTv = view.findViewById(R.id.id_bg_color_title_activity_main);
        bgColorIndicatorView = view.findViewById(R.id.id_bg_color_indicator_activity_main);
        bgColorIndicatorView.setOnClickListener(v -> chooseColor(widgetPref.getBackgroundColor(), c -> {
            widgetPref.setBackgroundColor(c);
            renderBgColor();
            renderBatteryWidget();
        }));

        dardBgColorTitleTv = view.findViewById(R.id.id_dark_bg_color_title_activity_main);
        darkBgColorIndicatorView = view.findViewById(R.id.id_bg_color_in_dark_indicator_activity_main);
        darkBgColorIndicatorView.setOnClickListener(v -> chooseColor(widgetPref.getBackgroundColorInDarkMode(), c -> {
            widgetPref.setBackgroundColorInDarkMode(c);
            renderDarkBg();
            renderBatteryWidget();
        }));

        TextView roundTitle = view.findViewById(R.id.id_round_title_activity_main);
        roundSlider = view.findViewById(R.id.id_round_slide_activity_main);
        roundSlider.addOnChangeListener((slider, value, fromUser) -> {
            roundTitle.setText(getString(R.string.round, (int)value));
            widgetPref.setRound((int) value);
            renderBatteryWidget();
        });

        // progress settings
        bgProgressSwitch = view.findViewById(R.id.id_show_bg_progress_switch_activity_main);
        bgProgressSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            widgetPref.setShowBackgroundProgress(isChecked);
            renderBatteryWidget();
        });

        TextView lineWidthTitle = view.findViewById(R.id.id_stroke_title_activity_main);
        lineSlider = view.findViewById(R.id.id_stroke_slide_activity_main);
        lineSlider.addOnChangeListener((slider, value, fromUser) -> {
            lineWidthTitle.setText(getString(R.string.line_width, (int)value));
            widgetPref.setLineWidth((int) value);
            renderBatteryWidget();
        });

        MaterialButton saveBtn = view.findViewById(R.id.save_pref);
        saveBtn.setOnClickListener(v -> BatteryWidgetPrefHelper.saveBatteryWidgetPref(App.getAppContext(), widgetPref));

        MaterialButton addBtn = view.findViewById(R.id.add_widget);
        addBtn.setVisibility(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? View.VISIBLE : View.INVISIBLE);
        addBtn.setOnClickListener(v -> {
            BatteryWidgetPrefHelper.saveBatteryWidgetPref(App.getAppContext(), widgetPref);
            requestToPinWidget();
        });
    }

    private void setUpViews() {
        wallpaperCheckBox.setChecked(widgetPref.isShowWallpaper());

        bgSwitch.setChecked(widgetPref.isShowBackground());
        renderBgColor();
        renderDarkBg();
        roundSlider.setValues((float) widgetPref.getRound());

        bgProgressSwitch.setChecked(widgetPref.isShowBackgroundProgress());
        lineSlider.setValues((float) widgetPref.getLineWidth());
    }

    private void renderBgColor() {
        bgColorTitleTv.setText(getString(R.string.background_color, "0x" + Integer.toHexString(widgetPref.getBackgroundColor())));
        bgColorIndicatorView.setImageDrawable(new ColorDrawable(widgetPref.getBackgroundColor()));
    }

    private void renderDarkBg() {
        dardBgColorTitleTv.setText(getString(R.string.background_color_dark_model, "0x" + Integer.toHexString(widgetPref.getBackgroundColorInDarkMode())));
        darkBgColorIndicatorView.setImageDrawable(new ColorDrawable(widgetPref.getBackgroundColorInDarkMode()));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.id_restore_default) {
            widgetPref = new BatteryWidgetPref();
            setUpViews();
            renderBatteryWidget();
            return true;
        } else if (item.getItemId() == R.id.id_share) {
            ShareUtil.share(getActivity());
            return true;
        } else if (item.getItemId() == R.id.id_advance) {
            startActivity(new Intent(getActivity(), SettingsActivity.class));
            return true;
        } else if (item.getItemId() == R.id.id_feedback) {
            ShareUtil.feedback(getActivity());
        }
        return super.onOptionsItemSelected(item);
    }

    private void requestRenderWallpaper() {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", App.getAppContext().getPackageName(), null));
                startActivity(intent);
            } else {
                wallpaperPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        } else {
            renderWallpaper();
        }
    }

    private void renderWallpaper() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            WallpaperManager wallpaperManager = (WallpaperManager) App.getAppContext().getSystemService(Context.WALLPAPER_SERVICE);
            Drawable drawable = wallpaperManager.getFastDrawable();
            wallpaperBgView.setBackground(drawable);
        }
    }

    private void removeWallpaper() {
        wallpaperBgView.setBackground(null);
    }

    private void renderBatteryWidget() {
        handler.removeCallbacks(renderTask);
        handler.post(renderTask);
    }

    private void requestToPinWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager appWidgetManager = App.getAppContext().getSystemService(AppWidgetManager.class);
            ComponentName myProvider = new ComponentName(getActivity(), BatteryWidget.class);
            assert appWidgetManager != null;
            if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                Intent pinnedWidgetCallbackIntent = new Intent(getActivity(), MainActivity.class);
                PendingIntent successCallback = PendingIntent.getBroadcast(getActivity(), 0,
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
                .with(getActivity())
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
