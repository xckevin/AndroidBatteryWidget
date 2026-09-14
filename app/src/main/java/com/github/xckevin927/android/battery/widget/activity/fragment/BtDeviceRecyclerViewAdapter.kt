package com.github.xckevin927.android.battery.widget.activity.fragment

import android.annotation.SuppressLint
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.xckevin927.android.battery.widget.R
import com.github.xckevin927.android.battery.widget.databinding.FragmentBtDeviceBinding
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import com.github.xckevin927.android.battery.widget.utils.BatteryStatusText
import com.github.xckevin927.android.battery.widget.utils.BtUtil
import com.github.xckevin927.android.battery.widget.utils.DevicePreferences

class BtDeviceRecyclerViewAdapter(
    private val onVisibilityChanged: (BtDeviceState, Boolean) -> Unit,
    private val onRename: (BtDeviceState) -> Unit,
    private val onMove: (BtDeviceState, Int) -> Unit
) :
    RecyclerView.Adapter<BtDeviceRecyclerViewAdapter.ViewHolder>() {

    private var values: List<BtDeviceState> = emptyList()
    private var highlightedDeviceAddress: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(FragmentBtDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindData(values[position], position)
    }

    override fun getItemCount() = values.size

    fun submitDevices(devices: List<BtDeviceState>) {
        values = devices.toList()
        notifyDataSetChanged()
    }

    fun highlightDevice(address: String?) {
        highlightedDeviceAddress = address
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: FragmentBtDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("MissingPermission")
        fun bindData(deviceState: BtDeviceState, position: Int) {
            val context = binding.root.context
            val displayName = DevicePreferences.displayName(context, deviceState)
            val status = BatteryStatusText.bluetooth(context, deviceState)
            val freshness = BatteryStatusText.freshness(context, deviceState)

            binding.name.text = displayName
            val hasCurrentBatteryLevel = deviceState.isConnected && deviceState.batteryLevel in 0..100
            binding.level.visibility = if (hasCurrentBatteryLevel) View.VISIBLE else View.GONE
            if (hasCurrentBatteryLevel) {
                binding.level.text = "${deviceState.batteryLevel}%"
            }
            binding.status.text = status.substringAfter(" · ", status)
            binding.freshness.text = freshness

            val deviceDescription = try {
                BtUtil.getBtClassDrawableWithDescription(context, deviceState.bluetoothDevice)
            } catch (_: SecurityException) {
                null
            }
            if (deviceDescription != null) {
                binding.img.setImageDrawable(deviceDescription.first)
                binding.type.text = context.getString(
                    R.string.device_type_and_source,
                    deviceDescription.second,
                    sourceLabel(deviceState)
                )
            } else {
                binding.img.setImageResource(R.drawable.ic_settings_bluetooth)
                binding.type.text = sourceLabel(deviceState)
            }

            binding.state.setBackgroundColor(statusColor(deviceState))
            val address = try {
                deviceState.bluetoothDevice.address
            } catch (_: SecurityException) {
                null
            }
            val highlighted = address != null &&
                address.equals(highlightedDeviceAddress, ignoreCase = true)
            binding.root.strokeColor = ContextCompat.getColor(
                context,
                if (highlighted) R.color.ui_primary else R.color.ui_outline
            )
            val strokeWidthDp = if (highlighted) 3 else 1
            binding.root.strokeWidth = (strokeWidthDp * context.resources.displayMetrics.density)
                .toInt()
                .coerceAtLeast(1)
            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.isChecked = DevicePreferences.isVisible(context, deviceState)
            binding.checkbox.setOnCheckedChangeListener { _, checked ->
                onVisibilityChanged(deviceState, checked)
            }
            binding.rename.setOnClickListener { onRename(deviceState) }
            binding.moveUp.isEnabled = position > 0
            binding.moveDown.isEnabled = position < values.lastIndex
            binding.moveUp.setOnClickListener { onMove(deviceState, -1) }
            binding.moveDown.setOnClickListener { onMove(deviceState, 1) }
            binding.root.contentDescription = "$displayName. $status. $freshness"
        }

        private fun sourceLabel(deviceState: BtDeviceState): String {
            val context = binding.root.context
            return context.getString(
                when (deviceState.source) {
                    "framework" -> R.string.device_source_framework
                    "gatt" -> R.string.device_source_gatt
                    else -> R.string.device_source_unknown
                }
            )
        }

        private fun statusColor(deviceState: BtDeviceState): Int {
            val color = when {
                !deviceState.isConnected -> R.color.bt_state_unconnected
                deviceState.status == "available" -> R.color.bt_state_active
                deviceState.status == "cached" -> R.color.bt_state_connected
                else -> R.color.bt_state_unconnected
            }
            return ContextCompat.getColor(binding.root.context, color)
        }
    }
}
