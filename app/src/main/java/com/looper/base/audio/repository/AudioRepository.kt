package com.looper.base.audio.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.core.net.toUri
import com.looper.base.audio.model.DeviceAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AudioRepository(context: Context) {
    private val contentResolver = context.applicationContext.contentResolver

    suspend fun getDeviceAudio(): List<DeviceAudio> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val albumArtwork = "content://media/external/audio/albumart".toUri()
        val audio = mutableListOf<DeviceAudio>()

        // Include recordings, podcasts and other audio, not just IS_MUSIC entries.
        val cursor = checkNotNull(
            contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.SIZE} > 0",
                null,
                null,
            ),
        ) { "Unable to query device audio" }

        cursor.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val displayNameColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (it.moveToNext()) {
                ensureActive()
                val id = it.getLong(idColumn)
                val name = it.getString(titleColumn)?.trim()?.takeIf { title -> title.isNotEmpty() }
                    ?: it.getString(displayNameColumn)?.trim()
                        ?.takeIf { displayName -> displayName.isNotEmpty() }
                    ?: "Audio $id"
                val artist = it.getString(artistColumn)?.trim()
                    ?.takeIf { artist -> artist.isNotEmpty() && artist != MediaStore.UNKNOWN_STRING }
                    ?: "Unknown artist"
                val albumId = it.getLong(albumIdColumn)

                audio.add(
                    DeviceAudio(
                        id = id,
                        name = name,
                        artist = artist,
                        uri = ContentUris.withAppendedId(collection, id),
                        // An album ID does not guarantee artwork; consumers need a fallback.
                        artworkUri = if (albumId > 0) {
                            ContentUris.withAppendedId(albumArtwork, albumId)
                        } else {
                            null
                        },
                        durationMs = it.getLong(durationColumn).coerceAtLeast(0),
                    ),
                )
            }
        }

        audio.sortedWith(
            compareBy<DeviceAudio, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.name }
                .thenBy { it.id },
        )
    }
}
