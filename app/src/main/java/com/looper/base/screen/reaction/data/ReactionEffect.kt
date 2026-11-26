package com.looper.base.screen.reaction.data

import android.net.Uri
import androidx.annotation.StringRes

sealed interface ReactionEffect {
    data class VideoSaved(val uri: Uri) : ReactionEffect
    data class Error(@StringRes val messageRes: Int) : ReactionEffect
}
