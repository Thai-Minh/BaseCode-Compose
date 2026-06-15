package com.looper.base.di

import com.looper.base.utils.NetworkManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val networkModule = module {
    single { NetworkManager(androidContext()) }
}