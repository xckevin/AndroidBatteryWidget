package com.github.xckevin927.android.battery.widget.activity.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.xckevin927.android.battery.widget.R
import com.github.xckevin927.android.battery.widget.appwidget.WidgetConstants
import com.github.xckevin927.android.battery.widget.model.BtDeviceState
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo
import com.github.xckevin927.android.battery.widget.ui.GridSpaceItemDecoration
import com.github.xckevin927.android.battery.widget.ui.SpacesItemDecoration
import com.github.xckevin927.android.battery.widget.utils.DevicePreferences
import com.github.xckevin927.android.battery.widget.utils.UiUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

/** Displays and manages paired Bluetooth devices without triggering reads while rendering. */
class BtDeviceFragment : Fragment() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var intentLauncher: ActivityResultLauncher<Intent>

    private lateinit var actionButton: MaterialButton
    private lateinit var refreshButton: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var summaryText: TextView
    private lateinit var systemStatusText: TextView
    private lateinit var tipsText: TextView
    private lateinit var emptyState: View
    private lateinit var pageScrollView: NestedScrollView
    private lateinit var deviceAdapter: BtDeviceRecyclerViewAdapter
    private var columnCount = 1
    private var subscribed = false
    private var pendingTargetAddress: String? = null
    private var pendingTargetRefreshRequestedAt: Long? = null

    private val clearDeviceHighlight = Runnable {
        if (::deviceAdapter.isInitialized) deviceAdapter.highlightDevice(null)
    }

    private val repoListener = Runnable {
        if (!isAdded || view == null || isHidden) return@Runnable
        recyclerView.post { renderEnvironment(triggerRefresh = false) }
    }

    /** Refreshes age/staleness labels from the cache only; it never starts Bluetooth I/O. */
    private val freshnessTicker = object : Runnable {
        override fun run() {
            if (!isAdded || view == null || isHidden || !isResumed) return
            renderEnvironment(triggerRefresh = false)
            view?.postDelayed(this, FRESHNESS_REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        columnCount = arguments?.getInt(ARG_COLUMN_COUNT, 1) ?: 1
        val manager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = manager?.adapter

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                renderEnvironment(triggerRefresh = true, refreshReason = "bluetooth_permission_granted")
            } else {
                renderEnvironment(triggerRefresh = false)
            }
        }
        intentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            renderEnvironment(triggerRefresh = true, refreshReason = "bluetooth_recovery_returned")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_bt_device_item_list, container, false)
        pageScrollView = root as NestedScrollView
        actionButton = root.findViewById(R.id.id_action_fragment_bt)
        refreshButton = root.findViewById(R.id.id_refresh_fragment_bt)
        recyclerView = root.findViewById(R.id.id_list_fragment_bt)
        summaryText = root.findViewById(R.id.id_summary_fragment_bt)
        systemStatusText = root.findViewById(R.id.id_system_status_fragment_bt)
        tipsText = root.findViewById(R.id.id_tips_fragment_bt)
        emptyState = root.findViewById(R.id.id_empty_state_fragment_bt)

        if (columnCount <= 1) {
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.addItemDecoration(SpacesItemDecoration(UiUtil.dp2px(requireContext(), 8)))
        } else {
            val margin = UiUtil.dp2px(requireContext(), 4)
            recyclerView.layoutManager = GridLayoutManager(requireContext(), columnCount)
            recyclerView.addItemDecoration(GridSpaceItemDecoration(columnCount, margin, margin))
        }

        deviceAdapter = BtDeviceRecyclerViewAdapter(
            onVisibilityChanged = { state, visible ->
                DevicePreferences.setVisible(requireContext(), state, visible)
                renderSnapshot()
            },
            onRename = ::showRenameDialog,
            onMove = { state, offset ->
                val snapshot = BatteryRepo.getBtSnapshot()
                DevicePreferences.move(requireContext(), snapshot, state, offset)
                renderSnapshot()
            }
        )
        recyclerView.adapter = deviceAdapter
        refreshButton.setOnClickListener {
            renderEnvironment(triggerRefresh = true, refreshReason = "bluetooth_page_manual")
        }
        return root
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            subscribe()
            renderEnvironment(triggerRefresh = true, refreshReason = "bluetooth_page")
            restartFreshnessTicker()
        }
    }

    override fun onPause() {
        stopFreshnessTicker()
        unsubscribe()
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            stopFreshnessTicker()
            unsubscribe()
        } else if (isResumed && view != null) {
            subscribe()
            renderEnvironment(triggerRefresh = true, refreshReason = "bluetooth_page")
            restartFreshnessTicker()
        }
    }

    override fun onDestroyView() {
        stopFreshnessTicker()
        unsubscribe()
        recyclerView.removeCallbacks(clearDeviceHighlight)
        recyclerView.adapter = null
        super.onDestroyView()
    }

    private fun subscribe() {
        if (subscribed) return
        BatteryRepo.addListener(repoListener)
        subscribed = true
    }

    private fun unsubscribe() {
        if (!subscribed) return
        BatteryRepo.removeListener(repoListener)
        subscribed = false
    }

    private fun restartFreshnessTicker() {
        stopFreshnessTicker()
        view?.postDelayed(freshnessTicker, FRESHNESS_REFRESH_INTERVAL_MS)
    }

    private fun stopFreshnessTicker() {
        view?.removeCallbacks(freshnessTicker)
    }

    private fun renderEnvironment(
        triggerRefresh: Boolean,
        refreshReason: String = "bluetooth_page"
    ) {
        if (!isAdded || view == null) return
        val context = requireContext()
        val adapter = bluetoothAdapter
        if (adapter == null) {
            deferPendingTargetNavigation()
            showBlockingState(R.string.device_no_bluetooth_hardware)
            return
        }
        if (!hasBluetoothPermission(context)) {
            deferPendingTargetNavigation()
            showPermissionState(context)
            return
        }
        val enabled = try {
            adapter.isEnabled
        } catch (_: SecurityException) {
            false
        }
        if (!enabled) {
            deferPendingTargetNavigation()
            showBlockingState(
                message = R.string.device_bluetooth_off,
                action = R.string.device_enable_bluetooth
            ) {
                intentLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            return
        }

        clearBlockingState()
        renderSnapshot()
        if (requestPendingTargetRefresh()) {
            BatteryRepo.refresh("widget_device_focus")
        } else if (triggerRefresh) {
            BatteryRepo.refresh(refreshReason)
        }
    }

    private fun hasBluetoothPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun showPermissionState(context: Context) {
        val wasRequested = DevicePreferences.wasBluetoothPermissionRequested(context)
        val permanentlyDenied = wasRequested &&
            !shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)
        if (permanentlyDenied) {
            showBlockingState(
                message = R.string.device_permission_permanently_denied,
                action = R.string.device_open_app_settings
            ) {
                val uri = Uri.fromParts("package", context.packageName, null)
                intentLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
            }
        } else {
            showBlockingState(
                message = if (wasRequested) {
                    R.string.device_permission_denied
                } else {
                    R.string.device_permission_rationale
                },
                action = R.string.device_allow_permission
            ) {
                DevicePreferences.markBluetoothPermissionRequested(context)
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }

    private fun showBlockingState(
        message: Int,
        action: Int? = null,
        onAction: (() -> Unit)? = null
    ) {
        summaryText.visibility = View.GONE
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.GONE
        refreshButton.isEnabled = false
        systemStatusText.setText(message)
        systemStatusText.visibility = View.VISIBLE
        actionButton.setOnClickListener(null)
        if (action != null && onAction != null) {
            actionButton.setText(action)
            actionButton.setOnClickListener { onAction() }
            actionButton.visibility = View.VISIBLE
        } else {
            actionButton.visibility = View.GONE
        }
    }

    private fun clearBlockingState() {
        systemStatusText.visibility = View.GONE
        actionButton.visibility = View.GONE
        actionButton.setOnClickListener(null)
        refreshButton.isEnabled = true
    }

    private fun renderSnapshot() {
        if (!isAdded || view == null) return
        val context = requireContext()
        val allDevices = DevicePreferences.allDevices(context, BatteryRepo.getBtSnapshot())
        val visibleCount = DevicePreferences.visibleDevices(context, allDevices).size
        summaryText.text = getString(R.string.device_list_summary, allDevices.size, visibleCount)
        summaryText.visibility = View.VISIBLE

        if (allDevices.isEmpty()) {
            deviceAdapter.submitDevices(emptyList())
            recyclerView.visibility = View.GONE
            tipsText.setText(R.string.device_ui_empty_title)
            emptyState.visibility = View.VISIBLE
            actionButton.setText(R.string.device_open_bluetooth_settings)
            actionButton.setOnClickListener {
                intentLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            actionButton.visibility = View.VISIBLE
        } else {
            emptyState.visibility = View.GONE
            actionButton.visibility = View.GONE
            actionButton.setOnClickListener(null)
            recyclerView.visibility = View.VISIBLE
            deviceAdapter.submitDevices(allDevices)
        }
        resolvePendingTarget(allDevices)
    }

    private fun resolvePendingTarget(devices: List<BtDeviceState>) {
        val target = readPendingTargetAddress() ?: return
        val position = devices.indexOfFirst { state ->
            addressOf(state)?.normalizeAddress() == target
        }
        if (position >= 0) {
            deviceAdapter.highlightDevice(target)
            consumePendingTarget()
            recyclerView.removeCallbacks(clearDeviceHighlight)
            scrollToTarget(position)
            recyclerView.postDelayed(clearDeviceHighlight, DEVICE_HIGHLIGHT_DURATION_MS)
            return
        }

        val requestedAt = pendingTargetRefreshRequestedAt ?: return
        if (BatteryRepo.lastRefreshAt >= requestedAt) {
            consumePendingTarget()
            Snackbar.make(
                requireView(),
                R.string.device_widget_target_unavailable,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun requestPendingTargetRefresh(): Boolean {
        if (readPendingTargetAddress() == null || pendingTargetRefreshRequestedAt != null) {
            return false
        }
        pendingTargetRefreshRequestedAt = System.currentTimeMillis()
        return true
    }

    /** Keep the Activity extra while permission, Bluetooth, or the first authoritative snapshot is unavailable. */
    private fun deferPendingTargetNavigation() {
        if (readPendingTargetAddress() != null) pendingTargetRefreshRequestedAt = null
    }

    private fun readPendingTargetAddress(): String? {
        val current = activity?.intent
            ?.getStringExtra(WidgetConstants.EXTRA_DEVICE_ADDRESS)
            ?.takeIf { it.isNotBlank() }
            ?.normalizeAddress()
        if (current != pendingTargetAddress) {
            pendingTargetAddress = current
            pendingTargetRefreshRequestedAt = null
        }
        return current
    }

    private fun consumePendingTarget() {
        activity?.intent?.removeExtra(WidgetConstants.EXTRA_DEVICE_ADDRESS)
        pendingTargetAddress = null
        pendingTargetRefreshRequestedAt = null
    }

    /**
     * The device cards are expanded inside the page NestedScrollView, so moving the RecyclerView's
     * LayoutManager alone does not move the visible viewport. Wait until submitDevices has laid out
     * the target card, then scroll the owning page container to that card.
     */
    private fun scrollToTarget(position: Int, attempt: Int = 0) {
        recyclerView.post {
            if (!isAdded || view == null) return@post
            val targetView = recyclerView.layoutManager?.findViewByPosition(position)
            if (targetView == null && attempt == 0) {
                recyclerView.scrollToPosition(position)
                scrollToTarget(position, attempt + 1)
                return@post
            }
            if (targetView != null) {
                val targetBounds = Rect()
                targetView.getDrawingRect(targetBounds)
                pageScrollView.offsetDescendantRectToMyCoords(targetView, targetBounds)
                pageScrollView.smoothScrollTo(0, targetBounds.top)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun addressOf(state: BtDeviceState): String? = try {
        state.bluetoothDevice.address
    } catch (_: SecurityException) {
        null
    }

    private fun String.normalizeAddress(): String = trim().uppercase(Locale.ROOT)

    private fun showRenameDialog(state: BtDeviceState) {
        if (!isAdded) return
        val context = requireContext()
        val input = EditText(context).apply {
            hint = getString(R.string.device_alias_hint)
            setText(DevicePreferences.alias(context, state))
            filters = arrayOf(InputFilter.LengthFilter(MAX_ALIAS_LENGTH))
            setSelection(text.length)
        }
        val horizontalPadding = UiUtil.dp2px(context, 24)
        val container = android.widget.FrameLayout(context).apply {
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            addView(input, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.device_alias_title)
            .setMessage(R.string.device_alias_help)
            .setView(container)
            .setNegativeButton(R.string.device_alias_cancel, null)
            .setPositiveButton(R.string.device_alias_save) { _, _ ->
                DevicePreferences.setAlias(context, state, input.text?.toString().orEmpty())
                renderSnapshot()
            }
            .show()
    }

    companion object {
        private const val ARG_COLUMN_COUNT = "column-count"
        private const val FRESHNESS_REFRESH_INTERVAL_MS = 30_000L
        private const val DEVICE_HIGHLIGHT_DURATION_MS = 4_000L
        private const val MAX_ALIAS_LENGTH = 60

        @JvmStatic
        fun newInstance(columnCount: Int): BtDeviceFragment {
            return BtDeviceFragment().apply {
                arguments = Bundle().apply { putInt(ARG_COLUMN_COUNT, columnCount) }
            }
        }
    }
}
