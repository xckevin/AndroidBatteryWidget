package com.github.xckevin927.android.battery.widget.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArraySet

/** Application-wide owner for the single non-consumable Google Play product. */
object ProBilling : PurchasesUpdatedListener {
    enum class ErrorCode {
        BILLING_UNAVAILABLE,
        NETWORK,
        PRODUCT_UNAVAILABLE,
        PURCHASE_FAILED,
        VERIFICATION_FAILED,
        ACKNOWLEDGEMENT_FAILED,
        STORAGE_FAILED
    }

    enum class MessageCode { READY, RESTORED, PURCHASE_PENDING, PURCHASED }

    data class State(
        val owned: Boolean = false,
        val pending: Boolean = false,
        val busy: Boolean = false,
        val price: String? = null,
        val error: ErrorCode? = null,
        val message: MessageCode? = null
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Runnable>()
    private val cacheLock = Any()
    private val afterSetup = ArrayDeque<(BillingClient) -> Unit>()

    @Volatile private var state = State()
    @Volatile private var cachedOwned: Boolean? = null
    private var appContext: Context? = null
    private var receiptStore: ReceiptStore? = null
    private var entitlement: EntitlementPolicy? = null
    private var client: BillingClient? = null
    private var connecting = false
    private var setupCompleted = false
    private var busyCount = 0
    private var purchaseQueryInFlight = false
    private var purchaseQueryAgain = false
    private var restoreRequested = false
    private var priceQueryInFlight = false
    private var purchaseFlowActive = false
    private var setupGeneration = 0
    private var currentPurchaseToken: String? = null
    private val acknowledgements = mutableMapOf<String, Long>()
    private var acknowledgementSequence = 0L
    private var checkoutGeneration = 0L

    @JvmStatic fun hasPro(context: Context): Boolean = synchronized(cacheLock) {
        cachedOwned?.let { return@synchronized it }
        val app = context.applicationContext
        val store = ReceiptStore(app)
        val receipt = store.read()
        val valid = receipt != null && verifyReceipt(app, receipt, expectedToken = null,
            expectedRawState = PurchaseSecurity.RAW_PURCHASE_STATE_PURCHASED)
        if (!valid && receipt != null) store.clear()
        cachedOwned = valid
        valid
    }

    @JvmStatic fun initialize(context: Context) {
        val app = context.applicationContext
        val owned = hasPro(app)
        onMain {
            if (appContext == null) {
                appContext = app
                receiptStore = ReceiptStore(app)
                entitlement = EntitlementPolicy(owned)
                publish(state.copy(owned = owned))
            }
        }
    }

    /** Call when the app returns to the foreground. */
    @JvmStatic fun refresh() = onMain {
        if (appContext != null) refreshInternal(restoring = false)
    }

    @JvmStatic fun restore() = onMain {
        if (appContext != null) refreshInternal(restoring = true)
    }

    @JvmStatic fun launchPurchase(activity: Activity) = onMain {
        if (appContext == null) initialize(activity.applicationContext)
        if (state.owned || state.pending || state.busy || purchaseFlowActive ||
            activity.isFinishing || activity.isDestroyed) return@onMain
        purchaseFlowActive = true
        val checkout = ++checkoutGeneration
        beginBusy()
        withClient { billingClient -> queryProductForPurchase(billingClient, activity, checkout) }
    }

    @JvmStatic fun addListener(listener: Runnable) {
        listeners.add(listener)
    }

    @JvmStatic fun removeListener(listener: Runnable) {
        listeners.remove(listener)
    }

    @JvmStatic fun getState(): State = state

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        onMain {
            finishPurchaseFlow()
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> processPurchaseUpdate(purchases.orEmpty())
                BillingClient.BillingResponseCode.USER_CANCELED -> publish(state.copy(error = null))
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshInternal(restoring = false)
                else -> publish(state.copy(error = errorFor(result, ErrorCode.PURCHASE_FAILED), message = null))
            }
        }
    }

    private fun refreshInternal(restoring: Boolean) {
        // Returning from Play also recovers a checkout whose update callback was lost.
        if (purchaseFlowActive) finishPurchaseFlow()
        if (purchaseQueryInFlight) {
            purchaseQueryAgain = true
            restoreRequested = restoreRequested || restoring
            return
        }
        purchaseQueryInFlight = true
        beginBusy()
        withClient { billingClient -> queryPurchases(billingClient, restoring) }
        if (!state.owned) queryPrice()
    }

    private fun queryPurchases(billingClient: BillingClient, restoring: Boolean) {
        purchaseQueryInFlight = true
        val startedAtRevision = entitlement?.beginQuery() ?: 0
        var completed = false
        val deliver: (BillingResult, List<Purchase>) -> Unit = { result, purchases ->
            if (!completed && client === billingClient) {
                completed = true
                purchaseQueryInFlight = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    processPurchaseQuery(purchases, startedAtRevision, restoring)
                } else {
                    publish(state.copy(error = errorFor(result, ErrorCode.BILLING_UNAVAILABLE), message = null))
                }
                endBusy()
                if (purchaseQueryAgain) {
                    val nextRestore = restoreRequested
                    purchaseQueryAgain = false
                    restoreRequested = false
                    refreshInternal(nextRestore)
                }
            }
        }
        mainHandler.postDelayed({ deliver(networkFailure(), emptyList()) }, 15_000)
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            onMain { deliver(result, purchases) }
        }
    }

    private fun processPurchaseQuery(
        purchases: List<Purchase>,
        startedAtRevision: Long,
        restoring: Boolean
    ) {
        // A complete query describes the instant it started, not a later purchase callback.
        if (entitlement?.beginQuery() != startedAtRevision) return
        val relevant = purchases.filter { PRODUCT_ID in it.products }
        val purchased = relevant.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PURCHASED && verifiedPurchase(it)
        }
        val pending = relevant.any {
            it.purchaseState == Purchase.PurchaseState.PENDING && verifiedPendingPurchase(it)
        }
        val invalid = relevant.any { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> !verifiedPurchase(purchase)
                Purchase.PurchaseState.PENDING -> !verifiedPendingPurchase(purchase)
                else -> false
            }
        }

        val decision = entitlement?.completeSuccessfulQuery(
            startedAtRevision,
            purchased = purchased != null,
            pending = pending,
            resultWasEmpty = relevant.isEmpty()
        ) ?: return
        if (decision.revokeCachedReceipt) clearReceipt()
        if (purchased != null) {
            grantAndCache(purchased, restoring)
            acknowledgeIfNeeded(purchased)
        } else {
            publish(state.copy(
                owned = decision.snapshot.owned,
                pending = decision.snapshot.pending,
                error = if (invalid) ErrorCode.VERIFICATION_FAILED else null,
                message = when {
                    pending -> MessageCode.PURCHASE_PENDING
                    restoring -> MessageCode.RESTORED
                    else -> MessageCode.READY
                }
            ))
            if (!decision.snapshot.owned && state.price == null) queryPrice()
        }
    }

    private fun processPurchaseUpdate(purchases: List<Purchase>) {
        val relevant = purchases.filter { PRODUCT_ID in it.products }
        val purchased = relevant.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PURCHASED && verifiedPurchase(it)
        }
        if (purchased != null) {
            entitlement?.purchaseConfirmed()
            grantAndCache(purchased)
            acknowledgeIfNeeded(purchased)
            return
        }
        if (relevant.any { it.purchaseState == Purchase.PurchaseState.PENDING && verifiedPendingPurchase(it) }) {
            val snapshot = entitlement?.purchasePending() ?: return
            clearReceipt()
            publish(state.copy(owned = snapshot.owned, pending = true, error = null,
                message = MessageCode.PURCHASE_PENDING))
            return
        }
        if (relevant.isNotEmpty()) {
            publish(state.copy(error = ErrorCode.VERIFICATION_FAILED, message = null))
        }
    }

    private fun grantAndCache(purchase: Purchase, restoring: Boolean = false) {
        val snapshot = entitlement?.snapshot() ?: return
        val stored = receiptStore?.write(ReceiptStore.Receipt(purchase.originalJson, purchase.signature)) == true
        // A verified purchase remains usable in this process even if the disk is temporarily full.
        // The UI exposes the persistence error and Play can restore it on the next launch.
        synchronized(cacheLock) { cachedOwned = true }
        currentPurchaseToken = purchase.purchaseToken
        publish(state.copy(
            owned = true,
            pending = false,
            error = if (stored) null else ErrorCode.STORAGE_FAILED,
            message = if (restoring) MessageCode.RESTORED else MessageCode.PURCHASED
        ))
        if (!snapshot.owned) entitlement?.purchaseConfirmed()
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) {
            if (state.error == ErrorCode.ACKNOWLEDGEMENT_FAILED) publish(state.copy(error = null))
            return
        }
        val billingClient = client ?: return
        if (acknowledgements.containsKey(purchase.purchaseToken)) return
        val attempt = ++acknowledgementSequence
        acknowledgements[purchase.purchaseToken] = attempt
        val finish: (BillingResult) -> Unit = { result ->
            if (acknowledgements[purchase.purchaseToken] == attempt) {
                acknowledgements.remove(purchase.purchaseToken)
                if (currentPurchaseToken == purchase.purchaseToken && state.owned) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        publish(state.copy(error = ErrorCode.ACKNOWLEDGEMENT_FAILED))
                    } else if (state.error == ErrorCode.ACKNOWLEDGEMENT_FAILED) {
                        publish(state.copy(error = null))
                    }
                }
            }
        }
        mainHandler.postDelayed({ finish(networkFailure()) }, 15_000)
        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        ) { result ->
            onMain { finish(result) }
        }
    }

    private fun queryPrice() {
        if (priceQueryInFlight) return
        priceQueryInFlight = true
        publish(state.copy(price = null))
        beginBusy()
        withClient { billingClient ->
            queryProductDetails(billingClient) { result, details ->
                priceQueryInFlight = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK && details != null) {
                    val offer = purchaseOffer(details)
                    publish(state.copy(
                        price = offer?.formattedPrice,
                        error = if (state.owned) state.error
                            else if (offer == null) ErrorCode.PRODUCT_UNAVAILABLE else null,
                        message = MessageCode.READY
                    ))
                } else {
                    if (!state.owned) publish(state.copy(price = null,
                        error = errorFor(result, ErrorCode.PRODUCT_UNAVAILABLE)))
                }
                endBusy()
            }
        }
    }

    private fun queryProductForPurchase(billingClient: BillingClient, activity: Activity, checkout: Long) {
        queryProductDetails(billingClient) { result, details ->
            if (checkout != checkoutGeneration) return@queryProductDetails
            if (result.responseCode != BillingClient.BillingResponseCode.OK || details == null) {
                publish(state.copy(error = errorFor(result, ErrorCode.PRODUCT_UNAVAILABLE), message = null))
                finishPurchaseFlow()
                return@queryProductDetails
            }
            val offer = purchaseOffer(details)
            if (offer == null) {
                publish(state.copy(error = ErrorCode.PRODUCT_UNAVAILABLE, message = null))
                finishPurchaseFlow()
                return@queryProductDetails
            }
            if (activity.isFinishing || activity.isDestroyed || !purchaseFlowActive ||
                (activity is LifecycleOwner && !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
                finishPurchaseFlow()
                return@queryProductDetails
            }
            val productBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
            offer.offerToken?.takeIf { it.isNotBlank() }?.let(productBuilder::setOfferToken)
            val flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productBuilder.build()))
                .build()
            val launchResult = billingClient.launchBillingFlow(activity, flow)
            if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                publish(state.copy(error = errorFor(launchResult, ErrorCode.PURCHASE_FAILED), message = null))
                finishPurchaseFlow()
            }
        }
    }

    private fun queryProductDetails(
        billingClient: BillingClient,
        callback: (BillingResult, ProductDetails?) -> Unit
    ) {
        var completed = false
        val deliver: (BillingResult, ProductDetails?) -> Unit = { result, details ->
            if (!completed && client === billingClient) {
                completed = true
                callback(result, details)
            }
        }
        mainHandler.postDelayed({ deliver(networkFailure(), null) }, 15_000)
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ))
            .build()
        billingClient.queryProductDetailsAsync(params, ProductDetailsResponseListener { result, response ->
            onMain { deliver(result, response.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }) }
        })
    }

    private fun purchaseOffer(details: ProductDetails): ProductDetails.OneTimePurchaseOfferDetails? {
        val offers = details.oneTimePurchaseOfferDetailsList.orEmpty()
        if (offers.isNotEmpty()) {
            return offers.firstOrNull { it.purchaseOptionId == PURCHASE_OPTION_ID && it.rentalDetails == null }
        }
        return details.oneTimePurchaseOfferDetails?.takeIf { it.rentalDetails == null }
    }

    private fun verifiedPurchase(purchase: Purchase): Boolean =
        purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            verifyPurchase(purchase, PurchaseSecurity.RAW_PURCHASE_STATE_PURCHASED)

    private fun verifiedPendingPurchase(purchase: Purchase): Boolean =
        purchase.purchaseState == Purchase.PurchaseState.PENDING &&
            verifyPurchase(purchase, PurchaseSecurity.RAW_PURCHASE_STATE_PENDING)

    private fun verifyPurchase(purchase: Purchase, expectedRawState: Int): Boolean {
        val context = appContext ?: return false
        if (purchase.packageName != context.packageName || PRODUCT_ID !in purchase.products ||
            purchase.purchaseToken.isBlank()) return false
        return verifyReceipt(context, ReceiptStore.Receipt(purchase.originalJson, purchase.signature),
            purchase.purchaseToken, expectedRawState)
    }

    private fun clearReceipt() {
        receiptStore?.clear()
        currentPurchaseToken = null
        synchronized(cacheLock) { cachedOwned = false }
    }

    private fun withClient(action: (BillingClient) -> Unit) {
        val billingClient = client ?: buildClient().also { client = it }
        if (billingClient.isReady || setupCompleted) {
            action(billingClient)
            return
        }
        afterSetup.add(action)
        if (connecting) return
        connecting = true
        val generation = ++setupGeneration
        mainHandler.postDelayed({
            if (connecting && generation == setupGeneration) {
                setupGeneration++
                billingClient.endConnection()
                client = null
                setupCompleted = false
                failSetup(ErrorCode.NETWORK)
            }
        }, 15_000)
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                onMain {
                    if (generation != setupGeneration) return@onMain
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        setupCompleted = true
                        while (afterSetup.isNotEmpty()) afterSetup.removeFirst().invoke(billingClient)
                    } else {
                        failSetup(errorFor(result, ErrorCode.BILLING_UNAVAILABLE))
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                onMain {
                    if (client !== billingClient) return@onMain
                    val awaitingAcknowledgement = acknowledgements.isNotEmpty()
                    acknowledgements.clear()
                    publish(state.copy(error = if (awaitingAcknowledgement && state.owned)
                        ErrorCode.ACKNOWLEDGEMENT_FAILED else ErrorCode.BILLING_UNAVAILABLE))
                }
            }
        })
    }

    private fun buildClient(): BillingClient {
        val context = checkNotNull(appContext)
        return BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .enableAutoServiceReconnection()
            .build()
    }

    private fun beginBusy() {
        busyCount++
        publish(state.copy(busy = true, error = null))
    }

    private fun finishPurchaseFlow() {
        if (!purchaseFlowActive) return
        purchaseFlowActive = false
        checkoutGeneration++
        endBusy()
    }

    private fun failSetup(error: ErrorCode) {
        connecting = false
        afterSetup.clear()
        purchaseQueryInFlight = false
        purchaseQueryAgain = false
        restoreRequested = false
        priceQueryInFlight = false
        purchaseFlowActive = false
        checkoutGeneration++
        acknowledgements.clear()
        busyCount = 0
        publish(state.copy(busy = false, price = null, error = error, message = null))
    }

    private fun endBusy() {
        busyCount = (busyCount - 1).coerceAtLeast(0)
        publish(state.copy(busy = busyCount > 0))
    }

    private fun publish(newState: State) {
        val ownedChanged = state.owned != newState.owned
        state = newState
        if (ownedChanged || listeners.isNotEmpty()) {
            listeners.forEach { listener -> runCatching { listener.run() } }
        }
    }

    private fun errorFor(result: BillingResult, fallback: ErrorCode): ErrorCode = when (result.responseCode) {
        BillingClient.BillingResponseCode.NETWORK_ERROR -> ErrorCode.NETWORK
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> ErrorCode.BILLING_UNAVAILABLE
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> ErrorCode.PRODUCT_UNAVAILABLE
        else -> fallback
    }

    private fun networkFailure(): BillingResult = BillingResult.newBuilder()
        .setResponseCode(BillingClient.BillingResponseCode.NETWORK_ERROR).build()

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private fun verifyReceipt(
        context: Context,
        receipt: ReceiptStore.Receipt,
        expectedToken: String?,
        expectedRawState: Int
    ): Boolean = runCatching {
        PurchaseSecurity.verifyReceipt(
            receipt.originalJson,
            Base64.decode(receipt.signature, Base64.DEFAULT),
            Base64.decode(PUBLIC_KEY, Base64.DEFAULT),
            context.packageName,
            PRODUCT_ID,
            expectedToken,
            expectedRawState
        ) != null
    }.getOrDefault(false)

    const val PRODUCT_ID = "battery_widget_pro"
    private const val PURCHASE_OPTION_ID = "buy"
    private const val PUBLIC_KEY =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqmqfy+Cf73xmAybW3PXYcrJvHViyv9NXIBA7u8rh/D4QqB38l0RmdUC5yolRqKCdAWZHHn7R9D5dq8KUvSyLMLQ4FbQKJodAyVuXdtrkrs/6WSUoeFapm2P+zlsYvkZbxNRt2VflNs9ijnV1RxAccPOKFszpAyz8cNG2qx1w3E1v1rkVaPzJEkI/51ZZquYoWAS/tXV4jfkGDsXGix0LzEX3VQ3l6PTV2YMoOlp30jFTYsWIVesLoiDbdvqU/OOSEbxN18hLQKEg2n6epXuF/Ez7dOSD2rauE6zuS2KsyhYT0Mo7WFOkc24yob+NdAIJ5Ekzr02oGXyambdHocrnTwIDAQAB"
}
