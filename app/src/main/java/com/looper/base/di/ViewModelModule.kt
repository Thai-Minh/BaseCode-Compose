package com.looper.base.di

import com.looper.base.iap.ui.MainIAPViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::MainIAPViewModel)
}