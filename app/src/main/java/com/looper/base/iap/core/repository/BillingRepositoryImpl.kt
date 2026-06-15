package com.looper.base.iap.core.repository

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.looper.base.R
import com.looper.base.iap.core.data.BillingState
import com.looper.base.iap.core.data.MONTHLY_KEY
import com.looper.base.iap.core.data.OfferSale
import com.looper.base.iap.core.data.PackageType
import com.looper.base.iap.core.data.PriceOffer
import com.looper.base.iap.core.data.ProductDetailItem
import com.looper.base.iap.core.data.PurchaseResult
import com.looper.base.iap.core.data.SubscriptionType
import com.looper.base.iap.core.data.packageInfoItems
import com.looper.base.iap.core.wrapper.BillingWrapperImpl
import com.looper.base.iap.core.wrapper.IBillingWrapper
import com.looper.base.iap.ui.data.StartIAPType
import com.looper.base.iap.utils.TRIAL
import com.looper.base.iap.utils.extractDays
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MAX_CURRENT_PURCHASES_ALLOWED = 1

class BillingRepositoryImpl(private val context: Context) : IBillingRepository {

    private val clientWrapper: IBillingWrapper = BillingWrapperImpl(context = context)

    override val state: Flow<BillingState> = clientWrapper.billingState

    override val productDetailItems: Flow<List<ProductDetailItem>?> =
        clientWrapper.productDetailsMap.map {
            it?.values?.toList()?.toProductDetailItems()
        }

    override val purchaseInApp: Flow<List<Purchase?>?> = clientWrapper.purchaseInApps

    override val purchaseSubs: Flow<List<Purchase?>?> = clientWrapper.purchaseSubscriptions

    override val purchaseResult: Flow<PurchaseResult?> = clientWrapper.purchaseResult

    private var _itemPurchase: Pair<StartIAPType, ProductDetailItem>? = null
    override val itemPurchase: Pair<StartIAPType, ProductDetailItem>?
        get() = _itemPurchase

    private val scope = MainScope()

    init {
        startConnection()
    }

    override fun startConnection() = clientWrapper.startConnection()

    override fun stopConnection() = clientWrapper.stopConnection()

    override fun reConnection() = clientWrapper.reConnection()

    override fun refreshPurchase() = clientWrapper.queryPurchase()

    override fun buy(
        activity: Activity,
        startBuy: StartIAPType,
        item: ProductDetailItem
    ) {
        setItemPurchaseSelected(startBuy = startBuy, item = item)

        scope.launch {
            val offerToken = item.subsOfferDetails?.offerToken

            val oldPurchaseToken: String

            val purchaseSubs = purchaseSubs.firstOrNull() ?: emptyList()

            val currentPurchase = purchaseSubs.firstOrNull()

            if (purchaseSubs.isNotEmpty() && currentPurchase != null && purchaseSubs.size == MAX_CURRENT_PURCHASES_ALLOWED) {
                oldPurchaseToken = currentPurchase.purchaseToken

                val billingParams = if (offerToken != null)
                    upDowngradeBillingFlowParamsBuilder(
                        productDetails = item.productDetails,
                        offerToken = offerToken,
                        oldToken = oldPurchaseToken
                    )
                else {
                    billingFlowParamsBuilder(
                        productDetails = item.productDetails,
                        offerToken = null
                    )
                }

                if (billingParams != null) {
                    clientWrapper.buy(
                        activity = activity,
                        params = billingParams,
                    )
                } else {
                    showToastError(activity = activity)
                }
            } else if (purchaseSubs.isEmpty()) {
                val billingParams = billingFlowParamsBuilder(
                    productDetails = item.productDetails,
                    offerToken = offerToken
                )

                if (billingParams != null) {
                    clientWrapper.buy(
                        activity = activity,
                        params = billingParams
                    )
                } else {
                    showToastError(activity = activity)
                }
            }
        }
    }

    override fun setItemPurchaseSelected(startBuy: StartIAPType?, item: ProductDetailItem?) {
        _itemPurchase = if (startBuy != null && item != null) {
            startBuy to item
        } else {
            null
        }
    }

    override suspend fun queryProductDetails(
        productId: String,
        packageType: String
    ): ProductDetails? {
        return clientWrapper.queryProductDetails(productId = productId, productType = packageType)
    }

    override suspend fun getProductDetailItem(packageType: PackageType): ProductDetailItem? {
        val productDetails = productDetailItems.firstOrNull()

        val productDetailItem = productDetails?.find {
            it.packageType == packageType
        }

        return productDetailItem
    }

    private fun List<ProductDetails>.toProductDetailItems(): List<ProductDetailItem> {
        val temps = mutableListOf<ProductDetailItem>()
        val tempInApps = mutableListOf<ProductDetailItem>()

        this.forEach { productDetails ->
            if (productDetails.productType == BillingClient.ProductType.SUBS) {
                temps.addAll(productDetails.findSubscriptionPackage(productDetails = productDetails))
            } else {
                tempInApps.add(productDetails.findInAppPackage())
            }
        }

        val inAppUpdates = tempInApps.updateSalePackage()

        temps.addAll(inAppUpdates)

        return temps.distinctBy {
            it.packageType
        }
    }

    private fun ProductDetails.findSubscriptionPackage(productDetails: ProductDetails): List<ProductDetailItem> {
        val subscriptionsOfferDetails = this.subscriptionOfferDetails ?: return emptyList()

        val weeks = subscriptionsOfferDetails
            .findSubscription(
                type = SubscriptionType.Weekly,
                productDetails = productDetails
            )

        val months = subscriptionsOfferDetails
            .findSubscription(
                type = SubscriptionType.Monthly,
                productDetails = productDetails
            )
            .updateSaveOff(subWeeks = weeks)

        val years = subscriptionsOfferDetails
            .findSubscription(
                type = SubscriptionType.Yearly,
                productDetails = productDetails
            ).updateSaveOff(subWeeks = weeks)

        return mutableListOf<ProductDetailItem>().apply {
            addAll(months)
            addAll(weeks)
            addAll(years)
        }
    }

    private fun ProductDetails.findInAppPackage(): ProductDetailItem {
        val productDetails = this

        val price = productDetails.oneTimePurchaseOfferDetails?.priceAmountMicros ?: 0L
        val priceStr = productDetails.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
        val priceCurrencyCode =
            productDetails.oneTimePurchaseOfferDetails?.priceCurrencyCode ?: "US"

        val packageInfo = packageInfoItems.find {
            productDetails.productId == it.id
        }

        val packageType = packageInfo?.packageType ?: PackageType.Lifetime

        return ProductDetailItem(
            offerNameId = R.string.lifetime,
            planId = productDetails.productId,
            productDetails = productDetails,
            packageType = packageType,
            price = price,
            originalPrice = price,
            priceStr = priceStr,
            originalPriceStr = priceStr,
            priceCurrencyCode = priceCurrencyCode
        )
    }

    private fun List<ProductDetails.SubscriptionOfferDetails>.findSubscription(
        type: SubscriptionType,
        productDetails: ProductDetails
    ): List<ProductDetailItem> {

        val list = this.retrieveEligibleOffers(tags = type.key)

        return list.map { subsOfferDetails ->

            val freeTrialItem = subsOfferDetails.getTrialOffer()

            val freeTrialDay = freeTrialItem?.pricingPhases?.pricingPhaseList?.getFreeTrialDay()

            val price = subsOfferDetails.getPrice()

            val isFreeTrial = freeTrialDay != null

            val packageType = if (freeTrialItem != null && isFreeTrial) {
                when (type) {
                    SubscriptionType.Monthly -> PackageType.MonthlyTrial

                    SubscriptionType.Weekly -> PackageType.WeeklyTrial
                    SubscriptionType.Yearly -> PackageType.YearlyTrial
                }
            } else {
                subsOfferDetails.getSubsPackageType(subType = type)
            }

            val offerNameId = when (type) {
                SubscriptionType.Monthly -> R.string.monthly
                SubscriptionType.Weekly -> R.string.weekly
                SubscriptionType.Yearly -> R.string.txt_yearly
            }

            ProductDetailItem(
                offerNameId = offerNameId,
                planId = subsOfferDetails.basePlanId,
                productDetails = productDetails,
                subsOfferDetails = subsOfferDetails,
                packageType = packageType,
                saleOff = price.saleOff,
                freeTrialDay = if (isFreeTrial) freeTrialDay else null,
                price = price.lowestPrice,
                originalPrice = price.originalPrice,
                priceStr = price.lowestPriceStr,
                originalPriceStr = price.originalPriceStr,
                priceCurrencyCode = price.priceCurrencyCode
            )
        }
    }

    private fun List<ProductDetailItem>.updateSalePackage(): List<ProductDetailItem> {
        val lifetime = find { it.packageType == PackageType.Lifetime } ?: return this

        return this.map { productDetailItem ->
            productDetailItem.packageType.promoSaleKey ?: return@map productDetailItem

            val sale = 100f - (productDetailItem.price * 100f / lifetime.originalPrice)

            productDetailItem.copy(
                saleOff = sale.roundToInt(),
                originalPrice = lifetime.originalPrice,
                originalPriceStr = lifetime.originalPriceStr
            )
        }
    }

    private fun ProductDetails.SubscriptionOfferDetails.getSubsPackageType(subType: SubscriptionType): PackageType {
        val offerId = this.offerId

        var packageType = subType.packageTypeDefault

        if (offerId != null) {
            val saleType = OfferSale.findPackageTypeById(id = offerId)
            if (saleType != null) {
                packageType = saleType
            }
        }

        return packageType
    }

    private fun List<ProductDetailItem>.updateSaveOff(subWeeks: List<ProductDetailItem>): List<ProductDetailItem> {
        if (subWeeks.isEmpty()) return this

        val originalPriceWeek = subWeeks.firstOrNull()?.originalPrice

        return map {
            val weekCount = if (it.packageType.key == MONTHLY_KEY)
                4
            else
                52

            it.copy(
                saveOff = if (originalPriceWeek != null) {
                    (100f - (it.originalPrice * 100f / (originalPriceWeek * weekCount))).toInt()
                } else {
                    0
                }
            )
        }
    }

    private fun List<ProductDetails.SubscriptionOfferDetails>.retrieveEligibleOffers(tags: String): List<ProductDetails.SubscriptionOfferDetails> {
        val eligibleOffers = mutableListOf<ProductDetails.SubscriptionOfferDetails>()

        this.forEach { offerDetail ->
            if (offerDetail.basePlanId.contains(tags)) {
                eligibleOffers.add(offerDetail)
            }
        }

        return eligibleOffers
    }

    private fun List<ProductDetails.PricingPhase>?.getFreeTrialDay(): Int? {
        val pricingPhase = this?.firstOrNull {
            it.recurrenceMode == ProductDetails.RecurrenceMode.FINITE_RECURRING
        } ?: return null

        return extractDays(durationString = pricingPhase.billingPeriod)
    }

    private fun ProductDetails.SubscriptionOfferDetails?.getPrice(): PriceOffer {
        if (this == null) return PriceOffer()

        var lowestPrice = 0L
        var originalPrice = 0L
        val priceOffer = PriceOffer()

        for (price in this.pricingPhases.pricingPhaseList) {
            if (lowestPrice == 0L || price.priceAmountMicros < lowestPrice) {
                lowestPrice = price.priceAmountMicros

                priceOffer.lowestPriceStr = price.formattedPrice
                priceOffer.lowestPrice = price.priceAmountMicros
            }

            if (lowestPrice == 0L || price.priceAmountMicros > originalPrice) {
                originalPrice = price.priceAmountMicros

                priceOffer.originalPriceStr = price.formattedPrice
                priceOffer.originalPrice = price.priceAmountMicros
            }

            priceOffer.priceCurrencyCode = price.priceCurrencyCode
        }

        priceOffer.saleOff = if (lowestPrice != originalPrice) {
            100 - ((lowestPrice * 100f) / originalPrice).toInt()
        } else {
            0
        }

        return priceOffer
    }

    private fun ProductDetails.SubscriptionOfferDetails.getTrialOffer(): ProductDetails.SubscriptionOfferDetails? {
        return if (this.offerTags.contains(TRIAL)) {
            this
        } else {
            null
        }
    }

    private fun billingFlowParamsBuilder(
        productDetails: ProductDetails,
        offerToken: String?
    ): BillingFlowParams? {
        return try {
            BillingFlowParams.newBuilder().setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder().apply {
                        setProductDetails(productDetails)

                        if (offerToken != null)
                            setOfferToken(offerToken)
                    }.build()
                )
            ).build()
        } catch (_: NoClassDefFoundError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun upDowngradeBillingFlowParamsBuilder(
        productDetails: ProductDetails,
        offerToken: String?,
        oldToken: String
    ): BillingFlowParams {
        return BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder().apply {
                    setProductDetails(productDetails)

                    if (offerToken != null) setOfferToken(offerToken)
                }.build()
            )
        ).setSubscriptionUpdateParams(
            BillingFlowParams.SubscriptionUpdateParams.newBuilder().apply {
                setOldPurchaseToken(oldToken)
                setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_FULL_PRICE)
            }.build()
        ).build()
    }

    private fun showToastError(activity: Activity) {
        Toast.makeText(
            activity,
            activity.getString(R.string.service_not_support),
            Toast.LENGTH_SHORT
        ).show()
    }
}