package com.github.xckevin927.android.battery.widget.activity.fragment;

import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.xckevin927.android.battery.widget.databinding.FragmentBtDeviceBinding;
import com.github.xckevin927.android.battery.widget.model.BtDeviceState;

import java.util.List;

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
        holder.mItem = mValues.get(position);
        holder.mIdView.setText(mValues.get(position).getName());
        holder.mContentView.setText(mValues.get(position).toString());
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView mIdView;
        public final TextView mContentView;
        public BtDeviceState mItem;

        public ViewHolder(FragmentBtDeviceBinding binding) {
            super(binding.getRoot());
            mIdView = binding.itemNumber;
            mContentView = binding.content;
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mContentView.getText() + "'";
        }
    }
}