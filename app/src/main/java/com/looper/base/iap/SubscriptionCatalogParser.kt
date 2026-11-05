/**
*Created by: ThaiND
*/

package com.looper.base.iap

import com.looper.iap.catalog.IapCatalog
import com.looper.iap.catalog.IapOfferConfig
import com.looper.iap.catalog.IapPlanConfig
import com.looper.iap.catalog.IapProductConfig
import com.looper.iap.model.OfferKind
import com.looper.iap.model.PackageType
import org.json.JSONArray
import org.json.JSONObject

data class AvailablePaywallOffer<T>(
    val productId: String,
    val basePlanId: String?,
    val offerId: String?,
    val item: T,
)

/** A selling selection, not the complete catalog needed to restore existing subscribers. */
data class SubscriptionSelection(
    val catalog: IapCatalog,
    val bestOfferKey: String,
) {
    fun <T> selectAvailableOffers(available: List<AvailablePaywallOffer<T>>): List<T> {
        val subscriptions = catalog.products.filterIsInstance<IapProductConfig.Subscription>()
        val plan = if (bestOfferKey.isEmpty()) {
            subscriptions.firstOrNull()?.plans?.firstOrNull()
        } else {
            subscriptions.asSequence().flatMap { product -> product.plans.asSequence() }
                .firstOrNull { candidate ->
                    candidate.offers.any { offer -> offer.packageType.key == bestOfferKey }
                }
        } ?: return emptyList()
        val product = subscriptions.firstOrNull { subscription -> plan in subscription.plans }
            ?: return emptyList()
        val base = plan.offers.singleOrNull { offer -> offer.kind == OfferKind.BASE }
            ?: return emptyList()

        fun find(offer: IapOfferConfig): T? = available.firstOrNull { candidate ->
            candidate.productId == product.productId &&
                candidate.basePlanId == plan.basePlanId &&
                candidate.offerId == offer.offerId
        }?.item

        val baseItem = find(offer = base)
        val preferred = plan.offers.firstOrNull { offer -> offer.packageType.key == bestOfferKey }
        val preferredItem = preferred?.let { offer -> find(offer = offer) }
        val selected = preferredItem ?: baseItem ?: return emptyList()

        return if (preferred?.kind == OfferKind.TRIAL && preferredItem != null && baseItem != null) {
            listOf(selected, baseItem)
        } else {
            listOf(selected)
        }
    }
}

object SubscriptionCatalogParser {
    fun parse(json: String): SubscriptionSelection {
        val root = JSONObject(json)
        root.requireFields(fields = setOf("bestoffer", "products"))
        val bestOffer = root.requiredString(name = "bestoffer", allowEmpty = true)

        if (bestOffer.isNotEmpty()) {
            validateKey(key = bestOffer)
        }

        val products = root.getJSONArray("products")
        require(value = products.length() == 1) { "Expected exactly one selling subscription" }
        val product = products.getJSONObject(0)
        product.requireFields(fields = setOf("productId", "entitlementId", "plan"))
        val productId = product.requiredString(name = "productId")
        val entitlementId = product.requiredString(name = "entitlementId")
        require(value = entitlementId == "premium") { "Expected premium entitlement" }
        val planArray = product.getJSONArray("plan")
        val plans = (0 until planArray.length()).map { index ->
            parsePlan(plan = planArray.getJSONObject(index))
        }
        val catalog = IapCatalog(
            products = listOf(
                IapProductConfig.Subscription(
                    productId = productId,
                    entitlementId = entitlementId,
                    plans = plans,
                ),
            ),
        )
        val offers = plans.flatMap { plan -> plan.offers }
        require(value = bestOffer.isEmpty() || offers.any { offer -> offer.packageType.key == bestOffer }) {
            "bestoffer must be empty or reference a declared offer"
        }

        return SubscriptionSelection(
            catalog = catalog,
            bestOfferKey = bestOffer,
        )
    }

    private fun parsePlan(plan: JSONObject): IapPlanConfig {
        plan.requireFields(fields = setOf("key", "basePlanId", "offer"))
        val key = plan.requiredString(name = "key")
        validateKey(key = key)
        val basePlanId = plan.requiredString(name = "basePlanId")
        val offerArray = plan.getJSONArray("offer")
        val offers = (0 until offerArray.length()).map { index ->
            parseOffer(offer = offerArray.getJSONObject(index))
        }
        require(value = offers.count { offer -> offer.kind == OfferKind.BASE } == 1) {
            "Each plan requires exactly one BASE offer"
        }
        require(value = offers.single { offer -> offer.kind == OfferKind.BASE }.packageType.key == key) {
            "Plan key must equal BASE key"
        }

        return IapPlanConfig(
            packageType = PackageType(key = key),
            basePlanId = basePlanId,
            offers = offers,
        )
    }

    private fun parseOffer(offer: JSONObject): IapOfferConfig {
        offer.requireFields(fields = setOf("key", "offerId", "kind", "tags"))
        val key = offer.requiredString(name = "key")
        validateKey(key = key)
        val kind = OfferKind.valueOf(value = offer.requiredString(name = "kind"))
        val offerId = if (offer.isNull("offerId")) {
            null
        } else {
            offer.requiredString(name = "offerId")
        }
        require(value = (kind == OfferKind.BASE) == (offerId == null)) {
            "Only BASE must have null offerId"
        }
        val tags = offer.getJSONArray("tags").requiredStrings()
        require(value = tags.size == tags.distinct().size) { "Duplicate tags" }

        return IapOfferConfig(
            packageType = PackageType(key = key),
            offerId = offerId,
            kind = kind,
            tags = tags.toSet(),
        )
    }

    private fun validateKey(key: String) {
        require(value = key.matches(regex = Regex(pattern = "[A-Z][a-zA-Z0-9]*"))) {
            "Expected CamelCase key"
        }
    }

    private fun JSONObject.requireFields(fields: Set<String>) {
        require(value = keys().asSequence().toSet() == fields) { "Missing or unexpected fields" }
    }

    private fun JSONObject.requiredString(name: String, allowEmpty: Boolean = false): String {
        val value = get(name)
        require(value = value is String) { "$name must be a string" }
        require(value = value == value.trim() && (allowEmpty || value.isNotBlank())) {
            "$name must not contain surrounding whitespace or be blank"
        }

        return value
    }

    private fun JSONArray.requiredStrings(): List<String> = (0 until length()).map { index ->
        val value = get(index)
        require(value = value is String && value.isNotBlank() && value == value.trim()) {
            "Tags must be nonblank strings"
        }

        value
    }
}
