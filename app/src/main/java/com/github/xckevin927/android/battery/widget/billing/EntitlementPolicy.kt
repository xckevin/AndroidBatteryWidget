package com.github.xckevin927.android.battery.widget.billing

/** Pure ordering policy that prevents an older empty query from undoing a newer purchase update. */
internal class EntitlementPolicy(initiallyOwned: Boolean) {
    data class Snapshot(val owned: Boolean, val pending: Boolean, val revision: Long)
    data class QueryResult(val snapshot: Snapshot, val revokeCachedReceipt: Boolean)

    private var snapshot = Snapshot(initiallyOwned, pending = false, revision = 0)

    fun snapshot(): Snapshot = snapshot

    fun beginQuery(): Long = snapshot.revision

    fun purchaseConfirmed(): Snapshot {
        snapshot = Snapshot(owned = true, pending = false, revision = snapshot.revision + 1)
        return snapshot
    }

    fun purchasePending(): Snapshot {
        snapshot = Snapshot(owned = false, pending = true, revision = snapshot.revision + 1)
        return snapshot
    }

    fun completeSuccessfulQuery(
        startedAtRevision: Long,
        purchased: Boolean,
        pending: Boolean,
        resultWasEmpty: Boolean
    ): QueryResult {
        if (purchased) return QueryResult(purchaseConfirmed(), revokeCachedReceipt = false)
        if (snapshot.revision != startedAtRevision) {
            return QueryResult(snapshot, revokeCachedReceipt = false)
        }
        if (pending) {
            val revoke = snapshot.owned
            snapshot = Snapshot(owned = false, pending = true, revision = snapshot.revision + 1)
            return QueryResult(snapshot, revokeCachedReceipt = revoke)
        }
        if (resultWasEmpty) {
            snapshot = Snapshot(owned = false, pending = false, revision = snapshot.revision + 1)
            return QueryResult(snapshot, revokeCachedReceipt = true)
        }
        return QueryResult(snapshot, revokeCachedReceipt = false)
    }
}
