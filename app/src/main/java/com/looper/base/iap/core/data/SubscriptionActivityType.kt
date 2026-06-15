package com.looper.base.iap.core.data

enum class SubscriptionActivityType(val key: Int) {
    SubscriptionFailed(key = 0), // include Pending, Failed
    SubscriptionSuccess(key = 1),
    SubscriptionExpire(key = 3),
    CancelPurchase(key = 4),
}