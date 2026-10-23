package com.looper.base.di

import com.looper.base.database.AppDatabase
import com.looper.base.database.DatabaseFactory
import org.koin.dsl.module

val appModule = module {

    single<AppDatabase> {
        DatabaseFactory.create(get())
    }

    single(createdAtStart = true) {
       // TODO: sử dụng "createdAtStart = true" khi muốn khởi tạo luôn class chứ không phải chờ khi nào sử dụng mới khởi tạo
    }
}