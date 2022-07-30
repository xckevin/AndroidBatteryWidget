package com.github.xckevin927.android.battery.widget.activity.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.xckevin927.android.battery.widget.R;
import com.github.xckevin927.android.battery.widget.model.BtDeviceState;
import com.github.xckevin927.android.battery.widget.utils.ReflectUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A fragment representing a list of Items.
 */
public class BtDeviceFragment extends Fragment {

    private static final String TAG = "BtDeviceFragment";
    private static final String ARG_COLUMN_COUNT = "column-count";

    private BluetoothAdapter bluetoothAdapter;

    private ActivityResultLauncher<String> btPermissionLauncher;
    private ActivityResultLauncher<Intent> btOpenLauncher;

    private TextView actionText;
    private RecyclerView recyclerView;
    private TextView tipsText;

    private int mColumnCount = 1;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public BtDeviceFragment() {
    }

    public static BtDeviceFragment newInstance(int columnCount) {
        BtDeviceFragment fragment = new BtDeviceFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        btPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
            if (result != null && result) {
                refreshViews();
            }
        });
        btOpenLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                refreshViews();
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bt_device_item_list, container, false);

        actionText = view.findViewById(R.id.id_action_fragment_bt);
        recyclerView = view.findViewById(R.id.id_list_fragment_bt);
        tipsText = view.findViewById(R.id.id_tips_fragment_bt);

        if (mColumnCount <= 1) {
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        } else {
            recyclerView.setLayoutManager(new GridLayoutManager(getContext(), mColumnCount));
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshViews();
    }

    private void refreshViews() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            actionText.setVisibility(View.VISIBLE);
            actionText.setText("get permission to get device");
            actionText.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
                }
            });
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            actionText.setVisibility(View.VISIBLE);
            actionText.setText("get permission to get device");
            actionText.setOnClickListener(v -> btOpenLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)));
            return;
        }
        actionText.setVisibility(View.GONE);
        renderBtList();
    }

    @SuppressLint("MissingPermission")
    private void renderBtList() {
        final Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        final int pairedDevicesCount = pairedDevices.size();
        if (pairedDevicesCount > 0) {
            tipsText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            List<BtDeviceState> list = new ArrayList<>(pairedDevicesCount);
            for (BluetoothDevice device : pairedDevices) {
                int level = ReflectUtil.invoke(device, "getBatteryLevel", new Class[0]);
                boolean connected = ReflectUtil.invoke(device, "isConnected", new Class[0]);

                BtDeviceState.BtDeviceStateBuilder builder = BtDeviceState.builder()
                        .withAddr(device.getAddress())
                        .withName(device.getName())
                        .withBatteryLevel(level)
                        .withDeviceClass(device.getBluetoothClass().getDeviceClass())
                        .withConnected(connected)
                        .withType(device.getBluetoothClass().getMajorDeviceClass());
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    String alias = device.getAlias();
                    builder.withAlias(alias);
                }
                list.add(builder.build());
            }
            recyclerView.setAdapter(new BtDeviceRecyclerViewAdapter(list));
        } else {
            tipsText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        }
    }
}