package com.looper.base.di

import com.looper.base.repository.combiner.CombinerRepositoryImpl
import com.looper.base.repository.combiner.ICombinerRepository
import com.looper.base.repository.dualcamera.DualCameraRepositoryImpl
import com.looper.base.repository.dualcamera.IDualCameraRepository
import com.looper.base.repository.reaction.IReactionRepository
import com.looper.base.repository.reaction.ReactionRepositoryImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val repositoryModule = module {
    // Hold the screen's bound cameras and running recording: a new instance per ViewModel.
    factory<IDualCameraRepository> { DualCameraRepositoryImpl(androidContext()) }
    factory<IReactionRepository> { ReactionRepositoryImpl(androidContext()) }

    single<ICombinerRepository> { CombinerRepositoryImpl(androidContext()) }
}
