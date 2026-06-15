package com.looper.base.iap.core.data

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val PRODUCT_ID = "productId"
private const val BASE_PLAN_ID = "basePlanId"
private const val PRODUCT_TYPE = "productType"
private const val AUTO_RENEWAL = "isAutoRenewal"
private const val PURCHASE_STATE = "purchaseState"
private const val PURCHASE_TIME = "purchaseTime"
private const val EXPIRY_TIME = "expiryTime"
private const val BILLING_PERIOD = "billingPeriod"
private const val FORMATTED_PRICE = "formattedPrice"
private const val PRICE_AMOUNT_MICROS = "priceAmountMicros"
private const val IS_PREMIUM = "isPremium"

data class SubscriptionInfo(
    val productId: String = "",
    val basePlanId: String = "",
    val productType: String = "",
    val isAutoRenewal: Boolean = false,
    val purchaseState: Int = 0,
    val purchaseTime: Long = -1L,
    val expiryTime: Long = -1L,
    val billingPeriod: String = "",
    val formattedPrice: String = "",
    val priceAmountMicros: Long = -1L,
    val isPremium: Boolean = false,
) {

    override fun toString(): String {
        return "SubscriptionInfo(productId=$productId, basePlanId=$basePlanId, productType=$productType, isAutoRenewal=$isAutoRenewal, purchaseState=$purchaseState, " +
                "purchaseTime=$purchaseTime, expiryTime=$expiryTime, billingPeriod=$billingPeriod, formattedPrice=$formattedPrice, " +
                "priceAmountMicros=$priceAmountMicros, isPremium=$isPremium)"
    }
}

fun List<SubscriptionInfo>.toJson(): String {
    val jsonArray = JSONArray()

    for (info in this) {
        val infoJson = info.toJson()
        jsonArray.put(infoJson)
    }

    return jsonArray.toString()
}

fun String.decodeBase64JsonOrEmpty(): String {
    return try {
        val decoded = Base64.decode(this, Base64.NO_WRAP)
        val text = String(decoded).trim()
        if (text.isEmpty() || text == "null") "[]" else text
    } catch (e: Exception) {
        "[]"
    }
}

fun String.toSubscriptionList(): MutableList<SubscriptionInfo> {
    val temps = mutableListOf<SubscriptionInfo>()

    try {
        val jsonArray = JSONArray(this)
        for (i in 0 until jsonArray.length()) {
            val infoStr = jsonArray.optString(i)
            temps.add(infoStr.toSubscriptionInfo())
        }
    } catch (e: JSONException) {
        Log.w("MTHAI", "Invalid JSON string: $this", e)
    }

    return temps
}

private fun SubscriptionInfo.toJson(): String {
    val jsonObject = JSONObject()
    jsonObject.put(PRODUCT_ID, this.productId)
    jsonObject.put(BASE_PLAN_ID, this.basePlanId)
    jsonObject.put(PRODUCT_TYPE, this.productType)
    jsonObject.put(AUTO_RENEWAL, this.isAutoRenewal)
    jsonObject.put(PURCHASE_STATE, this.purchaseState)
    jsonObject.put(PURCHASE_TIME, this.purchaseTime)
    jsonObject.put(EXPIRY_TIME, this.expiryTime)
    jsonObject.put(BILLING_PERIOD, this.billingPeriod)
    jsonObject.put(FORMATTED_PRICE, this.formattedPrice)
    jsonObject.put(PRICE_AMOUNT_MICROS, this.priceAmountMicros)
    jsonObject.put(IS_PREMIUM, this.isPremium)
    return jsonObject.toString()
}

private fun String.toSubscriptionInfo(): SubscriptionInfo {
    val jsonObject = JSONObject(this)

    return SubscriptionInfo(
        productId = jsonObject.optString(PRODUCT_ID, ""),
        basePlanId = jsonObject.optString(BASE_PLAN_ID, ""),
        productType = jsonObject.optString(PRODUCT_TYPE, ""),
        isAutoRenewal = jsonObject.optBoolean(AUTO_RENEWAL, false),
        purchaseState = jsonObject.optInt(PURCHASE_STATE, 0),
        purchaseTime = jsonObject.optLong(PURCHASE_TIME, -1L),
        expiryTime = jsonObject.optLong(EXPIRY_TIME, -1L),
        billingPeriod = jsonObject.optString(BILLING_PERIOD, ""),
        formattedPrice = jsonObject.optString(FORMATTED_PRICE, ""),
        priceAmountMicros = jsonObject.optLong(PRICE_AMOUNT_MICROS, -1L),
        isPremium = jsonObject.optBoolean(IS_PREMIUM, false),
    )
}

val SubscriptionInfo.isExpiry: Boolean get() = expiryTime < System.currentTimeMillis()