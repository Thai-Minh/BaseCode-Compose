package com.looper.base.widget

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.media3.common.Player
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PlaybackWidgetUpdater(
    private val context: Context,
    private val player: Player,
    private val scope: CoroutineScope,
    private val releaseScope: CoroutineScope,
) {

    private data class Snapshot(
        val hasMedia: Boolean,
        val title: String?,
        val artist: String?,
        val isPlaying: Boolean,
        val artworkUri: Uri?,
    )

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val ARTWORK_SIZE_PX = 256
        const val ARTWORK_FILE = "widget/artwork.png"
    }

    private var job: Job? = null
    private var renderedArtworkUri: Uri? = null
    private var artworkVersion = 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                )
            ) {
                schedule()
            }
        }
    }

    init {
        player.addListener(listener)
        schedule()
    }

    fun release() {
        job?.cancel()
        player.removeListener(listener)
        val snapshot = snapshot().copy(isPlaying = false)
        releaseScope.launch {
            write(snapshot = snapshot)
        }
    }

    private fun schedule() {
        job?.cancel()
        job = scope.launch {
            delay(timeMillis = DEBOUNCE_MS)
            write(snapshot = snapshot())
        }
    }

    private fun snapshot(): Snapshot {
        val metadata = player.mediaMetadata
        return Snapshot(
            hasMedia = player.currentMediaItem != null,
            title = metadata.title?.toString(),
            artist = metadata.artist?.toString(),
            isPlaying = player.isPlaying,
            artworkUri = metadata.artworkUri,
        )
    }

    private suspend fun write(snapshot: Snapshot) {
        val ids = runCatching {
            GlanceAppWidgetManager(context).getGlanceIds(provider = PlaybackWidget::class.java)
        }.getOrDefault(defaultValue = emptyList())
        if (ids.isEmpty()) {
            return
        }
        val artworkPath = artworkFor(uri = snapshot.artworkUri)
        ids.forEach { id ->
            updateAppWidgetState(context = context, glanceId = id) { preferences ->
                preferences[PlaybackWidgetKeys.HAS_MEDIA] = snapshot.hasMedia
                preferences[PlaybackWidgetKeys.IS_PLAYING] = snapshot.isPlaying
                preferences[PlaybackWidgetKeys.TITLE] = snapshot.title.orEmpty()
                preferences[PlaybackWidgetKeys.ARTIST] = snapshot.artist.orEmpty()
                if (artworkPath == null) {
                    preferences.remove(key = PlaybackWidgetKeys.ARTWORK_PATH)
                } else {
                    preferences[PlaybackWidgetKeys.ARTWORK_PATH] = artworkPath
                }
                preferences[PlaybackWidgetKeys.ARTWORK_VERSION] = artworkVersion
            }
            PlaybackWidget().update(context = context, id = id)
        }
    }

    private suspend fun artworkFor(uri: Uri?): String? {
        val file = File(context.filesDir, ARTWORK_FILE)
        if (uri == null) {
            renderedArtworkUri = null
            return null
        }
        if (uri == renderedArtworkUri && file.exists()) {
            return file.absolutePath
        }
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(ARTWORK_SIZE_PX)
            .allowHardware(enable = false)
            .build()
        val bitmap = (SingletonImageLoader.get(context)
            .execute(request) as? SuccessResult)?.image?.toBitmap()
            ?: return null
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        renderedArtworkUri = uri
        artworkVersion++
        return file.absolutePath
    }
}
