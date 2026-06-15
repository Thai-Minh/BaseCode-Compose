package com.looper.base.di

import org.koin.dsl.module

val appModule = module {

//    single<AppDatabase> {
//        DatabaseFactory.create(get())
//    }

    single(createdAtStart = true) {
       // TODO: sử dụng "createdAtStart = true" khi muốn khởi tạo luôn class chứ không phải chờ khi nào sử dụng mới khởi tạo
    }
}