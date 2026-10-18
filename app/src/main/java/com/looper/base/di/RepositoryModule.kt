package com.looper.base.di

import com.looper.base.audio.repository.AudioRepository
import com.looper.base.repository.youtubedl.YoutubeDLDownloadRepository
import com.looper.base.repository.youtubedl.YoutubeDLDownloadRepositoryImpl
import com.looper.base.repository.youtubedl.YoutubeDLVideoResolver
import com.looper.base.repository.youtubedl.YoutubeDLVideoResolverImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val repositoryModule = module {
    single { AudioRepository(androidContext()) }
    single<YoutubeDLDownloadRepository> { YoutubeDLDownloadRepositoryImpl(androidContext(), get()) }
    single<YoutubeDLVideoResolver> { YoutubeDLVideoResolverImpl(androidContext()) }
}
