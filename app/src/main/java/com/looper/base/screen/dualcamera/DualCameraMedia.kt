package com.looper.base.screen.dualcamera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageProxy
import androidx.camera.video.MediaStoreOutputOptions
import com.looper.base.screen.dualcamera.data.LayoutRect
import com.looper.base.screen.dualcamera.data.PreviewLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Bitmap / MediaStore helpers for the dual camera feature.
 */
object DualCameraMedia {

    private const val ALBUM_NAME = "DualCamera"

    /**
     * Converts an [ImageProxy] into an upright [Bitmap] and closes the proxy.
     *
     * @param mirror mirror horizontally so the front camera photo matches its (mirrored) preview.
     */
    fun ImageProxy.toUprightBitmap(mirror: Boolean): Bitmap = use { image ->
        rotateAndMirror(image.toBitmap(), image.imageInfo.rotationDegrees, mirror)
    }

    /** Rotates [source] clockwise by [rotationDegrees], then optionally mirrors it. Recycles [source]. */
    fun rotateAndMirror(source: Bitmap, rotationDegrees: Int, mirror: Boolean): Bitmap {
        if (rotationDegrees == 0 && !mirror) return source

        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            if (mirror) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
            if (it !== source) source.recycle()
        }
    }

    /**
     * Renders the photo exactly as the preview looked on screen (WYSIWYG), for any
     * [PreviewLayout] (split halves or full screen + PiP):
     * - each camera is center-cropped into its rectangle, like the viewfinder's
     *   `ContentScale.Crop` + `Alignment.Center`; [second] is drawn on top with rounded corners;
     * - a camera that delivered no frame (null) is painted [PreviewLayout.FAILED_CAMERA_COLOR],
     *   like on screen; the viewport background is black.
     *
     * Output resolution follows [main]'s native resolution in its rectangle (else [second]'s),
     * capped at [MAX_OUTPUT_PIXELS]; every on-screen measure in [layout] is scaled to it.
     */
    fun composeLikePreview(main: Bitmap?, second: Bitmap?, layout: PreviewLayout): Bitmap {
        // Image px per on-screen px.
        val reference = when {
            main != null -> main to layout.main
            second != null -> second to layout.second
            else -> null
        }
        var toImage = if (reference != null) {
            val (bitmap, rect) = reference
            // Inverse of the Crop scale that fits the bitmap into its on-screen rectangle.
            1f / max(rect.width / bitmap.width, rect.height / bitmap.height)
        } else {
            FALLBACK_OUTPUT_WIDTH / layout.viewportWidth
        }
        val pixels = layout.viewportWidth * layout.viewportHeight * toImage * toImage
        if (pixels > MAX_OUTPUT_PIXELS) toImage *= sqrt(MAX_OUTPUT_PIXELS / pixels)

        val outWidth = (layout.viewportWidth * toImage).roundToInt().coerceAtLeast(1)
        val outHeight = (layout.viewportHeight * toImage).roundToInt().coerceAtLeast(1)

        val result = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(android.graphics.Color.BLACK)
        drawSlot(canvas, main, layout.main, cornerRadius = 0f, toImage)
        drawSlot(canvas, second, layout.second, layout.secondCornerRadius, toImage)
        return result
    }

    private fun drawSlot(
        canvas: Canvas,
        bitmap: Bitmap?,
        rect: LayoutRect,
        cornerRadius: Float,
        toImage: Float,
    ) {
        val dst = RectF(
            rect.left * toImage,
            rect.top * toImage,
            rect.right * toImage,
            rect.bottom * toImage,
        )
        val corner = cornerRadius * toImage
        canvas.save()
        if (corner > 0f) {
            canvas.clipPath(Path().apply { addRoundRect(dst, corner, corner, Path.Direction.CW) })
        } else {
            canvas.clipRect(dst)
        }
        if (bitmap != null) {
            canvas.drawBitmap(
                bitmap,
                centerCrop(bitmap.width, bitmap.height, dst.width(), dst.height()),
                dst,
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
            )
        } else {
            canvas.drawColor(PreviewLayout.FAILED_CAMERA_COLOR)
        }
        canvas.restore()
    }

    /** Output width when no camera delivered a frame and there's no native size to keep. */
    private const val FALLBACK_OUTPUT_WIDTH = 1080f

    /** Keeps split layouts (two native-res halves) from producing huge bitmaps. */
    private const val MAX_OUTPUT_PIXELS = 12_000_000f

    /** Source rect of a [srcWidth]x[srcHeight] image that center-crop fills a [dstWidth]x[dstHeight] box. */
    private fun centerCrop(srcWidth: Int, srcHeight: Int, dstWidth: Float, dstHeight: Float): Rect {
        val fit = max(dstWidth / srcWidth, dstHeight / srcHeight)
        val cropWidth = (dstWidth / fit).roundToInt().coerceIn(1, srcWidth)
        val cropHeight = (dstHeight / fit).roundToInt().coerceIn(1, srcHeight)
        val left = (srcWidth - cropWidth) / 2
        val top = (srcHeight - cropHeight) / 2
        return Rect(left, top, left + cropWidth, top + cropHeight)
    }

    /** Saves [bitmap] as JPEG into `Pictures/DualCamera`. Call off the main thread. */
    fun saveJpeg(context: Context, bitmap: Bitmap): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${newFileName()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Failed to create MediaStore entry")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                    "Failed to encode JPEG"
                }
            } ?: error("Failed to open output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** Copies a finished MP4 [file] into `Movies/DualCamera`. Call off the main thread. */
    fun saveVideoFile(context: Context, file: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${newFileName()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/$ALBUM_NAME"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Failed to create MediaStore entry")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { it.copyTo(output) }
            } ?: error("Failed to open output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** Output options for recording into `Movies/DualCamera`. */
    fun videoOutputOptions(context: Context): MediaStoreOutputOptions {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName())
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/$ALBUM_NAME"
                )
            }
        }
        return MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(values)
            .build()
    }

    private fun newFileName(): String =
        "DUAL_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
}
