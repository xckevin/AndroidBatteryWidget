package com.github.xckevin927.android.battery.widget.activity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo;
import com.github.xckevin927.android.battery.widget.utils.BatteryStatusText;

import com.github.xckevin927.android.battery.widget.Constants;
import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService;
import com.github.xckevin927.android.battery.widget.utils.NotificationUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        setTitle(R.string.ui_settings);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private SwitchPreferenceCompat monitoringSwitch;
        private Preference statusPreference;
        private final Runnable statusListener = this::refreshStatus;
        private boolean enableAfterSettings;
        private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted && NotificationUtil.isNotificationEnabled(requireContext())) {
                        setMonitoringEnabled(true);
                    } else {
                        onNotificationDisabled();
                    }
                });

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            enableAfterSettings = savedInstanceState != null
                    && savedInstanceState.getBoolean("enableAfterSettings");
            monitoringSwitch = findPreference(Constants.SettingsKey.KEY_SHOW_IN_STATUS_BAR);
            monitoringSwitch.setTitle(R.string.continuous_monitoring_title);
            monitoringSwitch.setIcon(R.drawable.ic_ui_monitoring);
            PreferenceCategory monitoringGroup = findPreference("settings_ui_monitoring_group");
            PreferenceCategory actionsGroup = findPreference("settings_ui_actions_group");
            statusPreference = new Preference(requireContext());
            statusPreference.setTitle(R.string.monitor_status_label);
            statusPreference.setIcon(R.drawable.ic_ui_status);
            statusPreference.setOnPreferenceClickListener(preference -> {
                if (!NotificationUtil.isNotificationEnabled(requireContext())) onNotificationDisabled();
                else setMonitoringEnabled(true);
                return true;
            });
            monitoringGroup.addPreference(statusPreference);
            Preference refresh = new Preference(requireContext());
            refresh.setTitle(R.string.monitor_refresh);
            refresh.setSummary(R.string.settings_ui_settings_refresh_summary);
            refresh.setIcon(R.drawable.ic_ui_monitoring);
            refresh.setOnPreferenceClickListener(preference -> {
                WidgetUpdateService.refreshWidgets(requireContext(), "settings_manual");
                return true;
            });
            monitoringGroup.addPreference(refresh);
            Preference alerts = new Preference(requireContext());
            alerts.setTitle(R.string.monitor_alerts);
            alerts.setSummary(R.string.settings_ui_settings_alerts_summary);
            alerts.setIcon(R.drawable.ic_ui_alert);
            alerts.setIntent(new android.content.Intent(requireContext(), AlertsActivity.class));
            actionsGroup.addPreference(alerts);
            Preference pro = new Preference(requireContext());
            pro.setTitle(R.string.pro_title);
            pro.setSummary(R.string.settings_ui_settings_pro_summary);
            pro.setIcon(R.drawable.ic_ui_pro);
            pro.setIntent(new android.content.Intent(requireContext(), ProActivity.class));
            actionsGroup.addPreference(pro);
            monitoringSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                if (!Boolean.TRUE.equals(newValue)) {
                    enableAfterSettings = false;
                    setMonitoringEnabled(false);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
                } else if (!NotificationUtil.isNotificationEnabled(requireContext())) {
                    onNotificationDisabled();
                } else {
                    setMonitoringEnabled(true);
                }
                // Persist explicitly before the service reads the new preference.
                return false;
            });
        }

        private void setMonitoringEnabled(boolean enabled) {
            monitoringSwitch.setChecked(enabled);
            WidgetUpdateService.syncMonitoring(requireContext());
        }

        @Override
        public void onResume() {
            super.onResume();
            if (enableAfterSettings) {
                enableAfterSettings = false;
                if (NotificationUtil.isNotificationEnabled(requireContext())) {
                    setMonitoringEnabled(true);
                }
            }
            monitoringSwitch.setChecked(WidgetUpdateService.isMonitoringEnabled(requireContext()));
            BatteryRepo.addListener(statusListener);
            refreshStatus();
        }

        private void refreshStatus() {
            if (!isAdded() || statusPreference == null) return;
            String current = getString(WidgetUpdateService.getMonitoringStatusText(requireContext()));
            statusPreference.setSummary(current + "\n" + getString(R.string.monitor_last_check,
                    BatteryStatusText.age(requireContext(), BatteryRepo.INSTANCE.getLastPhoneReadAt())));
            monitoringSwitch.setSummary(R.string.continuous_monitoring_summary);
        }

        @Override public void onPause() {
            BatteryRepo.removeListener(statusListener);
            super.onPause();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBoolean("enableAfterSettings", enableAfterSettings);
        }

        private void onNotificationDisabled() {
            Context context = getContext();
            if (context == null) {
                return;
            }
            new MaterialAlertDialogBuilder(context)
                    .setTitle(android.R.string.dialog_alert_title)
                    .setMessage(R.string.notification_enable_notice)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        enableAfterSettings = true;
                        NotificationUtil.openNotificationSetting(context);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }


    }
}
