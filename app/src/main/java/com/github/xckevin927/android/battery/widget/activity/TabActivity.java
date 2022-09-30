package com.github.xckevin927.android.battery.widget.activity;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.activity.fragment.BtDeviceFragment;
import com.github.xckevin927.android.battery.widget.activity.fragment.phone.BatteryWidgetConfigFragment;
import com.github.xckevin927.android.battery.widget.databinding.ActivityTabBinding;
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

    private static class TabModel {
        Fragment fragment;
        String title;

        public TabModel(Fragment fragment, String title) {
            this.fragment = fragment;
            this.title = title;
        }
    }
}