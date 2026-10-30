package com.looper.base.di

import com.looper.base.database.AppDatabase
import com.looper.base.equalizer.data.EqRepository
import com.looper.base.player.source.MediaResolver
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val repositoryModule = module {

    single {
        MediaResolver(context = androidContext())
    }

    single {
        EqRepository(
            presetDao = get<AppDatabase>().eqPresetDao(),
            effectController = get(),
        )
    }

}