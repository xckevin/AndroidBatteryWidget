package com.github.xckevin927.android.battery.widget

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.xckevin927.android.battery.widget.billing.ProBilling
import com.github.xckevin927.android.battery.widget.repo.BatteryRepo
import com.github.xckevin927.android.battery.widget.utils.ReflectUtil
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.InvocationTargetException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Explicit opt-in, read-only diagnostics for real paired Bluetooth devices.
 *
 * This test never creates a GATT connection itself, connects/disconnects a device, changes
 * Bluetooth settings, or changes app/user state. BatteryRepo may perform its normal public GATT
 * fallback after its requested refresh; that production behavior is the subject being observed.
 * Run only with: -e liveBluetooth true
 */
@RunWith(AndroidJUnit4::class)
class LiveBluetoothDiagnosticsTest {

    @Test
    fun inspectRealBluetoothReadPathsAndRepositorySnapshots() {
        assumeTrue(
            "Live Bluetooth diagnostics require -e liveBluetooth true",
            InstrumentationRegistry.getArguments().getString("liveBluetooth") == "true"
        )

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        emit(instrumentation, JSONObject()
            .put("stage", "environment")
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("hasPro", ProBilling.hasPro(context))
            .put("reflectionBackend", "HiddenApiBypass")
            .put("hiddenApiAccessEnabled", ReflectUtil.isHiddenApiAccessEnabled()))

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = runCatching { manager?.adapter }.getOrNull()
        emit(instrumentation, JSONObject()
            .put("stage", "adapter")
            .put("present", adapter != null)
            .put("enabled", adapter?.let { runCatching { it.isEnabled }.getOrNull() } ?: JSONObject.NULL))

        val bonded = bondedDevices(adapter)
        emit(instrumentation, JSONObject()
            .put("stage", "bonded_devices")
            .put("devices", JSONArray().apply { bonded.forEach { put(inspectDevice(it)) } }))

        emit(instrumentation, JSONObject()
            .put("stage", "public_profiles")
            .put("headset", inspectProfile(context, adapter, BluetoothProfile.HEADSET, "HEADSET"))
            .put("a2dp", inspectProfile(context, adapter, BluetoothProfile.A2DP, "A2DP"))
            .put("gatt", inspectGatt(manager)))

        BatteryRepo.refresh("live_bluetooth_diagnostic")
        emit(instrumentation, JSONObject()
            .put("stage", "repository_initial")
            .put("snapshot", snapshotJson()))

        // BatteryRepo's production GATT timeout is 15 seconds. This only waits for that existing
        // asynchronous path; it performs no Bluetooth operation itself.
        SystemClock.sleep(REPOSITORY_SETTLE_MILLIS)
        emit(instrumentation, JSONObject()
            .put("stage", "repository_after_18_seconds")
            .put("snapshot", snapshotJson())
            .put("diagnosticComplete", true))
    }

    private fun bondedDevices(adapter: BluetoothAdapter?): List<BluetoothDevice> {
        if (adapter == null) return emptyList()
        return runCatching { adapter.bondedDevices.orEmpty().toList() }.getOrElse { emptyList() }
    }

    private fun inspectDevice(device: BluetoothDevice): JSONObject = JSONObject()
        .put("device", deviceId(device))
        .put("bondState", runCatching { device.bondState }.getOrNull() ?: JSONObject.NULL)
        .put("directIsConnected", directNoArgCall(device, "isConnected"))
        .put("directBatteryLevel", directNoArgCall(device, "getBatteryLevel"))
        .put("reflectUtilIsConnected", reflectUtilNoArgCall<Boolean>(device, "isConnected"))
        .put("reflectUtilBatteryLevel", reflectUtilNoArgCall<Int>(device, "getBatteryLevel"))

    private fun directNoArgCall(device: BluetoothDevice, methodName: String): JSONObject {
        return try {
            val method = BluetoothDevice::class.java.getDeclaredMethod(methodName)
            method.isAccessible = true
            JSONObject().put("value", jsonValue(method.invoke(device)))
        } catch (error: Throwable) {
            JSONObject().put("value", JSONObject.NULL).put("error", errorType(error))
        }
    }

    private fun <T> reflectUtilNoArgCall(device: BluetoothDevice, methodName: String): JSONObject {
        return try {
            val value = ReflectUtil.invoke<T>(device, methodName, emptyArray<Class<*>>())
            JSONObject().put("value", jsonValue(value))
        } catch (error: Throwable) {
            // ReflectUtil normally absorbs reflection exceptions and returns null; retain this
            // branch only for an unexpected helper-level failure.
            JSONObject().put("value", JSONObject.NULL).put("error", errorType(error))
        }
    }

    private fun inspectProfile(
        context: Context,
        adapter: BluetoothAdapter?,
        profileId: Int,
        profileName: String
    ): JSONObject {
        val result = JSONObject().put("profile", profileName)
        if (adapter == null) return result.put("requestAccepted", false).put("error", "adapter_unavailable")

        val latch = CountDownLatch(1)
        var proxy: BluetoothProfile? = null
        var callbackError: String? = null
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, connectedProxy: BluetoothProfile) {
                proxy = connectedProxy
                latch.countDown()
            }

            override fun onServiceDisconnected(profile: Int) = Unit
        }
        try {
            val accepted = adapter.getProfileProxy(context, listener, profileId)
            result.put("requestAccepted", accepted)
            if (accepted && !latch.await(PROFILE_WAIT_SECONDS, TimeUnit.SECONDS)) {
                result.put("callback", "timeout")
            }
            val connected = proxy
            if (connected != null) {
                val devices = try {
                    connected.connectedDevices
                } catch (error: Throwable) {
                    callbackError = errorType(error)
                    emptyList()
                }
                result.put("callback", "connected")
                    .put("connectedDevices", JSONArray().apply { devices.forEach { put(deviceId(it)) } })
            } else if (!result.has("callback")) {
                result.put("callback", "not_connected")
            }
            if (callbackError != null) result.put("error", callbackError)
        } catch (error: Throwable) {
            result.put("error", errorType(error))
        } finally {
            // Profile acquisition is read-only. Always release a successfully supplied proxy.
            proxy?.let { runCatching { adapter.closeProfileProxy(profileId, it) } }
        }
        return result
    }

    private fun inspectGatt(manager: BluetoothManager?): JSONObject {
        val result = JSONObject().put("profile", "GATT")
        if (manager == null) return result.put("error", "manager_unavailable")
        return try {
            result.put("connectedDevices", JSONArray().apply {
                manager.getConnectedDevices(BluetoothProfile.GATT).forEach { put(deviceId(it)) }
            })
        } catch (error: Throwable) {
            result.put("error", errorType(error))
        }
    }

    private fun snapshotJson(): JSONArray = JSONArray().apply {
        BatteryRepo.getBtSnapshot().forEach { state ->
            put(JSONObject()
                .put("device", deviceId(state.bluetoothDevice))
                .put("connected", state.isConnected)
                .put("batteryLevel", state.batteryLevel)
                .put("source", state.source)
                .put("status", state.status)
                .put("failure", state.failureReason ?: JSONObject.NULL)
                .put("lastCheckedAtMillis", state.lastCheckedAt)
                .put("lastSuccessfulReadAtMillis", state.lastSuccessfulReadAt))
        }
    }

    private fun deviceId(device: BluetoothDevice): String = try {
        val address = device.address ?: return "unavailable"
        val digest = MessageDigest.getInstance("SHA-256").digest(address.toByteArray(Charsets.UTF_8))
        "sha256:" + digest.take(6).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    } catch (_: Throwable) {
        "unavailable"
    }

    private fun errorType(error: Throwable): String {
        val cause = if (error is InvocationTargetException) error.targetException else error
        return cause.javaClass.simpleName.ifBlank { cause.javaClass.name }
    }

    private fun jsonValue(value: Any?): Any = value ?: JSONObject.NULL

    private fun emit(instrumentation: android.app.Instrumentation, value: JSONObject) {
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "\nLIVE_BLUETOOTH $value\n")
        })
    }

    private companion object {
        const val PROFILE_WAIT_SECONDS = 5L
        const val REPOSITORY_SETTLE_MILLIS = 18_000L
    }
}
