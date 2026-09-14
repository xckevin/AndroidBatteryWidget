package com.github.xckevin927.android.battery.widget.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementPolicyTest {
    @Test fun currentEmptyRelevantResultRevokesCachedEntitlement() {
        val policy = EntitlementPolicy(initiallyOwned = true)
        val ticket = policy.beginQuery()

        val result = policy.completeSuccessfulQuery(ticket, purchased = false,
            pending = false, resultWasEmpty = true)

        assertFalse(result.snapshot.owned)
        assertTrue(result.revokeCachedReceipt)
    }

    @Test fun currentPendingResultDoesNotKeepOldEntitlement() {
        val policy = EntitlementPolicy(initiallyOwned = true)
        val ticket = policy.beginQuery()

        val result = policy.completeSuccessfulQuery(ticket, purchased = false,
            pending = true, resultWasEmpty = false)

        assertFalse(result.snapshot.owned)
        assertTrue(result.snapshot.pending)
        assertTrue(result.revokeCachedReceipt)
    }

    @Test fun staleEmptyQueryCannotUndoNewPurchaseUpdate() {
        val policy = EntitlementPolicy(initiallyOwned = false)
        val oldTicket = policy.beginQuery()
        policy.purchaseConfirmed()

        val result = policy.completeSuccessfulQuery(oldTicket, purchased = false,
            pending = false, resultWasEmpty = true)

        assertTrue(result.snapshot.owned)
        assertFalse(result.revokeCachedReceipt)
    }

    @Test fun stalePendingQueryCannotUndoNewPurchaseUpdate() {
        val policy = EntitlementPolicy(initiallyOwned = false)
        val oldTicket = policy.beginQuery()
        policy.purchaseConfirmed()

        val result = policy.completeSuccessfulQuery(oldTicket, purchased = false,
            pending = true, resultWasEmpty = false)

        assertTrue(result.snapshot.owned)
        assertFalse(result.snapshot.pending)
        assertFalse(result.revokeCachedReceipt)
    }
}
