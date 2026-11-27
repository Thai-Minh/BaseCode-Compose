package com.looper.base.repository.dualcamera.data

import android.net.Uri

/**
 * A dual photo saved into the gallery.
 * @param isSingleCamera one of the two open cameras delivered no frame, so its slot is red
 * (never set in single-camera mode, where only one camera is open on purpose).
 */
data class DualPhoto(
    val uri: Uri,
    val isSingleCamera: Boolean,
)
