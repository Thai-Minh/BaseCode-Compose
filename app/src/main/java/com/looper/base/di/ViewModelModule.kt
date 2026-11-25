package com.looper.base.di

import com.looper.base.iap.ui.MainIAPViewModel
import com.looper.base.screen.dualcamera.DualCameraViewModel
import com.looper.base.screen.reaction.ReactionViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::MainIAPViewModel)
    viewModelOf(::DualCameraViewModel)
    viewModelOf(::ReactionViewModel)
}
