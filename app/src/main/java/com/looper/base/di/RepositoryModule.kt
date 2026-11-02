package com.looper.base.di

import com.looper.base.backup.data.BackupManager
import com.looper.base.database.AppDatabase
import com.looper.base.download.data.DownloadExporter
import com.looper.base.download.data.DownloadRepository
import com.looper.base.download.data.SmartDownloadsManager
import com.looper.base.equalizer.autoeq.AutoEqRepository
import com.looper.base.equalizer.data.EqRepository
import com.looper.base.lastfm.data.LastFmClient
import com.looper.base.lastfm.data.LastFmRepository
import com.looper.base.library.data.LibraryRepository
import com.looper.base.lyrics.data.UserLyricsRepository
import com.looper.base.player.library.MediaLibraryTree
import com.looper.base.player.source.MediaResolver
import com.looper.base.player.source.PlaybackItemFactory
import com.looper.base.player.source.StreamRepository
import com.looper.base.stats.data.ListeningStatsRepository
import com.looper.base.storage.StorageRepository
import com.looper.base.work.BackgroundWorkScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val repositoryModule = module {

    single {
        MediaResolver(context = androidContext())
    }

    single {
        StreamRepository(
            resolver = get(),
            mediaItemDao = get<AppDatabase>().mediaItemDao(),
            platformDao = get<AppDatabase>().platformDao(),
            streamCacheDao = get<AppDatabase>().streamCacheDao(),
            downloadedFileDao = get<AppDatabase>().downloadedFileDao(),
        )
    }

    single {
        PlaybackItemFactory(repository = get())
    }

    single {
        MediaLibraryTree(
            context = androidContext(),
            database = get(),
        )
    }

    single {
        EqRepository(
            presetDao = get<AppDatabase>().eqPresetDao(),
            effectController = get(),
            tenBandProcessor = get(),
        )
    }

    single {
        AutoEqRepository(context = androidContext())
    }

    single {
        DownloadRepository(
            context = androidContext(),
            jobDao = get<AppDatabase>().downloadJobDao(),
            fileDao = get<AppDatabase>().downloadedFileDao(),
            mediaItemDao = get<AppDatabase>().mediaItemDao(),
        )
    }

    single {
        DownloadExporter(
            context = androidContext(),
            mediaItemDao = get<AppDatabase>().mediaItemDao(),
            downloadedFileDao = get<AppDatabase>().downloadedFileDao(),
        )
    }

    single {
        StorageRepository(
            context = androidContext(),
            cache = get(),
            mediaItemDao = get<AppDatabase>().mediaItemDao(),
            downloadedFileDao = get<AppDatabase>().downloadedFileDao(),
        )
    }

    single {
        SmartDownloadsManager(
            playbackEventDao = get<AppDatabase>().playbackEventDao(),
            fileDao = get<AppDatabase>().downloadedFileDao(),
            downloadRepository = get(),
        )
    }

    single {
        BackupManager(
            context = androidContext(),
            database = get(),
        )
    }

    single {
        BackgroundWorkScheduler(
            context = androidContext(),
            downloadRepository = get(),
            smartDownloadsManager = get(),
            mediaItemDao = get<AppDatabase>().mediaItemDao(),
        )
    }

    single {
        LastFmClient()
    }

    single {
        LibraryRepository(
            context = androidContext(),
            database = get(),
            storageRepository = get(),
            lastFmClient = get(),
        )
    }

    single {
        UserLyricsRepository(
            lyricsDao = get<AppDatabase>().lyricsDao(),
            mediaItemDao = get<AppDatabase>().mediaItemDao(),
        )
    }

    single {
        ListeningStatsRepository(playbackEventDao = get<AppDatabase>().playbackEventDao())
    }

    single {
        LastFmRepository(client = get())
    }

}