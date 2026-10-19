package com.looper.base.di

import com.looper.base.repository.audio_local.ILocalAudioWrapper
import com.looper.base.repository.audio_local.LocalAudioWrapperImpl
import com.looper.base.repository.youtubedl.YoutubeDLDownloadRepository
import com.looper.base.repository.youtubedl.YoutubeDLDownloadRepositoryImpl
import com.looper.base.repository.youtubedl.YoutubeDLVideoResolver
import com.looper.base.repository.youtubedl.YoutubeDLVideoResolverImpl
import com.looper.local_file_manager.ILocalFileManager
import com.looper.local_file_manager.LocalFileManagerImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val repositoryModule = module {
    single<ILocalFileManager> { LocalFileManagerImpl(application = get()) }
    single<ILocalAudioWrapper> {
        LocalAudioWrapperImpl(localFileManager = get(), contentResolver = androidContext().contentResolver)
    }
    single<YoutubeDLDownloadRepository> { YoutubeDLDownloadRepositoryImpl(androidContext(), get()) }
    single<YoutubeDLVideoResolver> { YoutubeDLVideoResolverImpl(androidContext()) }
}
