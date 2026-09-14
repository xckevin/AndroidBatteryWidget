package com.github.xckevin927.android.battery.widget.activity

import android.graphics.Typeface
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import com.github.xckevin927.android.battery.widget.R
import com.github.xckevin927.android.battery.widget.billing.ProBilling
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** Describes and sells the single, non-consumable Pro entitlement. */
class ProActivity : BaseActivity() {
    private lateinit var statusText: TextView
    private lateinit var priceText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var purchaseButton: MaterialButton
    private lateinit var restoreButton: MaterialButton

    private val billingListener = Runnable {
        if (!isFinishing && !isDestroyed) renderBillingState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
        title = getString(R.string.pro_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        purchaseButton.setOnClickListener {
            val state = ProBilling.getState()
            if (canPurchase(state)) ProBilling.launchPurchase(this)
        }
        restoreButton.setOnClickListener {
            if (!ProBilling.getState().busy) ProBilling.restore()
        }
        renderBillingState()
    }

    override fun onStart() {
        super.onStart()
        ProBilling.addListener(billingListener)
        ProBilling.initialize(applicationContext)
        renderBillingState()
    }

    override fun onStop() {
        ProBilling.removeListener(billingListener)
        super.onStop()
    }

    private fun renderBillingState() {
        if (!::statusText.isInitialized) return
        val state = ProBilling.getState()
        val owned = ProBilling.hasPro(applicationContext)

        progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        statusText.setText(statusTextFor(state, owned))
        priceText.text = state.price
            ?.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.pro_price_value, it) }
            ?: getString(R.string.pro_price_unavailable)
        priceText.visibility = if (owned) View.GONE else View.VISIBLE

        purchaseButton.text = state.price
            ?.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.pro_buy_for_price, it) }
            ?: getString(R.string.pro_buy)
        purchaseButton.isEnabled = canPurchase(state)
        purchaseButton.visibility = if (owned) View.GONE else View.VISIBLE

        // Restore remains the recovery action when Play is offline or the product is unavailable.
        restoreButton.isEnabled = !state.busy
        restoreButton.visibility = View.VISIBLE
    }

    private fun canPurchase(state: ProBilling.State): Boolean {
        return !ProBilling.hasPro(applicationContext) &&
            !state.pending &&
            !state.busy &&
            state.error == null &&
            !state.price.isNullOrBlank()
    }

    private fun statusTextFor(state: ProBilling.State, owned: Boolean): Int {
        if (owned) {
            return when {
                state.error == ProBilling.ErrorCode.ACKNOWLEDGEMENT_FAILED ->
                    R.string.pro_status_acknowledgement_failed
                state.error == ProBilling.ErrorCode.STORAGE_FAILED ->
                    R.string.pro_status_storage_failed
                state.error != null -> R.string.pro_status_unlocked_offline
                state.message == ProBilling.MessageCode.RESTORED -> R.string.pro_status_restored
                else -> R.string.pro_status_unlocked
            }
        }
        if (state.pending) return R.string.pro_status_pending
        if (state.busy) return R.string.pro_status_checking
        return when (state.error) {
            ProBilling.ErrorCode.BILLING_UNAVAILABLE -> R.string.pro_status_billing_unavailable
            ProBilling.ErrorCode.NETWORK -> R.string.pro_status_network_error
            ProBilling.ErrorCode.PRODUCT_UNAVAILABLE -> R.string.pro_status_product_unavailable
            ProBilling.ErrorCode.PURCHASE_FAILED -> R.string.pro_status_purchase_failed
            ProBilling.ErrorCode.VERIFICATION_FAILED -> R.string.pro_status_verification_failed
            ProBilling.ErrorCode.ACKNOWLEDGEMENT_FAILED ->
                R.string.pro_status_acknowledgement_failed
            ProBilling.ErrorCode.STORAGE_FAILED -> R.string.pro_status_storage_failed
            null -> when {
                state.message == ProBilling.MessageCode.RESTORED ->
                    R.string.pro_status_no_purchase_found
                !state.price.isNullOrBlank() -> R.string.pro_status_ready
                else -> R.string.pro_status_unavailable
            }
        }
    }

    private fun createContentView(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }

        page.addView(card(R.color.ui_primary_container) {
            addView(heading(R.string.pro_title, 26f))
            addView(body(R.string.settings_ui_pro_value_title).withTopMargin(dp(8)))
            addView(body(R.string.pro_intro).withTopMargin(dp(4)))
        })

        page.addView(card {
            addView(heading(R.string.settings_ui_pro_included_title, 18f))
            addView(feature(R.string.pro_combined_widget_title, R.string.pro_combined_widget_summary))
            addView(feature(R.string.pro_alerts_feature_title, R.string.pro_alerts_feature_summary))
        })

        page.addView(card {
            addView(heading(R.string.settings_ui_pro_free_title, 18f))
            addView(body(R.string.pro_free_summary))
        })

        page.addView(card {
            addView(heading(R.string.pro_purchase_status_heading, 18f))
            statusText = body(R.string.pro_status_checking).apply {
                id = R.id.pro_status
                ViewCompat.setAccessibilityLiveRegion(
                    this,
                    ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE
                )
            }
            addView(statusText)

            progress = ProgressBar(this@ProActivity).apply {
                isIndeterminate = true
                contentDescription = getString(R.string.pro_status_checking)
            }
            addView(progress, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                topMargin = dp(12)
            })

            priceText = body(R.string.pro_price_unavailable).apply {
                id = R.id.pro_price
            }
            addView(priceText.withTopMargin(dp(12)))
            addView(body(R.string.pro_purchase_terms).withTopMargin(dp(8)))

            purchaseButton = MaterialButton(this@ProActivity).apply {
                id = R.id.pro_purchase
                applyPrimaryButtonAppearance()
                text = getString(R.string.pro_buy)
            }
            addView(purchaseButton.fullWidthButton(topMargin = dp(16)))

            restoreButton = MaterialButton(
                this@ProActivity,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                id = R.id.pro_restore
                applyOutlinedButtonAppearance()
                text = getString(R.string.pro_restore)
            }
            addView(restoreButton.fullWidthButton(topMargin = dp(8), minimumHeight = dp(48)))
        })

        return NestedScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(ContextCompat.getColor(this@ProActivity, R.color.ui_background))
            addView(
                page,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun card(
        backgroundColor: Int = R.color.ui_surface,
        content: LinearLayout.() -> Unit
    ): MaterialCardView {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            content()
        }
        return MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(this@ProActivity, R.color.ui_outline)
            setCardBackgroundColor(ContextCompat.getColor(this@ProActivity, backgroundColor))
            cardElevation = 0f
            addView(
                column,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
        }
    }

    private fun feature(titleRes: Int, summaryRes: Int): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@ProActivity).apply {
                setText(titleRes)
                setTextAppearance(this@ProActivity, R.style.TextAppearance_Battery_Body)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@ProActivity, R.color.ui_text))
            })
            addView(body(summaryRes).withTopMargin(dp(2)))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }
    }

    private fun heading(textRes: Int, sizeSp: Float): TextView {
        return TextView(this).apply {
            setText(textRes)
            setTextAppearance(
                this@ProActivity,
                if (sizeSp >= 24f) {
                    R.style.TextAppearance_Battery_Title
                } else {
                    R.style.TextAppearance_Battery_Section
                }
            )
            setTextColor(ContextCompat.getColor(this@ProActivity, R.color.ui_text))
            ViewCompat.setAccessibilityHeading(this, true)
        }
    }

    private fun body(textRes: Int): TextView {
        return TextView(this).apply {
            setText(textRes)
            setTextAppearance(this@ProActivity, R.style.TextAppearance_Battery_Body)
            setTextColor(ContextCompat.getColor(this@ProActivity, R.color.ui_text_secondary))
        }
    }

    private fun <T : View> T.withTopMargin(margin: Int): T {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = margin }
        return this
    }

    private fun <T : View> T.withBottomMargin(margin: Int): T {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = margin }
        return this
    }

    private fun MaterialButton.fullWidthButton(
        topMargin: Int,
        minimumHeight: Int = dp(52)
    ): MaterialButton {
        minHeight = minimumHeight
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { this.topMargin = topMargin }
        return this
    }

    private fun MaterialButton.applyPrimaryButtonAppearance() {
        minHeight = dp(52)
        cornerRadius = dp(16)
        // Keep the theme's disabled-state tint when Play cannot offer a purchase.
    }

    private fun MaterialButton.applyOutlinedButtonAppearance() {
        minHeight = dp(48)
        cornerRadius = dp(16)
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(
            ContextCompat.getColor(this@ProActivity, R.color.ui_outline)
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
