package com.github.xckevin927.android.battery.widget.billing

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Cryptographic and signed-claim verification for the exact payload returned by Google Play. */
internal object PurchaseSecurity {
    data class Claims(
        val packageName: String,
        val products: Set<String>,
        val purchaseToken: String,
        val rawPurchaseState: Int
    )

    fun verifyReceipt(
        originalJson: String,
        signatureBytes: ByteArray,
        publicKeyBytes: ByteArray,
        expectedPackage: String,
        expectedProduct: String,
        expectedToken: String? = null,
        expectedRawPurchaseState: Int = RAW_PURCHASE_STATE_PURCHASED
    ): Claims? {
        if (!verifySignature(originalJson, signatureBytes, publicKeyBytes)) return null
        val json = runCatching { JSONObject(originalJson) }.getOrNull() ?: return null
        val packageName = json.optString("packageName")
        val token = json.optString("purchaseToken").ifBlank { json.optString("token") }
        val purchaseState = json.optInt("purchaseState", Int.MIN_VALUE)
        val products = buildSet {
            json.optString("productId").takeIf(String::isNotBlank)?.let(::add)
            listOf("productIds", "products").forEach { key ->
                val array = json.optJSONArray(key) ?: return@forEach
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }
        if (purchaseState != expectedRawPurchaseState) return null
        if (packageName != expectedPackage || expectedProduct !in products || token.isBlank()) return null
        if (expectedToken != null && token != expectedToken) return null
        return Claims(packageName, products, token, purchaseState)
    }

    fun verifySignature(data: String, signatureBytes: ByteArray, publicKeyBytes: ByteArray): Boolean =
        runCatching {
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(publicKeyBytes))
            Signature.getInstance("SHA1withRSA").run {
                initVerify(publicKey)
                update(data.toByteArray(StandardCharsets.UTF_8))
                verify(signatureBytes)
            }
        }.getOrDefault(false)

    const val RAW_PURCHASE_STATE_PURCHASED = 0
    const val RAW_PURCHASE_STATE_PENDING = 4
}
