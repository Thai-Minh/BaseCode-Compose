package com.looper.base.di

import com.looper.base.audio.playback.AudioPlaybackConnection
import com.looper.base.screen.download_test.CatcherViewModel
import com.looper.base.screen.main.MainViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { MainViewModel(get(), AudioPlaybackConnection(androidContext())) }
    viewModel { CatcherViewModel(get(), get(), get()) }
}
