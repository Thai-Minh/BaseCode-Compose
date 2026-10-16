package com.looper.base.audio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.looper.base.R
import com.looper.base.activities.MainActivity
import com.looper.base.audio.playback.AudioPlaybackSnapshot
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@androidx.annotation.OptIn(UnstableApi::class)
object AudioHomeWidget {
    const val ACTION_PREVIOUS = "com.looper.base.audio.widget.PREVIOUS"
    const val ACTION_TOGGLE_PLAY_PAUSE = "com.looper.base.audio.widget.TOGGLE_PLAY_PAUSE"
    const val ACTION_NEXT = "com.looper.base.audio.widget.NEXT"

    private const val THUMBNAIL_SIZE = 160
    private val imageExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val artworkGeneration = AtomicInteger()

    fun update(context: Context, player: Player) {
        if (player.currentMediaItem == null) return clear(context)
        update(
            context,
            player.mediaMetadata.title,
            player.mediaMetadata.artist,
            player.mediaMetadata.artworkUri,
            !Util.shouldShowPlayButton(player),
        )
    }

    internal fun update(context: Context, snapshot: AudioPlaybackSnapshot) {
        update(context, snapshot.title, snapshot.artist, snapshot.artworkUri, isPlaying = false)
    }

    private fun update(
        context: Context,
        title: CharSequence?,
        artist: CharSequence?,
        artworkUri: Uri?,
        isPlaying: Boolean,
    ) {
        val appContext = context.applicationContext
        val ids = widgetIds(appContext)
        if (ids.isEmpty()) return
        val generation = artworkGeneration.incrementAndGet()
        val contentArtworkUri = artworkUri?.takeIf { it.scheme == "content" }
        val views = baseViews(appContext, active = true).apply {
            setTextViewText(
                R.id.audio_widget_title,
                title?.takeIf { it.isNotBlank() } ?: appContext.getString(R.string.audio_widget_idle)
            )
            setTextViewText(R.id.audio_widget_artist, artist?.toString().orEmpty())
            setImageViewResource(
                R.id.audio_widget_toggle,
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            setContentDescription(
                R.id.audio_widget_toggle,
                appContext.getString(if (isPlaying) R.string.audio_pause else R.string.audio_play)
            )
        }
        AppWidgetManager.getInstance(appContext).updateAppWidget(ids, views)

        if (contentArtworkUri != null) {
            imageExecutor.execute {
                val bitmap = decodeArtwork(appContext, contentArtworkUri) ?: return@execute
                mainHandler.post {
                    if (artworkGeneration.get() != generation) return@post
                    val artworkViews = RemoteViews(appContext.packageName, R.layout.audio_home_widget).apply {
                        setImageViewBitmap(R.id.audio_widget_artwork, bitmap)
                        setViewVisibility(R.id.audio_widget_artwork, View.VISIBLE)
                        setViewVisibility(R.id.audio_widget_placeholder, View.GONE)
                    }
                    AppWidgetManager.getInstance(appContext).partiallyUpdateAppWidget(ids, artworkViews)
                }
            }
        }
    }

    fun clear(context: Context) {
        val appContext = context.applicationContext
        artworkGeneration.incrementAndGet()
        val ids = widgetIds(appContext)
        if (ids.isNotEmpty()) {
            AppWidgetManager.getInstance(appContext).updateAppWidget(ids, baseViews(appContext))
        }
    }

    private fun widgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, AudioHomeWidgetProvider::class.java))

    private fun baseViews(context: Context, active: Boolean = false): RemoteViews {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun command(action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AudioHomeWidgetProvider::class.java)
                .setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return RemoteViews(context.packageName, R.layout.audio_home_widget).apply {
            setTextViewText(R.id.audio_widget_title, context.getString(R.string.audio_widget_idle))
            setTextViewText(R.id.audio_widget_artist, "")
            setImageViewResource(R.id.audio_widget_toggle, R.drawable.ic_play)
            setContentDescription(R.id.audio_widget_toggle, context.getString(R.string.audio_play))
            setViewVisibility(R.id.audio_widget_artwork, View.GONE)
            setViewVisibility(R.id.audio_widget_placeholder, View.VISIBLE)
            setOnClickPendingIntent(R.id.audio_widget_root, openApp)
            setOnClickPendingIntent(
                R.id.audio_widget_previous,
                if (active) command(ACTION_PREVIOUS, 1) else openApp
            )
            setOnClickPendingIntent(
                R.id.audio_widget_toggle,
                if (active) command(ACTION_TOGGLE_PLAY_PAUSE, 2) else openApp
            )
            setOnClickPendingIntent(
                R.id.audio_widget_next,
                if (active) command(ACTION_NEXT, 3) else openApp
            )
        }
    }

    private fun decodeArtwork(context: Context, uri: Uri): Bitmap? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val thumbnail = context.contentResolver.loadThumbnail(
                        uri, Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE), null
                    )
                    if (thumbnail.width <= THUMBNAIL_SIZE && thumbnail.height <= THUMBNAIL_SIZE) {
                        return thumbnail
                    }
                    return Bitmap.createScaledBitmap(
                        thumbnail,
                        (thumbnail.width * THUMBNAIL_SIZE.toFloat() / maxOf(thumbnail.width, thumbnail.height))
                            .toInt().coerceAtLeast(1),
                        (thumbnail.height * THUMBNAIL_SIZE.toFloat() / maxOf(thumbnail.width, thumbnail.height))
                            .toInt().coerceAtLeast(1),
                        true
                    ).also { if (it !== thumbnail) thumbnail.recycle() }
                } catch (_: Exception) {
                    // Some album-art content providers expose a stream but not loadThumbnail.
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > THUMBNAIL_SIZE) {
                    inSampleSize *= 2
                }
            }
            return context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (_: Exception) {
            return null
        } catch (_: OutOfMemoryError) {
            return null
        }
    }
}
