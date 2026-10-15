package com.anish1050.hires.billing

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
import com.anish1050.hires.data.api.HiResApi
import com.anish1050.hires.data.repo.EntitlementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** One purchasable plan as the paywall shows it. */
data class Offer(val basePlanId: String, val offerToken: String, val price: String, val billingPeriod: String, val hasFreeTrial: Boolean)

/**
 * Google Play subscription `premium` with base plans `monthly` and `annual`
 * and a 7-day trial offer. The server verifies and acknowledges purchases;
 * the app only starts the flow and reports the token.
 */
class PlayBilling(context: Context, private val api: HiResApi, private val entitlements: EntitlementRepository, private val scope: CoroutineScope) : PurchasesUpdatedListener {
    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private var premium: ProductDetails? = null
    private val _offers = MutableStateFlow<List<Offer>>(emptyList())
    val offers: StateFlow<List<Offer>> = _offers

    suspend fun loadOffers() {
        if (!connect()) return
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build(),
        )).build()
        premium = suspendCancellableCoroutine { continuation ->
            client.queryProductDetailsAsync(params) { _, result -> continuation.resume(result.productDetailsList.firstOrNull()) }
        }
        _offers.value = premium?.subscriptionOfferDetails.orEmpty()
            .groupBy { it.basePlanId }
            // Show the trial offer for a base plan when this user is eligible for it, else the plain plan.
            .map { (_, offers) -> offers.firstOrNull { it.offerId != null } ?: offers.first() }
            .map { details ->
                val phases = details.pricingPhases.pricingPhaseList
                val paid = phases.last()
                Offer(details.basePlanId, details.offerToken, paid.formattedPrice, paid.billingPeriod, hasFreeTrial = phases.any { it.priceAmountMicros == 0L })
            }
            .sortedBy { if (it.basePlanId == "annual") 0 else 1 }
    }

    /** The account id lets the server refuse a purchase token replayed onto another account. */
    fun buy(activity: Activity, offer: Offer, userId: String) {
        val product = premium ?: return
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offer.offerToken).build()
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).setObfuscatedAccountId(userId).build())
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) return
        purchases.orEmpty().filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { purchase ->
            scope.launch { runCatching { entitlements.store(api.verifyPurchase(purchase.purchaseToken)) } }
        }
    }

    suspend fun restore() {
        if (!connect()) return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        val purchases = suspendCancellableCoroutine { continuation -> client.queryPurchasesAsync(params) { _, list -> continuation.resume(list) } }
        purchases.forEach { runCatching { entitlements.store(api.verifyPurchase(it.purchaseToken)) } }
        runCatching { entitlements.refresh() }
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
    }

    private companion object {
        const val PRODUCT_ID = "premium"
    }
}
