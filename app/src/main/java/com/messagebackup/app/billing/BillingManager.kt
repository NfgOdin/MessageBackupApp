package com.messagebackup.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages Google Play In-App Billing for:
 * 1. Non-consumable "Remove Ads Forever" product.
 * 2. Consumable Tip Jar donations ($0.99, $2.99, $4.99).
 */
class BillingManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_REMOVE_ADS = "remove_ads_permanent"
        const val TIP_SMALL = "tip_coffee_099"
        const val TIP_MEDIUM = "tip_lunch_299"
        const val TIP_LARGE = "tip_pizza_499"

        private const val PREFS_NAME = "message_backup_billing_prefs"
        private const val KEY_ADS_REMOVED = "is_ads_removed"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isAdsRemoved = MutableStateFlow(prefs.getBoolean(KEY_ADS_REMOVED, false))
    val isAdsRemoved: StateFlow<Boolean> = _isAdsRemoved.asStateFlow()

    private val _productPrice = MutableStateFlow<String?>("$4.99")
    val productPrice: StateFlow<String?> = _productPrice.asStateFlow()

    private val _productDetailsMap = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetailsMap: StateFlow<Map<String, ProductDetails>> = _productDetailsMap.asStateFlow()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    init {
        startConnection()
    }

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                    queryProductDetails()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retry connection on next request
            }
        })
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchasesList(purchases)
            }
        }
    }

    private fun queryProductDetails() {
        val productList = listOf(
            PRODUCT_REMOVE_ADS,
            TIP_SMALL,
            TIP_MEDIUM,
            TIP_LARGE
        ).map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val map = mutableMapOf<String, ProductDetails>()
                for (details in productDetailsList) {
                    map[details.productId] = details
                    if (details.productId == PRODUCT_REMOVE_ADS) {
                        details.oneTimePurchaseOfferDetails?.formattedPrice?.let {
                            _productPrice.value = it
                        }
                    }
                }
                _productDetailsMap.value = map
            }
        }
    }

    fun launchBillingFlow(activity: Activity, productId: String = PRODUCT_REMOVE_ADS) {
        val details = _productDetailsMap.value[productId]
        if (details != null) {
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build()
                    )
                )
                .build()
            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchasesList(purchases)
        }
    }

    private fun handlePurchasesList(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (purchase.products.contains(PRODUCT_REMOVE_ADS)) {
                    prefs.edit().putBoolean(KEY_ADS_REMOVED, true).apply()
                    _isAdsRemoved.value = true
                } else {
                    // Consumable tip - consume so user can donate again if desired
                    consumePurchase(purchase)
                }
            }
        }
    }

    private fun consumePurchase(purchase: Purchase) {
        val consumeParams = com.android.billingclient.api.ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { _, _ -> }
    }
}
