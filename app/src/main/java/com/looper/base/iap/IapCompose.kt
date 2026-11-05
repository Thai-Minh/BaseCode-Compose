/**
*Created by: ThaiND
*/

package com.looper.base.iap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looper.base.utils.firebase.FRRemoteConfig
import org.koin.compose.koinInject

@Composable
fun canShowIapAds(): Boolean {
    val owner = koinInject<IAPOwner>()
    val isPremium by owner.isPremium.collectAsStateWithLifecycle()
    val review by FRRemoteConfig.fetchShowAdsWhenReview.collectAsStateWithLifecycle()

    return !isPremium && review == true
}
