package com.looper.base.iap.ui.data

import com.looper.base.iap.core.data.MONTHLY_KEY
import com.looper.base.iap.core.data.WEEKLY_KEY
import com.looper.base.iap.core.data.YEARLY_KEY

enum class RemoteBestOfferOB(
    val sampleName: String
) {
    Week(sampleName = "week"),
    Year(sampleName = "year"),
    Month(sampleName = "month");

    companion object {
        fun getPackageTypeKey(name: String): Int {
            val remote = entries.find {
                it.sampleName == name.lowercase()
            } ?: Week

            return when (remote) {
                Week -> WEEKLY_KEY
                Year -> YEARLY_KEY
                Month -> MONTHLY_KEY
            }
        }
    }
}