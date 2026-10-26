package com.looper.base.di

import com.looper.base.equalizer.ui.EqViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {

    viewModel {
        EqViewModel(
            repository = get(),
            effectController = get(),
        )
    }

}