package com.github.xckevin927.android.battery.widget

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.github.xckevin927.android.battery.widget.billing.ProBilling
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Explicit opt-in, read-only Play diagnostics. Never buys, acknowledges, or changes receipts. */
@RunWith(AndroidJUnit4::class)
class LiveBillingDiagnosticsTest {
    private lateinit var client: BillingClient

    @Test fun inspectCatalogAndPurchases() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveBilling") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val setup = CompletableFuture<BillingResult>()
        val productQuery = CompletableFuture<JSONObject>()
        val purchaseQuery = CompletableFuture<JSONObject>()
        fun emit(value: JSONObject) {
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "\nLIVE_BILLING $value\n")
            })
        }
        fun resultJson(stage: String, result: BillingResult) = JSONObject()
            .put("stage", stage).put("responseCode", result.responseCode)
            .put("debugMessage", result.debugMessage.take(500))
        try {
            instrumentation.runOnMainSync {
                client = BillingClient.newBuilder(instrumentation.targetContext)
                    .setListener { _, _ -> }
                    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                    .enableAutoServiceReconnection()
                    .build()
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) { setup.complete(result) }
                    override fun onBillingServiceDisconnected() = Unit
                })
            }
            val connected = setup.get(30, TimeUnit.SECONDS)
            emit(resultJson("setup", connected))
            assertEquals(BillingClient.BillingResponseCode.OK, connected.responseCode)
            instrumentation.runOnMainSync {
                client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder()
                    .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(ProBilling.PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()))
                    .build()) { result, response ->
                    val products = JSONArray()
                    response.productDetailsList.forEach { product ->
                        val offers = JSONArray()
                        product.oneTimePurchaseOfferDetailsList.orEmpty().forEach { offer ->
                            offers.put(JSONObject().put("optionId", offer.purchaseOptionId ?: JSONObject.NULL)
                                .put("price", offer.formattedPrice).put("currency", offer.priceCurrencyCode)
                                .put("rental", offer.rentalDetails != null)
                                .put("hasOfferToken", !offer.offerToken.isNullOrBlank()))
                        }
                        products.put(JSONObject().put("productId", product.productId).put("offers", offers)
                            .put("legacyPrice", product.oneTimePurchaseOfferDetails?.formattedPrice ?: JSONObject.NULL))
                    }
                    val unfetched = JSONArray()
                    response.unfetchedProductList.forEach { product ->
                        unfetched.put(JSONObject().put("productId", product.productId).put("statusCode", product.statusCode))
                    }
                    productQuery.complete(resultJson("products", result).put("products", products).put("unfetched", unfetched))
                }
                client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
                    val relevant = JSONArray()
                    purchases.filter { ProBilling.PRODUCT_ID in it.products }.forEach { purchase ->
                        relevant.put(JSONObject().put("state", purchase.purchaseState).put("acknowledged", purchase.isAcknowledged))
                    }
                    purchaseQuery.complete(resultJson("purchases", result).put("purchases", relevant))
                }
            }
            // An empty/unfetched result is diagnostic output, not proof that the purchase flow passed.
            emit(productQuery.get(30, TimeUnit.SECONDS))
            emit(purchaseQuery.get(30, TimeUnit.SECONDS))
        } finally {
            if (::client.isInitialized) instrumentation.runOnMainSync { client.endConnection() }
        }
    }
}
