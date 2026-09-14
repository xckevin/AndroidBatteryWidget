package com.github.xckevin927.android.battery.widget.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceOrderTest {
    @Test
    fun reconcile_keepsKnownOrder_removesMissing_andAppendsNewDevices() {
        assertEquals(
            listOf("headphones", "watch", "keyboard"),
            DeviceOrder.reconcile(
                stored = listOf("missing", "headphones", "watch", "headphones"),
                available = listOf("watch", "keyboard", "headphones")
            )
        )
    }

    @Test
    fun move_reordersWithinBounds() {
        val devices = listOf("headphones", "watch", "keyboard")
        assertEquals(listOf("watch", "headphones", "keyboard"), DeviceOrder.move(devices, "watch", -1))
        assertEquals(listOf("headphones", "keyboard", "watch"), DeviceOrder.move(devices, "watch", 1))
        assertEquals(devices, DeviceOrder.move(devices, "headphones", -1))
        assertEquals(devices, DeviceOrder.move(devices, "keyboard", 1))
    }
}
