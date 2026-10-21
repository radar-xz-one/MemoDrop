package com.memodrop

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*

class BillingManager(private val activity: Activity, private val onAdsRemovedChanged: (Boolean) -> Unit, private val onPrice: (String?) -> Unit) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID = "remove_ads"
        private const val PREFS = "MemoDrop"
        private const val KEY = "ads_removed"
        fun isAdsRemoved(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    private val client = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()
    private var details: ProductDetails? = null

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                queryDetails()
                queryOwned()
            }

            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { _, res ->
            details = res.productDetailsList.firstOrNull()
            activity.runOnUiThread { onPrice(details?.oneTimePurchaseOfferDetails?.formattedPrice) }
        }
    }

    fun queryOwned() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases, fromQuery = true)
        }
    }

    fun purchase() {
        val d = details ?: run { start(); return }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) handle(purchases, false)
        else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) queryOwned()
    }

    private fun handle(purchases: List<Purchase>, fromQuery: Boolean) {
        val owned = purchases.filter { it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        owned.filter { !it.isAcknowledged }.forEach {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()) {}
        }
        if (fromQuery || owned.isNotEmpty()) {
            val removed = owned.isNotEmpty()
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, removed).apply()
            activity.runOnUiThread { onAdsRemovedChanged(removed) }
        }
    }

    fun end() = client.endConnection()
}
