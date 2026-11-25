package com.looper.base.screen.dualcamera.data

import android.net.Uri
import androidx.annotation.StringRes

sealed interface DualCameraEffect {
    /** @param isSingleCamera only one camera delivered a frame, so the photo isn't a PiP. */
    data class PhotoSaved(val uri: Uri, val isSingleCamera: Boolean = false) : DualCameraEffect
    data class VideoSaved(val uri: Uri) : DualCameraEffect
    data class Error(@param:StringRes val messageRes: Int) : DualCameraEffect
}
