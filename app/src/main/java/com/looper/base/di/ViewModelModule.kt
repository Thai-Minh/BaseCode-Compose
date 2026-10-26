package com.looper.base.di

import com.looper.base.equalizer.ui.EqViewModel
import com.looper.base.player.sleeptimer.ui.SleepTimerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {

    viewModel {
        EqViewModel(
            repository = get(),
            effectController = get(),
        )
    }

    viewModel {
        SleepTimerViewModel(connection = get())
    }

}