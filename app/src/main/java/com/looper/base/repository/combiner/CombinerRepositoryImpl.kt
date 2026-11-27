package com.looper.base.repository.combiner

import android.content.Context
import android.net.Uri
import com.looper.base.repository.runCatchingCancellable
import com.looper.base.screen.combiner.CombinerExporter
import com.looper.base.screen.combiner.data.CombinerVideo
import com.looper.base.screen.common.data.DualAudioMode
import com.looper.base.screen.common.data.DualLayout
import com.looper.base.screen.common.data.PipPosition
import com.looper.base.screen.dualcamera.DualCameraMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ALBUM_NAME = "Combiner"
private const val FILE_PREFIX = "COMBINE_"

/** Stateless: the export is done by [CombinerExporter]. */
class CombinerRepositoryImpl(
    private val context: Context,
) : ICombinerRepository {

    private val exporter = CombinerExporter(context)

    override suspend fun readVideo(uri: Uri): CombinerVideo? =
        withContext(Dispatchers.IO) { CombinerVideo.read(context, uri) }

    override suspend fun combine(
        mainVideo: CombinerVideo,
        overlayVideo: CombinerVideo,
        overlayFullScreen: Boolean,
        arrangement: DualLayout,
        audioMode: DualAudioMode,
        pipPosition: PipPosition,
        onProgress: (Int) -> Unit,
    ): Result<Uri> = runCatchingCancellable {
        val (full, window) = if (overlayFullScreen) {
            overlayVideo to mainVideo
        } else {
            mainVideo to overlayVideo
        }
        // v1 = the main video's sound, v2 = the overlay's, wherever each one is shown.
        val keepsMainAudio = audioMode.keepsMain
        val keepsOverlayAudio = audioMode.keepsSecondary

        val output = exporter.export(
            firstWidth = full.width,
            firstHeight = full.height,
            full = full.toSource(if (overlayFullScreen) keepsOverlayAudio else keepsMainAudio),
            window = window.toSource(if (overlayFullScreen) keepsMainAudio else keepsOverlayAudio),
            windowAspectRatio = window.aspectRatio,
            arrangement = arrangement,
            pipPosition = pipPosition,
            onProgress = onProgress,
        )
        withContext(Dispatchers.IO) {
            try {
                DualCameraMedia.saveVideoFile(context, output, ALBUM_NAME, FILE_PREFIX)
            } finally {
                output.delete()
            }
        }
    }

    private fun CombinerVideo.toSource(keepAudio: Boolean) =
        CombinerExporter.Source(uri, durationUs, keepAudio)
}
