package com.looper.base.di

import com.looper.base.equalizer.ui.EqViewModel
import com.looper.base.player.sleeptimer.ui.SleepTimerViewModel
import com.looper.base.screen.about.AboutViewModel
import com.looper.base.screen.audiopreview.AudioPreviewViewModel
import com.looper.base.screen.home.HomeViewModel
import com.looper.base.screen.main.MainViewModel
import com.looper.base.screen.player.PlayerViewModel
import com.looper.base.screen.profile.EqualizerSheetViewModel
import com.looper.base.screen.profile.ProfileViewModel
import com.looper.base.screen.socialapp.SocialAppViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {

    viewModel {
        EqViewModel(
            repository = get(),
            effectController = get(),
        )
    }

    viewModel {
        SleepTimerViewModel(connection = get())
    }

    viewModel {
        MainViewModel()
    }

    viewModel {
        HomeViewModel(
            socialAppLauncher = get(),
            playerConnection = get(),
            libraryRepository = get(),
            incomingLinkDispatcher = get(),
        )
    }

    viewModel { parameters ->
        AudioPreviewViewModel(
            sourceUrl = parameters.get(),
            streamRepository = get(),
            libraryRepository = get(),
            downloadRepository = get(),
            playerConnection = get(),
        )
    }

    viewModel {
        SocialAppViewModel(socialAppLauncher = get())
    }

    viewModel {
        PlayerViewModel(
            connection = get(),
            libraryRepository = get(),
        )
    }

    viewModel {
        ProfileViewModel(
            playerConnection = get(),
            storageRepository = get(),
            downloadRepository = get(),
            listeningStatsRepository = get(),
        )
    }

    viewModel {
        AboutViewModel()
    }

    viewModel {
        EqualizerSheetViewModel(
            eqRepository = get(),
            autoEqRepository = get(),
        )
    }

}