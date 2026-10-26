package com.looper.base.equalizer.audio

import com.looper.base.equalizer.model.EqCapabilities
import com.looper.base.equalizer.model.EqSettings
import kotlinx.coroutines.flow.StateFlow

interface AudioEffectController {

    val capabilities: StateFlow<EqCapabilities>

    val audioSessionId: StateFlow<Int>

    fun attach(audioSessionId: Int)

    fun apply(settings: EqSettings)

    fun detach()
}
