package com.looper.base.repository.combiner

import android.net.Uri
import com.looper.base.screen.combiner.data.CombinerVideo
import com.looper.base.screen.common.data.DualAudioMode
import com.looper.base.screen.common.data.DualLayout
import com.looper.base.screen.common.data.PipPosition

/** Video combiner: reads the picked videos and composites them into `Movies/Combiner`. */
interface ICombinerRepository {

    /** Metadata of a picked video, or null if it isn't a playable video. */
    suspend fun readVideo(uri: Uri): CombinerVideo?

    /**
     * Composites [mainVideo] and [overlayVideo] like the preview (the overlay is the full-frame
     * one when [overlayFullScreen]) and saves the result into the gallery. [audioMode] keeps
     * the main (v1) and / or the overlay's (v2) sound, wherever each one is shown.
     * [onProgress] receives 0..100 on the main thread.
     */
    suspend fun combine(
        mainVideo: CombinerVideo,
        overlayVideo: CombinerVideo,
        overlayFullScreen: Boolean,
        arrangement: DualLayout,
        audioMode: DualAudioMode,
        pipPosition: PipPosition,
        onProgress: (Int) -> Unit,
    ): Result<Uri>
}
