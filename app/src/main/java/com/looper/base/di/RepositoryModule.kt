package com.looper.base.di

import com.looper.base.database.AppDatabase
import com.looper.base.equalizer.data.EqRepository
import org.koin.dsl.module

val repositoryModule = module {

    single {
        EqRepository(
            presetDao = get<AppDatabase>().eqPresetDao(),
            effectController = get(),
        )
    }

}