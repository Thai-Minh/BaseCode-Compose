package com.looper.base.screen.combiner.data

import android.net.Uri
import androidx.annotation.StringRes

sealed interface CombinerEffect {
    data class VideoSaved(val uri: Uri) : CombinerEffect
    data class Error(@StringRes val messageRes: Int) : CombinerEffect
}
