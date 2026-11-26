package com.looper.base.screen.combiner

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.looper.base.screen.combiner.data.CombinerLayout
import com.looper.base.screen.combiner.data.CombinerRect
import com.looper.base.screen.common.data.DualLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "CombinerExporter"

/** Longest / shortest side of the exported video. */
private const val MAX_OUTPUT_LONG_SIDE = 1920
private const val MAX_OUTPUT_SHORT_SIDE = 1080

/**
 * Width of each video in the split layouts (stacked: both, side by side: each half), before
 * scaling to the output limits.
 */
private const val SPLIT_SHARED_SIDE = 1080

/** Shorter differences are not worth a held frame (it would last a frame or two). */
private const val MIN_HOLD_US = 50_000L

/** Frame rate of the held last frame. */
private const val HOLD_FRAME_RATE = 30

private val PROGRESS_POLL_INTERVAL = 250.milliseconds

/**
 * Composites two videos like the preview, in a [DualLayout]: one fills the frame and the
 * other sits in a corner window (1/3 of the width), or they are stacked / side by side. Every
 * video keeps its original aspect ratio. The output lasts as long as the longer video; the one
 * that ends first keeps showing its last frame until the end.
 *
 * Built on Media3 Transformer. With several video sequences its compositor draws the first
 * sequence on top and takes the output timeline from it, so:
 * - sequence 0 = the window video, followed by a still of its last frame when it is the shorter
 *   one (it drives the timeline, so it must last until the end);
 * - sequence 1 = the full-frame video; when it is the shorter one the compositor keeps
 *   reusing its last frame by itself.
 */
@OptIn(UnstableApi::class)
class CombinerExporter(private val context: Context) {

    /**
     * One of the two inputs.
     * @param keepAudio The user wants this input's sound (ignored if the file has none).
     */
    class Source(
        val uri: Uri,
        val durationUs: Long,
        val keepAudio: Boolean,
    )

    /**
     * Exports [full] (the first video, [firstWidth] x [firstHeight] upright) and [window] (the
     * second, [windowAspectRatio] width / height) placed by [arrangement] into a temporary MP4 in
     * the cache dir and returns it; the caller owns the file. [onProgress] receives 0..100 on the
     * main thread.
     */
    suspend fun export(
        firstWidth: Int,
        firstHeight: Int,
        full: Source,
        window: Source,
        windowAspectRatio: Float,
        arrangement: DualLayout,
        onProgress: (Int) -> Unit,
    ): File {
        val firstAspectRatio = firstWidth.toFloat() / firstHeight
        val canvasAspectRatio =
            CombinerLayout.canvasAspectRatio(arrangement, firstAspectRatio, windowAspectRatio)
        val (frameWidth, frameHeight) = when (arrangement) {
            DualLayout.SplitVertical ->
                SPLIT_SHARED_SIDE to (SPLIT_SHARED_SIDE / canvasAspectRatio).roundToInt()

            // Each video gets a half as wide as the shared side.
            DualLayout.SplitHorizontal ->
                (2 * SPLIT_SHARED_SIDE) to (2 * SPLIT_SHARED_SIDE / canvasAspectRatio).roundToInt()

            else -> firstWidth to firstHeight
        }

        // A sequence that asks for audio needs an audio track in its video.
        val (fullSource, windowSource) = withContext(Dispatchers.IO) {
            full.withAvailableAudio() to window.withAvailableAudio()
        }

        // The window video drives the timeline: hold its last frame if it ends first.
        val holdUs = fullSource.durationUs - windowSource.durationUs
        val lastFrame = if (holdUs > MIN_HOLD_US) {
            withContext(Dispatchers.IO) { saveLastFrame(windowSource) }
        } else {
            null
        }

        val output = File(context.cacheDir, "combine_${System.nanoTime()}.mp4")
        try {
            val composition = buildComposition(
                canvasWidth = frameWidth,
                canvasHeight = frameHeight,
                firstAspectRatio = firstAspectRatio,
                windowAspectRatio = windowAspectRatio,
                windowSource = windowSource,
                windowLastFrame = lastFrame?.let { it to holdUs },
                fullSource = fullSource,
                arrangement = arrangement,
            )
            Log.i(
                TAG,
                "Export: arrangement=$arrangement, full=${fullSource.durationUs / 1000} ms " +
                    "(audio=${fullSource.keepAudio}), window=${windowSource.durationUs / 1000} ms " +
                    "(audio=${windowSource.keepAudio}), holdWindow=${lastFrame != null}",
            )
            runTransformer(composition, output, onProgress)
            return output
        } catch (e: Throwable) {
            output.delete()
            throw e
        } finally {
            lastFrame?.delete()
        }
    }

    private fun Source.withAvailableAudio(): Source =
        if (keepAudio && !hasAudio(uri)) Source(uri, durationUs, keepAudio = false) else this

    /** Runs the export; Transformer must be created and used on one Looper thread (main). */
    private suspend fun runTransformer(
        composition: Composition,
        output: File,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.Main) {
        val result = CompletableDeferred<Unit>()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    Log.i(TAG, "Exported ${exportResult.fileSizeBytes} bytes")
                    result.complete(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    result.completeExceptionally(exportException)
                }
            })
            .build()

        try {
            transformer.start(composition, output.path)
            coroutineScope {
                val poller = launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress)
                        }
                        delay(PROGRESS_POLL_INTERVAL)
                    }
                }
                result.await()
                poller.cancel()
            }
            onProgress(100)
        } catch (e: Throwable) {
            transformer.cancel()
            throw e
        }
    }

    /**
     * @param windowLastFrame Still of the window video's last frame and how long to show it,
     *     when the window video is the shorter one.
     */
    private fun buildComposition(
        canvasWidth: Int,
        canvasHeight: Int,
        firstAspectRatio: Float,
        windowAspectRatio: Float,
        windowSource: Source,
        windowLastFrame: Pair<File, Long>?,
        fullSource: Source,
        arrangement: DualLayout,
    ): Composition {
        val output = outputSize(canvasWidth, canvasHeight)
        val (fullRect, windowRect) = CombinerLayout.slots(
            output.width.toFloat(),
            output.height.toFloat(),
            arrangement,
            firstAspectRatio,
            windowAspectRatio,
        )
        val fullSize = fullRect.evenSize()
        val windowSize = windowRect.evenSize()
        Log.i(
            TAG,
            "Output ${output.width}x${output.height}, first ${fullSize.width}x${fullSize.height}, " +
                "second ${windowSize.width}x${windowSize.height}",
        )

        // Sequence 0 (on top, drives the timeline): the window video, then its held last frame.
        val windowPresentation = Presentation.createForWidthAndHeight(
            windowSize.width,
            windowSize.height,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
        )
        val windowSequence = EditedMediaItemSequence.Builder(trackTypes(windowSource.keepAudio))
            .addItem(videoItem(windowSource, windowPresentation))
            .apply {
                windowLastFrame?.let { (frame, durationUs) ->
                    addItem(stillItem(frame, durationUs, windowPresentation))
                }
            }
            .build()

        // Sequence 1: the first video (keeps its last frame by itself if shorter).
        val fullPresentation = Presentation.createForWidthAndHeight(
            fullSize.width,
            fullSize.height,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
        )
        val fullSequence = EditedMediaItemSequence.Builder(trackTypes(fullSource.keepAudio))
            .addItem(videoItem(fullSource, fullPresentation))
            .build()

        return Composition.Builder(windowSequence, fullSequence)
            .setVideoCompositorSettings(CombinerCompositorSettings(output, fullRect, windowRect))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
    }

    private fun videoItem(source: Source, presentation: Presentation): EditedMediaItem =
        EditedMediaItem.Builder(MediaItem.fromUri(source.uri))
            .setRemoveAudio(!source.keepAudio)
            .setEffects(
                Effects(
                    if (source.keepAudio) stereoProcessors() else emptyList(),
                    listOf(presentation),
                )
            )
            .build()

    /** A still image shown for [durationUs] (no audio: the sequence generates silence). */
    private fun stillItem(frame: File, durationUs: Long, presentation: Presentation): EditedMediaItem =
        EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(Uri.fromFile(frame))
                .setMimeType(MimeTypes.IMAGE_JPEG)
                .setImageDurationMs(max(1L, durationUs / 1000))
                .build()
        )
            .setFrameRate(HOLD_FRAME_RATE)
            .setEffects(Effects(emptyList(), listOf(presentation)))
            .build()

    /** Places input 0 (second video) in [windowRect] and input 1 (first video) in [fullRect]. */
    private class CombinerCompositorSettings(
        private val outputSize: Size,
        fullRect: CombinerRect,
        windowRect: CombinerRect,
    ) : VideoCompositorSettings {
        private val windowSettings = placedAt(windowRect)
        private val fullSettings = placedAt(fullRect)

        /** Pins the input's top-left corner to the rect's top-left corner (inputs are pre-sized). */
        private fun placedAt(rect: CombinerRect): OverlaySettings {
            // NDC: x -1 (left) .. 1 (right), y -1 (bottom) .. 1 (top).
            val x = 2f * rect.left.roundToInt() / outputSize.width - 1f
            val y = 1f - 2f * rect.top.roundToInt() / outputSize.height
            return StaticOverlaySettings.Builder()
                .setOverlayFrameAnchor(-1f, 1f)
                .setBackgroundFrameAnchor(x, y)
                .build()
        }

        override fun getOutputSize(inputSizes: List<Size>): Size = outputSize

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings =
            if (inputId == 0) windowSettings else fullSettings
    }

    private fun trackTypes(withAudio: Boolean): Set<Int> =
        if (withAudio) setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO) else setOf(C.TRACK_TYPE_VIDEO)

    /** Mixed audio inputs must share a channel count: convert mono / stereo inputs to stereo. */
    private fun stereoProcessors(): List<AudioProcessor> = listOf(
        ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(1, 2))
            putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(2, 2))
        }
    )

    /** Canvas size scaled down to at most 1920x1080 (either orientation), even dimensions. */
    private fun outputSize(width: Int, height: Int): Size {
        val longSide = max(width, height).toFloat()
        val shortSide = minOf(width, height).toFloat()
        val scale = minOf(1f, MAX_OUTPUT_LONG_SIDE / longSide, MAX_OUTPUT_SHORT_SIDE / shortSide)
        return Size((width * scale).toEven(), (height * scale).toEven())
    }

    private fun Float.toEven(): Int = max(2, (this / 2f).roundToInt() * 2)

    /**
     * Slot size rounded up to even pixels: neighbouring split slots overlap by a pixel rather
     * than leaving a seam.
     */
    private fun CombinerRect.evenSize() = Size(width.toEvenUp(), height.toEvenUp())

    private fun Float.toEvenUp(): Int = max(2, ceil(this / 2f).toInt() * 2)

    private fun hasAudio(uri: Uri): Boolean = withRetriever(uri) {
        extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
    }

    /** Saves the (upright) last frame of [source] as a JPEG in the cache dir. */
    private fun saveLastFrame(source: Source): File {
        val bitmap = withRetriever(source.uri) {
            val exactLastFrame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { count -> runCatching { getFrameAtIndex(count - 1) }.getOrNull() }
            } else {
                null
            }
            exactLastFrame
                ?: getFrameAtTime(source.durationUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: getFrameAtTime(source.durationUs, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC)
                ?: error("Can't read the last frame of ${source.uri}")
        }
        val file = File(context.cacheDir, "combine_last_${System.nanoTime()}.jpg")
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private inline fun <T> withRetriever(uri: Uri, block: MediaMetadataRetriever.() -> T): T {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            return retriever.block()
        } finally {
            retriever.release()
        }
    }
}
