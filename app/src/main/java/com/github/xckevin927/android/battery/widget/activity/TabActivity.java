package com.github.xckevin927.android.battery.widget.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.activity.fragment.BtDeviceFragment;
import com.github.xckevin927.android.battery.widget.activity.fragment.MonitorFragment;
import com.github.xckevin927.android.battery.widget.activity.fragment.phone.BatteryWidgetConfigFragment;
import com.github.xckevin927.android.battery.widget.appwidget.WidgetConstants;
import com.github.xckevin927.android.battery.widget.databinding.ActivityTabBinding;
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo;
import com.github.xckevin927.android.battery.widget.utils.ShareUtil;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;


public class TabActivity extends BaseActivity {

    private ActivityTabBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityTabBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        boolean landscape = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        binding.toolbar.setTitle(landscape ? "" : getString(R.string.app_name));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(!landscape);
        }

        List<TabModel> tabModelList = new ArrayList<>(4);
        tabModelList.add(new TabModel(new MonitorFragment(), getString(R.string.tab_monitor)));
        tabModelList.add(new TabModel(BatteryWidgetConfigFragment.newInstance(), getString(R.string.tab_widget)));
        tabModelList.add(new TabModel(BtDeviceFragment.newInstance(getResources().getConfiguration().screenWidthDp >= 600 ? 2 : 1), getString(R.string.tab_bt)));

        ViewPager2 viewPager = binding.viewPager;
        viewPager.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.ui_background));
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return tabModelList.get(position).fragment;
            }

            @Override
            public int getItemCount() {
                return tabModelList.size();
            }
        });
        TabLayout tabs = binding.tabs;

        new TabLayoutMediator(tabs, viewPager, (tab, position) -> tab.setText(tabModelList.get(position).title)).attach();
        if (savedInstanceState == null) showTabForIntent(getIntent());
    }

    public void showTab(String name) {
        int index = WidgetConstants.TAB_BLUETOOTH.equals(name) ? 2 : "widget".equals(name) ? 1 : 0;
        binding.viewPager.setCurrentItem(index, true);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showTabForIntent(intent);
        if (intent.hasExtra(WidgetConstants.EXTRA_DEVICE_ADDRESS)) {
            BatteryRepo.refresh("widget_open_device");
        }
    }

    /** A device target is sufficient to route correctly if an older widget omitted open_tab. */
    private void showTabForIntent(Intent intent) {
        String tab = intent.hasExtra(WidgetConstants.EXTRA_DEVICE_ADDRESS)
                ? WidgetConstants.TAB_BLUETOOTH
                : intent.getStringExtra(WidgetConstants.EXTRA_OPEN_TAB);
        showTab(tab);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_common, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.id_share) {
            ShareUtil.share(this);
            return true;
        } else if (item.getItemId() == R.id.id_advance) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (item.getItemId() == R.id.id_feedback) {
            ShareUtil.feedback(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class TabModel {
        Fragment fragment;
        String title;

        public TabModel(Fragment fragment, String title) {
            this.fragment = fragment;
            this.title = title;
        }
    }
}
