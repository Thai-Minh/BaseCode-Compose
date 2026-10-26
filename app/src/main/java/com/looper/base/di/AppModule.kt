package com.looper.base.di

import com.looper.base.database.AppDatabase
import com.looper.base.database.DatabaseFactory
import com.looper.base.equalizer.audio.AudioEffectController
import com.looper.base.equalizer.audio.AudioFxEffectController
import com.looper.base.player.PlayerConnection
import com.looper.base.player.pip.PipManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {

    single<AppDatabase> {
        DatabaseFactory.create(get())
    }

    single<AudioEffectController> {
        AudioFxEffectController()
    }

    single(createdAtStart = true) {
        PlayerConnection(context = androidContext()).also {
            it.bindToProcessLifecycle()
        }
    }

    single {
        PipManager()
    }
}