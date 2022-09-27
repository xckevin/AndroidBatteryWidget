package com.github.xckevin927.android.battery.widget.activity.fragment;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.databinding.FragmentBtDeviceBinding;
import com.github.xckevin927.android.battery.widget.model.BtDeviceState;

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

        public void bindData(BtDeviceState deviceState) {
            binding.img.setImageResource(R.drawable.ic_bluetoothon);
            binding.name.setText(deviceState.getName());
            binding.level.setText(deviceState.getBatteryLevel() + "%");
            binding.type.setText(Integer.toHexString(deviceState.getType()));
            binding.checkbox.setChecked(new Random().nextBoolean());
            binding.state.setBackgroundColor(getBtDeviceIndicatorColor(deviceState));
        }

        private int getBtDeviceIndicatorColor(BtDeviceState deviceState) {
            if (deviceState.getBatteryLevel() >= 0) {
                return ContextCompat.getColor(binding.getRoot().getContext(), R.color.bt_state_active);
            }
            if (deviceState.isConnected()) {
                return ContextCompat.getColor(binding.getRoot().getContext(), R.color.bt_state_connected);
            }
            return ContextCompat.getColor(binding.getRoot().getContext(), R.color.bt_state_unconnected);
        }

    }
}