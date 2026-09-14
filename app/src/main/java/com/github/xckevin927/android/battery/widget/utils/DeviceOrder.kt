package com.github.xckevin927.android.battery.widget.utils

/** Pure ordering rules shared by device preferences and local unit tests. */
internal object DeviceOrder {
    fun reconcile(stored: List<String>, available: List<String>): List<String> {
        val availableKeys = available.toSet()
        val result = LinkedHashSet<String>()
        stored.filterTo(result) { it in availableKeys }
        available.forEach(result::add)
        return result.toList()
    }

    fun move(ordered: List<String>, key: String, offset: Int): List<String> {
        val from = ordered.indexOf(key)
        if (from < 0) return ordered
        val to = (from + offset).coerceIn(0, ordered.lastIndex)
        if (from == to) return ordered
        return ordered.toMutableList().apply {
            add(to, removeAt(from))
        }
    }
}
