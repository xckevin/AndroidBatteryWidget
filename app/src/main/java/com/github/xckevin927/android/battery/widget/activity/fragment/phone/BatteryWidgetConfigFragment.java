package com.github.xckevin927.android.battery.widget.activity.fragment.phone;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;
import com.github.xckevin927.android.battery.widget.App;
import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.activity.MainActivity;
import com.github.xckevin927.android.battery.widget.activity.ProActivity;
import com.github.xckevin927.android.battery.widget.appwidget.BatteryWidget;
import com.github.xckevin927.android.battery.widget.appwidget.WidgetLayoutPolicy;
import com.github.xckevin927.android.battery.widget.billing.ProBilling;
import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;
import com.github.xckevin927.android.battery.widget.model.BtDeviceState;
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo;
import com.github.xckevin927.android.battery.widget.utils.AFunc1;
import com.github.xckevin927.android.battery.widget.utils.BatteryWidgetPrefHelper;
import com.github.xckevin927.android.battery.widget.utils.DevicePreferences;
import com.github.xckevin927.android.battery.widget.utils.Utils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Edits a draft. Persistence occurs only when the user saves. */
public class BatteryWidgetConfigFragment extends Fragment {
    private static final String ARG_WIDGET_ID = "widget_id";
    private static final String STATE_DRAFT = "widget_pref_draft";
    private final Handler handler = new Handler();
    private ActivityResultLauncher<String> wallpaperPermissionLauncher;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private BatteryWidgetPref widgetPref;
    private ImageView widgetPreviewImage, wallpaperBgView, bgColorIndicatorView, darkBgColorIndicatorView;
    private View previewPhoneArea, previewDevicesGrid, previewDevicesContainer;
    private TextView previewDevicesText, styleTitle;
    private MaterialCheckBox wallpaperCheckBox;
    private SwitchMaterial bgSwitch, bgProgressSwitch, allVisibleDevicesSwitch;
    private TextView bgColorTitleTv, darkBgColorTitleTv;
    private RangeSlider roundSlider, lineSlider;
    private RadioGroup styleGroup, clickGroup;
    private LinearLayout devicesContainer;
    private boolean repoListenerRegistered;

    private final android.content.BroadcastReceiver batteryChangedReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { renderBatteryWidget(); }
    };
    private final Runnable renderTask = () -> {
        if (widgetPreviewImage == null || widgetPref == null) return;
        Bitmap bitmap = Utils.generateBatteryBitmap(App.getAppContext(), BatteryRepo.INSTANCE.getBatteryState(), widgetPref);
        widgetPreviewImage.setImageBitmap(bitmap);
    };
    private final Runnable refreshDeviceChoices = () -> {
        if (isAdded() && devicesContainer != null) populateDeviceChoices();
    };
    private final Runnable repoListener = () -> handler.post(refreshDeviceChoices);

    public static BatteryWidgetConfigFragment newInstance() {
        return newInstance(AppWidgetManager.INVALID_APPWIDGET_ID);
    }
    public static BatteryWidgetConfigFragment newInstance(int widgetId) {
        BatteryWidgetConfigFragment fragment = new BatteryWidgetConfigFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_WIDGET_ID, widgetId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) appWidgetId = getArguments().getInt(ARG_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        BatteryWidgetPref restored = savedInstanceState == null ? null
                : (BatteryWidgetPref) savedInstanceState.getSerializable(STATE_DRAFT);
        widgetPref = restored == null
                ? BatteryWidgetPrefHelper.getBatteryWidgetPref(App.getAppContext(), appWidgetId).copy()
                : restored.copy();
        wallpaperPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (Boolean.TRUE.equals(granted)) renderWallpaper();
        });
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                                  @Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_battery_widget_config, container, false);
        initViews(view);
        setUpViews();
        return view;
    }
    @Override public void onResume() {
        super.onResume();
        renderBatteryWidget();
        App.getAppContext().registerReceiver(batteryChangedReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (!repoListenerRegistered) {
            BatteryRepo.addListener(repoListener);
            repoListenerRegistered = true;
        }
        populateDeviceChoices(); // snapshot-only; does not schedule a Bluetooth refresh.
    }
    @Override public void onPause() {
        super.onPause();
        try { App.getAppContext().unregisterReceiver(batteryChangedReceiver); } catch (IllegalArgumentException ignored) { }
        if (repoListenerRegistered) {
            BatteryRepo.removeListener(repoListener);
            repoListenerRegistered = false;
        }
    }
    @Override public void onDestroyView() {
        handler.removeCallbacks(renderTask);
        handler.removeCallbacks(refreshDeviceChoices);
        super.onDestroyView();
    }
    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_DRAFT, widgetPref == null ? null : widgetPref.copy());
    }

    private void initViews(View view) {
        widgetPreviewImage = view.findViewById(R.id.appwidget_progress);
        previewPhoneArea = view.findViewById(R.id.phone_area);
        previewDevicesGrid = view.findViewById(R.id.widget_devices);
        previewDevicesContainer = view.findViewById(R.id.widget_devices_container_widget);
        previewDevicesText = view.findViewById(R.id.widget_preview_devices);
        wallpaperBgView = view.findViewById(R.id.battery_container_wrapper);
        wallpaperCheckBox = view.findViewById(R.id.id_show_wallpaper_check_activity_main);
        wallpaperCheckBox.setTextColor(ContextCompat.getColor(requireContext(), R.color.ui_text));
        wallpaperCheckBox.setOnCheckedChangeListener((v, checked) -> {
            widgetPref.setShowWallpaper(checked);
            if (checked) requestRenderWallpaper(); else removeWallpaper();
        });
        bgSwitch = view.findViewById(R.id.id_show_bg_switch__activity_main);
        bgSwitch.setOnCheckedChangeListener((v, checked) -> { widgetPref.setShowBackground(checked); renderBatteryWidget(); });
        bgColorTitleTv = view.findViewById(R.id.id_bg_color_title_activity_main);
        bgColorIndicatorView = view.findViewById(R.id.id_bg_color_indicator_activity_main);
        bgColorIndicatorView.setOnClickListener(v -> chooseColor(widgetPref.getBackgroundColor(), color -> {
            widgetPref.setBackgroundColor(color); renderBgColor(); renderBatteryWidget();
        }));
        darkBgColorTitleTv = view.findViewById(R.id.id_dark_bg_color_title_activity_main);
        darkBgColorIndicatorView = view.findViewById(R.id.id_bg_color_in_dark_indicator_activity_main);
        darkBgColorIndicatorView.setOnClickListener(v -> chooseColor(widgetPref.getBackgroundColorInDarkMode(), color -> {
            widgetPref.setBackgroundColorInDarkMode(color); renderDarkBg(); renderBatteryWidget();
        }));
        TextView roundTitle = view.findViewById(R.id.id_round_title_activity_main);
        roundSlider = view.findViewById(R.id.id_round_slide_activity_main);
        roundSlider.addOnChangeListener((slider, value, user) -> { roundTitle.setText(getString(R.string.round, (int) value)); widgetPref.setRound((int) value); renderBatteryWidget(); });
        bgProgressSwitch = view.findViewById(R.id.id_show_bg_progress_switch_activity_main);
        bgProgressSwitch.setOnCheckedChangeListener((v, checked) -> { widgetPref.setShowBackgroundProgress(checked); renderBatteryWidget(); });
        TextView lineTitle = view.findViewById(R.id.id_stroke_title_activity_main);
        lineSlider = view.findViewById(R.id.id_stroke_slide_activity_main);
        lineSlider.addOnChangeListener((slider, value, user) -> {
            String label = getString(R.string.line_width, (int) value);
            lineTitle.setText(label);
            slider.setContentDescription(label);
            widgetPref.setLineWidth((int) value);
            renderBatteryWidget();
        });

        styleGroup = view.findViewById(R.id.widget_style_group);
        styleTitle = view.findViewById(R.id.widget_style_title);
        clickGroup = view.findViewById(R.id.widget_click_group);
        allVisibleDevicesSwitch = view.findViewById(R.id.widget_all_visible_devices);
        devicesContainer = view.findViewById(R.id.widget_devices_container);
        styleGroup.setOnCheckedChangeListener((group, checked) -> {
            widgetPref.setWidgetStyle(checked == R.id.widget_style_phone ? BatteryWidgetPref.STYLE_PHONE
                    : checked == R.id.widget_style_devices ? BatteryWidgetPref.STYLE_DEVICES
                    : BatteryWidgetPref.STYLE_COMBINED);
            updatePreviewMode();
        });
        clickGroup.setOnCheckedChangeListener((group, checked) -> widgetPref.setClickAction(
                checked == R.id.widget_click_device ? BatteryWidgetPref.CLICK_DEVICE : BatteryWidgetPref.CLICK_STATUS));
        allVisibleDevicesSwitch.setOnCheckedChangeListener((button, checked) -> {
            widgetPref.setShowAllVisibleDevices(checked);
            updatePreviewMode();
        });
        MaterialButton save = view.findViewById(R.id.save_pref);
        save.setOnClickListener(v -> save());
        MaterialButton add = view.findViewById(R.id.add_widget);
        if (isConfigureFlow()) {
            save.setText(R.string.widget_save_and_finish);
            save.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ui_primary)));
            save.setTextColor(ContextCompat.getColor(requireContext(), R.color.ui_on_primary));
            add.setVisibility(View.VISIBLE);
            add.setText(android.R.string.cancel);
            add.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ui_primary_container)));
            add.setTextColor(ContextCompat.getColor(requireContext(), R.color.ui_primary));
            add.setStrokeWidth(0);
            add.setOnClickListener(v -> requireActivity().finish()); // MainActivity keeps RESULT_CANCELED.
        } else {
            save.setText(R.string.widget_save_defaults);
            add.setOnClickListener(v -> {
                if (save()) requestToPinWidget();
            });
        }
        if (isBtWidgetConfiguration()) {
            widgetPref.setWidgetStyle(BatteryWidgetPref.STYLE_DEVICES);
            styleTitle.setVisibility(View.GONE);
            styleGroup.setVisibility(View.GONE);
        }
    }

    private void setUpViews() {
        wallpaperCheckBox.setChecked(widgetPref.isShowWallpaper());
        bgSwitch.setChecked(widgetPref.isShowBackground()); renderBgColor(); renderDarkBg();
        roundSlider.setValues((float) widgetPref.getRound());
        bgProgressSwitch.setChecked(widgetPref.isShowBackgroundProgress());
        lineSlider.setValues((float) widgetPref.getLineWidth());
        lineSlider.setContentDescription(getString(R.string.line_width, widgetPref.getLineWidth()));
        styleGroup.check(BatteryWidgetPref.STYLE_PHONE.equals(widgetPref.getWidgetStyle()) ? R.id.widget_style_phone
                : BatteryWidgetPref.STYLE_DEVICES.equals(widgetPref.getWidgetStyle()) ? R.id.widget_style_devices
                : R.id.widget_style_combined);
        clickGroup.check(BatteryWidgetPref.CLICK_DEVICE.equals(widgetPref.getClickAction()) ? R.id.widget_click_device : R.id.widget_click_status);
        allVisibleDevicesSwitch.setChecked(widgetPref.isShowAllVisibleDevices());
        populateDeviceChoices();
        updatePreviewMode();
    }

    private void populateDeviceChoices() {
        devicesContainer.removeAllViews();
        List<BtDeviceState> devices = DevicePreferences.visibleDevices(requireContext(), BatteryRepo.INSTANCE.getBtSnapshot());
        if (devices.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.widget_no_devices_available);
            devicesContainer.addView(empty);
            updatePreviewMode();
            return;
        }
        Set<String> selected = new LinkedHashSet<>(widgetPref.getSelectedDeviceAddresses());
        for (BtDeviceState state : devices) {
            String address = addressOf(state);
            if (address == null) continue;
            String name = DevicePreferences.displayName(requireContext(), state);
            MaterialCheckBox choice = new MaterialCheckBox(requireContext());
            choice.setText(name);
            choice.setChecked(selected.contains(address));
            choice.setContentDescription(getString(R.string.widget_device_choice_description, name));
            choice.setOnCheckedChangeListener((button, checked) -> {
                Set<String> result = new LinkedHashSet<>(widgetPref.getSelectedDeviceAddresses());
                if (checked) result.add(address); else result.remove(address);
                widgetPref.setSelectedDeviceAddresses(new ArrayList<>(result));
                updatePreviewMode();
            });
            devicesContainer.addView(choice);
        }
        updatePreviewMode();
    }
    private String addressOf(BtDeviceState state) {
        try { return state.getBluetoothDevice().getAddress(); } catch (SecurityException ignored) { return null; }
    }
    private boolean isConfigureFlow() { return appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID; }
    private boolean isBtWidgetConfiguration() {
        return getActivity() instanceof MainActivity && ((MainActivity) getActivity()).isBtWidgetConfiguration();
    }
    private void updatePreviewMode() {
        if (previewPhoneArea == null || previewDevicesText == null || previewDevicesGrid == null || previewDevicesContainer == null) return;
        String style = isBtWidgetConfiguration() ? BatteryWidgetPref.STYLE_DEVICES : widgetPref.getWidgetStyle();
        boolean showPhone = !BatteryWidgetPref.STYLE_DEVICES.equals(style);
        boolean showDevices = !BatteryWidgetPref.STYLE_PHONE.equals(style);
        previewPhoneArea.setVisibility(showPhone ? View.VISIBLE : View.GONE);
        previewDevicesContainer.setVisibility(showDevices ? View.VISIBLE : View.GONE);
        // Activity previews do not bind RemoteViews collections. Show an equivalent native summary.
        previewDevicesGrid.setVisibility(View.GONE);
        previewDevicesText.setVisibility(showDevices ? View.VISIBLE : View.GONE);
        if (showDevices) previewDevicesText.setText(previewDeviceSummary());
    }
    private String previewDeviceSummary() {
        List<BtDeviceState> devices = DevicePreferences.visibleDevices(requireContext(), BatteryRepo.INSTANCE.getBtSnapshot());
        Set<String> selected = new LinkedHashSet<>(widgetPref.getSelectedDeviceAddresses());
        List<String> names = new ArrayList<>();
        for (BtDeviceState state : devices) {
            String address = addressOf(state);
            if (widgetPref.isShowAllVisibleDevices()
                    || (address != null && selected.contains(address))) {
                names.add(DevicePreferences.displayName(requireContext(), state));
            }
            if (names.size() == 3) break;
        }
        return names.isEmpty() ? getString(R.string.widget_no_devices) : android.text.TextUtils.join("\n", names);
    }
    private boolean save() {
        Context context = App.getAppContext();
        if (WidgetLayoutPolicy.requiresPro(widgetPref.getWidgetStyle())
                && !ProBilling.hasPro(context)) {
            Toast.makeText(requireContext(), R.string.widget_pro_required, Toast.LENGTH_LONG).show();
            startActivity(new Intent(requireContext(), ProActivity.class));
            return false;
        }
        if (isConfigureFlow()) {
            BatteryWidgetPrefHelper.saveBatteryWidgetPref(context, appWidgetId, widgetPref);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).updateConfiguredWidget();
                ((MainActivity) getActivity()).completeWidgetConfiguration();
            } else {
                BatteryWidget.updateWidget(context, appWidgetId);
            }
        } else {
            BatteryWidgetPrefHelper.saveBatteryWidgetPref(context, widgetPref);
            Toast.makeText(requireContext(), R.string.widget_saved, Toast.LENGTH_SHORT).show();
        }
        return true;
    }
    private void renderBgColor() {
        String label = getString(R.string.background_color, "0x" + Integer.toHexString(widgetPref.getBackgroundColor()));
        bgColorTitleTv.setText(label);
        bgColorIndicatorView.setContentDescription(label);
        bgColorIndicatorView.setImageDrawable(new ColorDrawable(widgetPref.getBackgroundColor()));
    }
    private void renderDarkBg() {
        String label = getString(R.string.background_color_dark_model, "0x" + Integer.toHexString(widgetPref.getBackgroundColorInDarkMode()));
        darkBgColorTitleTv.setText(label);
        darkBgColorIndicatorView.setContentDescription(label);
        darkBgColorIndicatorView.setImageDrawable(new ColorDrawable(widgetPref.getBackgroundColorInDarkMode()));
    }
    @Override public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) { inflater.inflate(R.menu.menu_main, menu); super.onCreateOptionsMenu(menu, inflater); }
    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.id_restore_default) { widgetPref = new BatteryWidgetPref(); setUpViews(); renderBatteryWidget(); return true; }
        return super.onOptionsItemSelected(item);
    }
    private void requestRenderWallpaper() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { removeWallpaper(); return; }
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", App.getAppContext().getPackageName(), null)); startActivity(intent);
            } else wallpaperPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else renderWallpaper();
    }
    @SuppressLint("MissingPermission") private void renderWallpaper() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return;
        try { WallpaperManager manager = (WallpaperManager) App.getAppContext().getSystemService(Context.WALLPAPER_SERVICE); Drawable drawable = manager.getFastDrawable(); wallpaperBgView.setImageDrawable(drawable); } catch (SecurityException ignored) { removeWallpaper(); }
    }
    private void removeWallpaper() { if (wallpaperBgView != null) wallpaperBgView.setImageDrawable(null); }
    private void renderBatteryWidget() { handler.removeCallbacks(renderTask); handler.post(renderTask); }
    private void requestToPinWidget() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { Toast.makeText(requireContext(), R.string.widget_pin_not_supported, Toast.LENGTH_LONG).show(); return; }
        AppWidgetManager manager = requireContext().getSystemService(AppWidgetManager.class);
        if (manager == null || !manager.isRequestPinAppWidgetSupported()) { Toast.makeText(requireContext(), R.string.widget_pin_not_supported, Toast.LENGTH_LONG).show(); return; }
        manager.requestPinAppWidget(new ComponentName(requireContext(), BatteryWidget.class), null, null);
    }
    private void chooseColor(@ColorInt int initial, @NonNull AFunc1<Integer> callback) {
        ColorPickerDialogBuilder.with(requireContext()).setTitle(getString(R.string.choose_color)).initialColor(initial)
                .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER).density(12)
                .setPositiveButton(android.R.string.ok, (dialog, color, colors) -> callback.call(color))
                .setNegativeButton(android.R.string.cancel, null).build().show();
    }
}
