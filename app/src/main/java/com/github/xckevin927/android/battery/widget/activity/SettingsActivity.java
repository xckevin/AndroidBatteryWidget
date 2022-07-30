package com.github.xckevin927.android.battery.widget.activity;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceFragmentCompat;

import com.github.xckevin927.android.battery.widget.App;
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
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            findPreference(Constants.SettingsKey.KEY_SHOW_IN_STATUS_BAR).setOnPreferenceChangeListener((preference, newValue) -> {
                if (!NotificationUtil.isNotificationEnabled(getContext())) {
                    onNotificationDisabled();
                    return false;
                }
                WidgetUpdateService.start(App.getAppContext());
                return true;
            });
        }

        private void onNotificationDisabled() {
            Context context = getContext();
            if (context == null) {
                return;
            }
            new MaterialAlertDialogBuilder(context)
                    .setTitle(android.R.string.dialog_alert_title)
                    .setMessage(R.string.notification_enable_notice)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> NotificationUtil.openNotificationSetting(context))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }


    }
}