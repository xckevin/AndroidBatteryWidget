package com.github.xckevin927.android.battery.widget.activity.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.databinding.FragmentBtDeviceBinding;
import com.github.xckevin927.android.battery.widget.model.BtDeviceState;
import com.github.xckevin927.android.battery.widget.utils.BtUtil;

import java.util.List;
import java.util.Random;

public class BtDeviceRecyclerViewAdapter extends RecyclerView.Adapter<BtDeviceRecyclerViewAdapter.ViewHolder> {

    private final List<BtDeviceState> mValues;

    public BtDeviceRecyclerViewAdapter(List<BtDeviceState> items) {
        mValues = items;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        return new ViewHolder(FragmentBtDeviceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));

    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.bindData(mValues.get(position));
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final FragmentBtDeviceBinding binding;

        public ViewHolder(FragmentBtDeviceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @SuppressLint("MissingPermission")
        public void bindData(BtDeviceState deviceState) {
//            binding.img.setImageResource(R.drawable.ic_bluetoothon);
            binding.name.setText(deviceState.getBluetoothDevice().getName());
            if (deviceState.getBatteryLevel() >= 0) {
                binding.level.setText(deviceState.getBatteryLevel() + "%");
                binding.level.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_battery, 0, 0, 0);
            } else {
                binding.level.setText("-");
                binding.level.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
            Pair<Drawable, String> info = BtUtil.getBtClassDrawableWithDescription(binding.getRoot().getContext(), deviceState.getBluetoothDevice());
            binding.img.setImageDrawable(info.first);
            binding.type.setText(info.second);
            binding.checkbox.setChecked(new Random().nextBoolean());
            binding.state.setBackgroundColor(BtUtil.getBtDeviceIndicatorColor(binding.getRoot().getContext(), deviceState));
        }



    }
}