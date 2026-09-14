package com.github.xckevin927.android.battery.widget.billing

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature

class PurchaseSecurityTest {
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    @Test fun validPurchasedReceiptPassesAllSignedClaims() {
        val json = receipt(packageName = PACKAGE, product = PRODUCT, token = TOKEN, state = 0)

        val claims = verify(json)

        assertNotNull(claims)
    }

    @Test fun validSignatureCannotAuthorizeWrongPackageProductOrToken() {
        assertNull(verify(receipt("other.package", PRODUCT, TOKEN, 0)))
        assertNull(verify(receipt(PACKAGE, "other_product", TOKEN, 0)))
        assertNull(verify(receipt(PACKAGE, PRODUCT, "other_token", 0)))
    }

    @Test fun pendingRawReceiptCannotBeLoadedAsPurchased() {
        val pending = receipt(PACKAGE, PRODUCT, TOKEN, 4)
        assertNull(verify(pending))
        assertNotNull(PurchaseSecurity.verifyReceipt(pending, sign(pending), keyPair.public.encoded,
            PACKAGE, PRODUCT, TOKEN, PurchaseSecurity.RAW_PURCHASE_STATE_PENDING))
    }

    @Test fun productIdsArrayIsAcceptedForSignedMultiProductPayload() {
        val json =
            """{"packageName":"$PACKAGE","productIds":["other","$PRODUCT"],"purchaseToken":"$TOKEN","purchaseState":0}"""

        assertNotNull(verify(json))
    }

    @Test fun tamperedPayloadAndSignatureAreRejected() {
        val valid = receipt(PACKAGE, PRODUCT, TOKEN, 0)
        val signature = sign(valid)
        val tampered = valid.replace(PRODUCT, "forged_product")

        assertNull(PurchaseSecurity.verifyReceipt(tampered, signature, keyPair.public.encoded,
            PACKAGE, "forged_product", TOKEN))
        assertNull(PurchaseSecurity.verifyReceipt(valid, signature.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() },
            keyPair.public.encoded, PACKAGE, PRODUCT, TOKEN))
    }

    private fun verify(json: String): PurchaseSecurity.Claims? = PurchaseSecurity.verifyReceipt(
        json,
        sign(json),
        keyPair.public.encoded,
        PACKAGE,
        PRODUCT,
        TOKEN
    )

    private fun sign(data: String): ByteArray = Signature.getInstance("SHA1withRSA").run {
        initSign(keyPair.private)
        update(data.toByteArray(StandardCharsets.UTF_8))
        sign()
    }

    private fun receipt(packageName: String, product: String, token: String, state: Int) =
        """{"packageName":"$packageName","productId":"$product","purchaseToken":"$token","purchaseState":$state}"""

    companion object {
        private const val PACKAGE = "com.github.xckevin927.android.battery.widget"
        private const val PRODUCT = "battery_widget_pro"
        private const val TOKEN = "signed-token"
    }
}
