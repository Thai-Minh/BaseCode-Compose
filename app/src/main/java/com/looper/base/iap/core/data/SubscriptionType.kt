package com.looper.base.iap.core.data

import com.looper.base.iap.utils.MONTH
import com.looper.base.iap.utils.WEEK
import com.looper.base.iap.utils.YEAR

enum class SubscriptionType(val key: String, val packageTypeDefault: PackageType) {
    Monthly(
        key = MONTH,
        packageTypeDefault = PackageType.MonthlyOriginal
    ),

    Weekly(
        key = WEEK,
        packageTypeDefault = PackageType.WeeklyOriginal
    ),

    Yearly(
        key = YEAR,
        packageTypeDefault = PackageType.YearlyOriginal
    )
}