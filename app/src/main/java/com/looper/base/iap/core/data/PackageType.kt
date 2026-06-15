package com.looper.base.iap.core.data

import com.looper.base.R

const val INAPP_KEY = 0
const val LIFETIME_KEY = 1
const val MONTHLY_KEY = 2
const val WEEKLY_KEY = 3
const val YEARLY_KEY = 4


private const val MONTH_SALE = "monthlySale"
private const val WEEK_SALE = "weekSale"
private const val YEAR_SALE = "yearSale"


enum class PackageType(
    val key: Int,
    val nameId: Int,
    val defaultPackage: Boolean = false,
    val promoSaleKey: String? = null
) {
    Lifetime(
        key = LIFETIME_KEY,
        nameId = R.string.lifetime,
        defaultPackage = true
    ),

    MonthlyOriginal(
        key = MONTHLY_KEY,
        nameId = R.string.monthly,
        defaultPackage = true
    ),
    MonthlyTrial(
        key = MONTHLY_KEY,
        nameId = R.string.monthly
    ),
    MonthlySale(
        key = MONTHLY_KEY,
        nameId = R.string.monthly,
        promoSaleKey = MONTH_SALE
    ),

    WeeklyOriginal(
        key = WEEKLY_KEY,
        nameId = R.string.weekly,
        defaultPackage = true
    ),
    WeeklyTrial(
        key = WEEKLY_KEY,
        nameId = R.string.weekly
    ),
    WeeklySale(
        key = WEEKLY_KEY,
        nameId = R.string.weekly,
        promoSaleKey = WEEK_SALE
    ),

    YearlyOriginal(
        key = YEARLY_KEY,
        nameId = R.string.txt_yearly,
        defaultPackage = true
    ),
    YearlyTrial(
        key = YEARLY_KEY,
        nameId = R.string.txt_yearly
    ),
    YearlySale(
        key = YEARLY_KEY,
        nameId = R.string.txt_yearly,
        promoSaleKey = YEAR_SALE
    );

    companion object {
        fun getPackageTrial(key: Int): PackageType? {
            return when (key) {
                MONTHLY_KEY -> MonthlyOriginal
                WEEKLY_KEY -> WeeklyTrial
                else -> null
            }
        }

        fun getPackageSale(promoSaleKey: String): PackageType? {
            return entries.find {
                it.promoSaleKey == promoSaleKey
            }
        }
    }
}

val packageSales = listOf(PackageType.MonthlySale, PackageType.WeeklySale, PackageType.YearlySale)
val packageTrials = listOf(PackageType.MonthlyTrial, PackageType.WeeklyTrial)

fun PackageType.isOriginal(): Boolean {
    return this == PackageType.YearlyOriginal || this == PackageType.MonthlyOriginal || this == PackageType.WeeklyOriginal
}
