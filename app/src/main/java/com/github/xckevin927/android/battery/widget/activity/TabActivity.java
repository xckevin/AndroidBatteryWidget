package com.github.xckevin927.android.battery.widget.activity;

import android.content.Intent;
import android.graphics.Color;
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
import com.github.xckevin927.android.battery.widget.activity.fragment.phone.BatteryWidgetConfigFragment;
import com.github.xckevin927.android.battery.widget.databinding.ActivityTabBinding;
import com.github.xckevin927.android.battery.widget.utils.ShareUtil;

import com.github.xckevin927.android.battery.widget.utils.Utils;
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
        binding.toolbar.setTitle(R.string.app_name);

        List<TabModel> tabModelList = new ArrayList<>(4);
        tabModelList.add(new TabModel(BatteryWidgetConfigFragment.newInstance(), getString(R.string.tab_widget)));
        tabModelList.add(new TabModel(BtDeviceFragment.newInstance(2), getString(R.string.tab_bt)));

        ViewPager2 viewPager = binding.viewPager;
        viewPager.setBackgroundColor(Utils.isNightMode(this) ? Color.parseColor("#3f3f3f") : Color.parseColor("#f3f3f3"));
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