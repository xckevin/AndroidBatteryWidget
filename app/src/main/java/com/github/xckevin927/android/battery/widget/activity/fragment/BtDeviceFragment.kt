package com.github.xckevin927.android.battery.widget.activity.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.xckevin927.android.battery.widget.App
import com.github.xckevin927.android.battery.widget.R
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo.getBtDeviceStates
import com.github.xckevin927.android.battery.widget.ui.GridSpaceItemDecoration
import com.github.xckevin927.android.battery.widget.ui.SpacesItemDecoration
import com.github.xckevin927.android.battery.widget.utils.UiUtil

/**
 * A fragment representing a list of Items.
 */
class BtDeviceFragment : Fragment() {
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private var btPermissionLauncher: ActivityResultLauncher<String>? = null
    private var btOpenLauncher: ActivityResultLauncher<Intent>? = null

    private var actionText: TextView? = null
    private lateinit var recyclerView: RecyclerView
    private var adapter:BtDeviceRecyclerViewAdapter? = null
    private var tipsText: TextView? = null
    private var mColumnCount = 1

    private val listener = object : (BtDeviceState) -> Unit {
        override fun invoke(p1: BtDeviceState) {
            adapter?.mValues?.indexOf(p1)?.let {
                adapter?.notifyItemChanged(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            mColumnCount = requireArguments().getInt(ARG_COLUMN_COUNT)
        }
        val bluetoothManager = App.getAppContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        btPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { result: Boolean? ->
            if (result != null && result) {
                refreshViews()
            }
        }
        btOpenLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                refreshViews()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_bt_device_item_list, container, false)
        actionText = view.findViewById(R.id.id_action_fragment_bt)
        recyclerView = view.findViewById(R.id.id_list_fragment_bt)
        tipsText = view.findViewById(R.id.id_tips_fragment_bt)
        if (mColumnCount <= 1) {
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.addItemDecoration(SpacesItemDecoration(UiUtil.dp2px(recyclerView.getContext(), 8)))
        } else {
            val margin = UiUtil.dp2px(recyclerView.context, 8)
            recyclerView.layoutManager = GridLayoutManager(context, mColumnCount)
            recyclerView.addItemDecoration(GridSpaceItemDecoration(mColumnCount, margin, margin))
        }

        BatteryRepo.leUpdateListener.add(listener)
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        BatteryRepo.leUpdateListener.remove(listener)
    }

    override fun onResume() {
        super.onResume()
        refreshViews()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            refreshViews()
        }
    }

    private fun refreshViews() {
        val context = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            actionText!!.visibility = View.VISIBLE
            actionText!!.setText(R.string.open_bt_permission)
            actionText!!.setOnClickListener { v: View? -> btPermissionLauncher!!.launch(Manifest.permission.BLUETOOTH_CONNECT) }
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            actionText!!.visibility = View.VISIBLE
            actionText!!.setText(R.string.open_bt)
            actionText!!.setOnClickListener { v: View? -> btOpenLauncher!!.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            return
        }
        actionText!!.visibility = View.GONE
        renderBtList()
    }

    @SuppressLint("MissingPermission")
    private fun renderBtList() {
        val list = getBtDeviceStates()
        if (list.isNotEmpty()) {
            tipsText!!.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            adapter = BtDeviceRecyclerViewAdapter(list)
            recyclerView.adapter = adapter
        } else {
            tipsText!!.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    companion object {
        private const val TAG = "BtDeviceFragment"
        private const val ARG_COLUMN_COUNT = "column-count"
        @JvmStatic
        fun newInstance(columnCount: Int): BtDeviceFragment {
            val fragment = BtDeviceFragment()
            val args = Bundle()
            args.putInt(ARG_COLUMN_COUNT, columnCount)
            fragment.arguments = args
            return fragment
        }
    }
}