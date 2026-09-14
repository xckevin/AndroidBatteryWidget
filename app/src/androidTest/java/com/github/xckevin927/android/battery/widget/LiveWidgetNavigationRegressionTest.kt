package com.github.xckevin927.android.battery.widget

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.xckevin927.android.battery.widget.activity.TabActivity
import com.github.xckevin927.android.battery.widget.appwidget.WidgetConstants
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo
import com.github.xckevin927.android.battery.widget.utils.DevicePreferences
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in QA check of the real outer viewport, without editing paired devices or preferences. */
@RunWith(AndroidJUnit4::class)
class LiveWidgetNavigationRegressionTest {
    @Test fun realOffscreenDeviceIsVisibleAfterColdAndWarmTargetIntents() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveWidgetNavigation") == "true")
        assumeTrue(context.packageName == "com.github.xckevin927.android.battery.widget.oppoqa")
        BatteryRepo.refresh("qa_widget_navigation_baseline")
        val devices = DevicePreferences.allDevices(context, BatteryRepo.getBtSnapshot())
        val target = devices.lastOrNull { it.isConnected && it.batteryLevel in 0..100 }
        assumeTrue("Requires a real connected accessory with battery data", target != null)
        val address = requireNotNull(target).bluetoothDevice.address

        fun intent(withTarget: Boolean) = Intent(context, TabActivity::class.java)
            .putExtra(WidgetConstants.EXTRA_OPEN_TAB, WidgetConstants.TAB_BLUETOOTH)
            .apply { if (withTarget) putExtra(WidgetConstants.EXTRA_DEVICE_ADDRESS, address) }

        fun targetNameView(activity: TabActivity): View? {
            val recycler = activity.findViewById<RecyclerView>(R.id.id_list_fragment_bt) ?: return null
            val index = DevicePreferences.allDevices(context, BatteryRepo.getBtSnapshot())
                .indexOfFirst { it.bluetoothDevice.address == address }
            return recycler.findViewHolderForAdapterPosition(index)?.itemView?.findViewById(R.id.name)
        }

        fun isVisible(view: View?): Boolean {
            val rect = Rect()
            return view != null && view.getGlobalVisibleRect(rect) && rect.height() >= view.height && view.height > 0
        }

        fun pageScroll(activity: TabActivity): NestedScrollView {
            var parent = activity.findViewById<RecyclerView>(R.id.id_list_fragment_bt).parent
            while (parent !is NestedScrollView) parent = parent.parent
            return parent
        }

        fun withActivity(launchIntent: Intent, block: (TabActivity) -> Unit) {
            val activity = instrumentation.startActivitySync(launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )) as TabActivity
            try {
                instrumentation.waitForIdleSync()
                block(activity)
            } finally {
                instrumentation.runOnMainSync { activity.finish() }
                instrumentation.waitForIdleSync()
            }
        }

        fun awaitTarget(activity: TabActivity) {
            val deadline = SystemClock.elapsedRealtime() + 10_000
            var visible = false
            while (!visible && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync { visible = isVisible(targetNameView(activity)) && pageScroll(activity).scrollY > 0 }
                if (!visible) SystemClock.sleep(100)
            }
            assertTrue("Target card must be visible in the outer page viewport", visible)
        }

        withActivity(intent(false)) { baseline ->
            instrumentation.waitForIdleSync()
            val deadline = SystemClock.elapsedRealtime() + 10_000
            var listReady = false
            while (!listReady && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync { listReady = baseline.findViewById<RecyclerView>(R.id.id_list_fragment_bt) != null }
                if (!listReady) SystemClock.sleep(100)
            }
            assertTrue("Bluetooth list must finish creating its view", listReady)
            var initiallyVisible = false
            instrumentation.runOnMainSync {
                pageScroll(baseline).scrollTo(0, 0)
                initiallyVisible = isVisible(targetNameView(baseline))
            }
            assumeTrue("Requires a target outside the first screen to prove page scrolling", !initiallyVisible)
        }

        withActivity(intent(true)) { activity ->
            awaitTarget(activity)
            instrumentation.runOnMainSync { pageScroll(activity).scrollTo(0, 0) }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                activity.startActivity(intent(true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            awaitTarget(activity)
        }
    }
}
