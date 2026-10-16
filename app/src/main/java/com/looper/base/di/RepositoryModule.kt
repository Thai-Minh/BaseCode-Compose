package com.looper.base.di

import com.looper.base.audio.repository.AudioRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val repositoryModule = module {
    single { AudioRepository(androidContext()) }
}
