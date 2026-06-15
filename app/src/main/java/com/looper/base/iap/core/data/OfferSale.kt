package com.looper.base.iap.core.data

enum class OfferSale(
    val offerID: String,
    val packageType: PackageType
) {
    YearSale(offerID = "sale-off-year", packageType = PackageType.YearlySale),
    MonthSale(offerID = "sale-off-month", packageType = PackageType.MonthlySale),
    WeekSale(offerID = "sale-off-week", packageType = PackageType.WeeklySale);
    companion object {
        fun findPackageTypeById(id: String): PackageType? {
            return entries.find { it.offerID == id }?.packageType
        }
    }
}