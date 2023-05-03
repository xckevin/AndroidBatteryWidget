package com.github.xckevin927.android.battery.widget.activity.fragment

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.xckevin927.android.battery.widget.R
import com.github.xckevin927.android.battery.widget.databinding.FragmentBtDeviceBinding
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import com.github.xckevin927.android.battery.widget.utils.BtUtil
import java.util.Random

class BtDeviceRecyclerViewAdapter(val mValues: List<BtDeviceState>) :
    RecyclerView.Adapter<BtDeviceRecyclerViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(FragmentBtDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindData(mValues[position])
    }

    override fun getItemCount() = mValues.size

    inner class ViewHolder(private val binding: FragmentBtDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("MissingPermission", "SetTextI18n")
        fun bindData(deviceState: BtDeviceState) {
//            binding.img.setImageResource(R.drawable.ic_bluetoothon);
            binding.name.text = deviceState.bluetoothDevice.name
            if (deviceState.batteryLevel >= 0) {
                binding.level.text = "${deviceState.batteryLevel}%"
                binding.level.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_battery, 0, 0, 0)
            } else {
                binding.level.text = "-"
                binding.level.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }
            val info = BtUtil.getBtClassDrawableWithDescription(
                binding.root.context, deviceState.bluetoothDevice
            )
            binding.img.setImageDrawable(info.first)
            binding.type.text = info.second
            binding.checkbox.isChecked = Random().nextBoolean()
            binding.state.setBackgroundColor(BtUtil.getBtDeviceIndicatorColor(binding.root.context, deviceState))
        }
    }
}