package com.looper.base.di

import android.os.Build
import androidx.media3.datasource.cache.Cache
import com.looper.base.BuildConfig
import com.looper.base.R
import com.looper.base.base.storage.playerSettings
import com.looper.base.database.AppDatabase
import com.looper.base.database.DatabaseFactory
import com.looper.base.deeplink.IncomingLinkDispatcher
import com.looper.base.equalizer.audio.AudioEffectController
import com.looper.base.equalizer.audio.AudioFxEffectController
import com.looper.base.equalizer.audio.TenBandEqualizerProcessor
import com.looper.base.listentogether.ListenTogetherSession
import com.looper.base.player.PlayerConnection
import com.looper.base.player.audio.AudioOutputRouter
import com.looper.base.player.pip.PipManager
import com.looper.base.player.source.PlayerCacheFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val appModule = module {

    single<AppDatabase> {
        DatabaseFactory.create(get())
    }

    single<Cache> {
        PlayerCacheFactory.create(
            context = androidContext(),
            sizeMb = runBlocking {
                playerSettings.first().cacheSizeMb
            },
        )
    }

    single<AudioEffectController> {
        AudioFxEffectController()
    }

    single {
        TenBandEqualizerProcessor()
    }

    single {
        AudioOutputRouter(context = androidContext())
    }

    single(createdAtStart = true) {
        PlayerConnection(context = androidContext()).also {
            it.bindToProcessLifecycle()
        }
    }

    single {
        PipManager()
    }

    single {
        OkHttpClient.Builder()
            .connectTimeout(timeout = 15, unit = TimeUnit.SECONDS)
            .build()
    }

    single {
        val context = androidContext()
        ListenTogetherSession(
            okHttpClient = get(),
            userAgent = "${
                context.getString(R.string.app_name).replace(oldValue = " ", newValue = "")
            }/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})",
        )
    }

    single {
        IncomingLinkDispatcher(
            playerConnection = get(),
            mediaItemDao = get<AppDatabase>().mediaItemDao(),
            lastFmRepository = get(),
        )
    }
}