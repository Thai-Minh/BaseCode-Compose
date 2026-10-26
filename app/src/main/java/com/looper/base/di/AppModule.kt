package com.looper.base.di

import com.looper.base.database.AppDatabase
import com.looper.base.database.DatabaseFactory
import com.looper.base.equalizer.audio.AudioEffectController
import com.looper.base.equalizer.audio.AudioFxEffectController
import org.koin.dsl.module

val appModule = module {

    single<AppDatabase> {
        DatabaseFactory.create(get())
    }

    single<AudioEffectController> {
        AudioFxEffectController()
    }

    single(createdAtStart = true) {
       // TODO: sử dụng "createdAtStart = true" khi muốn khởi tạo luôn class chứ không phải chờ khi nào sử dụng mới khởi tạo
    }
}