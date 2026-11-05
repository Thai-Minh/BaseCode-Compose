package com.looper.base.di

import com.looper.base.iap.FirebaseSubscriptionConfigSource
import com.looper.base.iap.IAPOwner
import com.looper.base.iap.SessionCatalogLoader
import com.looper.base.iap.data.DefaultSubscriptionConfig
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

fun iapModule(applicationScope: CoroutineScope) = module {
    single<IAPOwner> {
        IAPOwner(
            context = get(),
            scope = applicationScope,
            catalogLoader = SessionCatalogLoader(
                source = FirebaseSubscriptionConfigSource(context = get()),
                defaultPayload = DefaultSubscriptionConfig.payload,
                onFallback = {},
            ),
        )
    }
}