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
        const val PRODUCT_AUTO_BACKUP = "auto_backup_standalone"
        const val PRODUCT_UNLOCK_HUGINN = "huginn_live_dispatch_standalone"
        const val PRODUCT_UNLOCK_ODIN_EYE = "odin_eye_of_mimir_standalone"
        const val PRODUCT_PACK_ANSUZ_100 = "pack_ansuz_100"
        const val PRODUCT_PACK_OTHALA_100 = "pack_othala_100"
        const val PRODUCT_PACK_JERA_10 = "pack_jera_10"
        const val TIP_SMALL = "tip_coffee_099"
        const val TIP_MEDIUM = "tip_lunch_299"
        const val TIP_LARGE = "tip_pizza_499"

        private const val PREFS_NAME = "message_backup_billing_prefs"
        private const val KEY_ADS_REMOVED = "is_ads_removed"
        private const val KEY_AUTO_BACKUP = "is_auto_backup_unlocked"
        private const val KEY_HUGINN_UNLOCKED = "is_huginn_unlocked"
        private const val KEY_ODIN_UNLOCKED = "is_odin_unlocked"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isAdsRemoved = MutableStateFlow(prefs.getBoolean(KEY_ADS_REMOVED, false))
    val isAdsRemoved: StateFlow<Boolean> = _isAdsRemoved.asStateFlow()

    private val _isAutoBackupUnlocked = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_BACKUP, false) || prefs.getBoolean(KEY_ADS_REMOVED, false)
    )
    val isAutoBackupUnlocked: StateFlow<Boolean> = _isAutoBackupUnlocked.asStateFlow()

    private val _isHuginnUnlocked = MutableStateFlow(
        prefs.getBoolean(KEY_HUGINN_UNLOCKED, false) || prefs.getBoolean(KEY_ADS_REMOVED, false)
    )
    val isHuginnUnlocked: StateFlow<Boolean> = _isHuginnUnlocked.asStateFlow()

    private val _isOdinUnlocked = MutableStateFlow(
        prefs.getBoolean(KEY_ODIN_UNLOCKED, false) || prefs.getBoolean(KEY_ADS_REMOVED, false)
    )
    val isOdinUnlocked: StateFlow<Boolean> = _isOdinUnlocked.asStateFlow()

    private val _proProductPrice = MutableStateFlow<String?>("$4.99")
    val proProductPrice: StateFlow<String?> = _proProductPrice.asStateFlow()

    // Backward-compatibility alias
    val productPrice: StateFlow<String?> = _proProductPrice

    private val _autoBackupPrice = MutableStateFlow<String?>("$0.99")
    val autoBackupPrice: StateFlow<String?> = _autoBackupPrice.asStateFlow()

    private val _huginnPrice = MutableStateFlow<String?>("$0.99")
    val huginnPrice: StateFlow<String?> = _huginnPrice.asStateFlow()

    private val _odinEyePrice = MutableStateFlow<String?>("$0.99")
    val odinEyePrice: StateFlow<String?> = _odinEyePrice.asStateFlow()

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
        try {
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
        } catch (_: Exception) {
            // Gracefully ignore on devices without Google Play Services (e.g., Amazon Fire OS)
        }
    }

    fun restorePurchases(onComplete: (Boolean) -> Unit = {}) {
        try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    handlePurchasesList(purchases)
                    val isProActive = _isAdsRemoved.value
                    onComplete(isProActive)
                } else {
                    onComplete(false)
                }
            }
        } catch (_: Exception) {
            onComplete(false)
        }
    }

    private fun queryPurchases() {
        try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    handlePurchasesList(purchases)
                }
            }
        } catch (_: Exception) {
            // Play services not available
        }
    }

    private fun queryProductDetails() {
        val productList = listOf(
            PRODUCT_REMOVE_ADS,
            PRODUCT_AUTO_BACKUP,
            PRODUCT_UNLOCK_HUGINN,
            PRODUCT_UNLOCK_ODIN_EYE,
            PRODUCT_PACK_ANSUZ_100,
            PRODUCT_PACK_OTHALA_100,
            PRODUCT_PACK_JERA_10,
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
                            _proProductPrice.value = it
                        }
                    } else if (details.productId == PRODUCT_AUTO_BACKUP) {
                        details.oneTimePurchaseOfferDetails?.formattedPrice?.let {
                            _autoBackupPrice.value = it
                        }
                    } else if (details.productId == PRODUCT_UNLOCK_HUGINN) {
                        details.oneTimePurchaseOfferDetails?.formattedPrice?.let {
                            _huginnPrice.value = it
                        }
                    } else if (details.productId == PRODUCT_UNLOCK_ODIN_EYE) {
                        details.oneTimePurchaseOfferDetails?.formattedPrice?.let {
                            _odinEyePrice.value = it
                        }
                    }
                }
                _productDetailsMap.value = map
            }
        }
    }

    fun launchBillingFlow(activity: Activity, productId: String = PRODUCT_REMOVE_ADS) {
        try {
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
                val result = billingClient.launchBillingFlow(activity, flowParams)
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    android.widget.Toast.makeText(
                        activity,
                        "Store unavailable on this device. You can earn all perks 100% free in the Arcade!",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                // Re-query in background in case connection was delayed
                queryProductDetails()
                android.widget.Toast.makeText(
                    activity,
                    "Connecting to store... If unavailable, all perks can be earned free in the Arcade!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } catch (_: Exception) {
            android.widget.Toast.makeText(
                activity,
                "Store service not available on this device. All features can be earned free in the Arcade!",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchasesList(purchases)
        }
    }

    private fun handlePurchasesList(purchases: List<Purchase>) {
        val appPrefs = runCatching { com.messagebackup.app.MessageBackupApp.instance.appPreferences }.getOrNull()
        val processedTokens = prefs.getStringSet("processed_consumable_tokens", emptySet())?.toMutableSet() ?: mutableSetOf()

        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                var isNonConsumable = false

                if (purchase.products.contains(PRODUCT_REMOVE_ADS)) {
                    isNonConsumable = true
                    prefs.edit()
                        .putBoolean(KEY_ADS_REMOVED, true)
                        .putBoolean(KEY_AUTO_BACKUP, true)
                        .putBoolean(KEY_HUGINN_UNLOCKED, true)
                        .putBoolean(KEY_ODIN_UNLOCKED, true)
                        .apply()
                    _isAdsRemoved.value = true
                    _isAutoBackupUnlocked.value = true
                    _isHuginnUnlocked.value = true
                    _isOdinUnlocked.value = true
                    appPrefs?.setHuginnPermanentUnlocked(true)
                    appPrefs?.setOdinPermanentUnlocked(true)
                }
                if (purchase.products.contains(PRODUCT_AUTO_BACKUP)) {
                    isNonConsumable = true
                    prefs.edit().putBoolean(KEY_AUTO_BACKUP, true).apply()
                    _isAutoBackupUnlocked.value = true
                }
                if (purchase.products.contains(PRODUCT_UNLOCK_HUGINN)) {
                    isNonConsumable = true
                    prefs.edit().putBoolean(KEY_HUGINN_UNLOCKED, true).apply()
                    _isHuginnUnlocked.value = true
                    appPrefs?.setHuginnPermanentUnlocked(true)
                }
                if (purchase.products.contains(PRODUCT_UNLOCK_ODIN_EYE)) {
                    isNonConsumable = true
                    prefs.edit().putBoolean(KEY_ODIN_UNLOCKED, true).apply()
                    _isOdinUnlocked.value = true
                    appPrefs?.setOdinPermanentUnlocked(true)
                }

                if (isNonConsumable) {
                    if (!purchase.isAcknowledged) {
                        acknowledgeNonConsumablePurchase(purchase)
                    }
                } else {
                    // Consumable packs & tips: consume and grant once per purchaseToken
                    val token = purchase.purchaseToken
                    val alreadyGranted = processedTokens.contains(token)
                    consumePurchase(purchase) {
                        if (!alreadyGranted) {
                            processedTokens.add(token)
                            prefs.edit().putStringSet("processed_consumable_tokens", processedTokens).apply()
                            when {
                                purchase.products.contains(PRODUCT_PACK_ANSUZ_100) -> appPrefs?.addAnsuzCredits(100)
                                purchase.products.contains(PRODUCT_PACK_OTHALA_100) -> appPrefs?.addAiCredits(100)
                                purchase.products.contains(PRODUCT_PACK_JERA_10) -> appPrefs?.addAutoBackupCredits(10)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun acknowledgeNonConsumablePurchase(purchase: Purchase) {
        val params = com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { _ -> }
    }

    private fun consumePurchase(purchase: Purchase, onConsumedSuccess: () -> Unit = {}) {
        val consumeParams = com.android.billingclient.api.ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { result, _ ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onConsumedSuccess()
            }
        }
    }

    /**
     * Secret Developer License Activation.
     * Compares the salted SHA-256 hash of the entered key so the actual secret key is NEVER stored in plaintext
     * and cannot be decompiled or extracted from the APK.
     */
    fun unlockDeveloperPro(enteredKey: String): Boolean {
        val trimmed = enteredKey.trim()
        if (trimmed.isEmpty()) return false

        try {
            // Salt prevents dictionary/rainbow table lookups against compiled strings
            val salt = "Muninn-DevMasterSalt-2026#"
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest((salt + trimmed).toByteArray(Charsets.UTF_8))
            val hashString = digest.joinToString("") { "%02x".format(it) }

            // Target salted SHA-256 hash for developer master key
            // Pre-computed hash of ("Muninn-DevMasterSalt-2026#" + "NfgOdin-DevMaster-2026-Vault")
            // Plaintext is NEVER stored anywhere in the bytecode
            val targetSaltedHash = "49f3e718629b08d788402d99224762ec0aac2ea67b9b07b67ef6b75e68c1bec4"

            if (hashString.equals(targetSaltedHash, ignoreCase = true)) {
                prefs.edit()
                    .putBoolean(KEY_ADS_REMOVED, true)
                    .putBoolean(KEY_AUTO_BACKUP, true)
                    .putBoolean(KEY_HUGINN_UNLOCKED, true)
                    .putBoolean(KEY_ODIN_UNLOCKED, true)
                    .apply()
                _isAdsRemoved.value = true
                _isAutoBackupUnlocked.value = true
                _isHuginnUnlocked.value = true
                _isOdinUnlocked.value = true
                runCatching {
                    val appPrefs = com.messagebackup.app.MessageBackupApp.instance.appPreferences
                    appPrefs.setHuginnPermanentUnlocked(true)
                    appPrefs.setOdinPermanentUnlocked(true)
                }
                return true
            }
        } catch (e: Exception) {
            // Ignore security exception
        }
        return false
    }

    /**
     * Resets Pro license back to Free tier for testing ad and credit mechanics.
     */
    fun resetDeveloperPro() {
        prefs.edit()
            .putBoolean(KEY_ADS_REMOVED, false)
            .putBoolean(KEY_AUTO_BACKUP, false)
            .putBoolean(KEY_HUGINN_UNLOCKED, false)
            .putBoolean(KEY_ODIN_UNLOCKED, false)
            .apply()
        _isAdsRemoved.value = false
        _isAutoBackupUnlocked.value = false
        _isHuginnUnlocked.value = false
        _isOdinUnlocked.value = false
        runCatching {
            val appPrefs = com.messagebackup.app.MessageBackupApp.instance.appPreferences
            appPrefs.setHuginnPermanentUnlocked(false)
            appPrefs.setOdinPermanentUnlocked(false)
        }
    }
}
