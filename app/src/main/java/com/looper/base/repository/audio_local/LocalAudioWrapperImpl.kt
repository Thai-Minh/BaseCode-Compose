package com.looper.base.repository.audio_local

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import com.looper.ads.AdsManager
import com.looper.base.repository.audio_local.data.DeviceAudio
import com.looper.base.repository.audio_local.data.LocalAudioItem
import com.looper.local_file_manager.ILocalFileManager
import com.looper.local_file_manager.data.FileManagerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class LocalAudioWrapperImpl(
    private val localFileManager: ILocalFileManager,
    private val contentResolver: ContentResolver,
) : ILocalAudioWrapper {

    private val albumArtwork = "content://media/external/audio/albumart".toUri()

    override val localAudio: Flow<LocalAudioItem> = localFileManager.audioFileItem
        .distinctUntilChanged()
        .map { item ->
            try {
                val mediaByPath = item.list.filter { it.size > 0 }.associateBy { it.path }
                val audio = mutableListOf<DeviceAudio>()
                if (mediaByPath.isNotEmpty()) {
                    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    // The manager supplies the library; MediaStore only enriches matching files.
                    val cursor = checkNotNull(
                        contentResolver.query(
                            collection,
                            arrayOf(
                                MediaStore.Audio.Media._ID,
                                MediaStore.Audio.Media.DATA,
                                MediaStore.Audio.Media.TITLE,
                                MediaStore.Audio.Media.ARTIST,
                                MediaStore.Audio.Media.ALBUM_ID,
                            ),
                            "${MediaStore.Audio.Media.SIZE} > 0",
                            null,
                            null,
                        ),
                    ) { "Unable to query audio metadata" }

                    cursor.use {
                        val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                        val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                        val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                        while (it.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val media = mediaByPath[it.getString(pathColumn)] ?: continue
                            val id = it.getLong(idColumn)
                            val name = it.getString(titleColumn)?.trim()?.takeIf { it.isNotEmpty() }
                                ?: media.name.trim().takeIf { it.isNotEmpty() }
                                ?: "Audio $id"
                            val artist = it.getString(artistColumn)?.trim()
                                ?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }
                                ?: "Unknown artist"
                            val albumId = it.getLong(albumIdColumn)
                            audio.add(
                                DeviceAudio(
                                    id = id,
                                    name = name,
                                    artist = artist,
                                    uri = ContentUris.withAppendedId(collection, id),
                                    artworkUri = if (albumId > 0) {
                                        ContentUris.withAppendedId(albumArtwork, albumId)
                                    } else {
                                        null
                                    },
                                    durationMs = (media.duration ?: 0).coerceAtLeast(0),
                                ),
                            )
                        }
                    }
                }
                LocalAudioItem(
                    state = if (item.state == FileManagerState.Success && audio.isEmpty()) {
                        FileManagerState.Empty
                    } else {
                        item.state
                    },
                    audio = audio.sortedWith(
                        compareBy<DeviceAudio, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                            .thenBy { it.name }
                            .thenBy { it.id },
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                LocalAudioItem(state = FileManagerState.Failed, audio = emptyList())
            }
        }
        .flowOn(Dispatchers.IO)

    override fun fetch() = localFileManager.fetch()

    override suspend fun getDuration(path: String): Long? = localFileManager.getDuration(path = path)

    override suspend fun deleteFile(
        activity: ComponentActivity,
        path: String
    ): Boolean {
        AdsManager.ignoreShowAppOpenAdOnetime()

        return localFileManager.deleteFile(activity = activity, path = path)
    }

    override suspend fun deleteFiles(
        activity: ComponentActivity,
        paths: List<String>
    ): Boolean {
        AdsManager.ignoreShowAppOpenAdOnetime()

        return localFileManager.deleteFiles(activity = activity, paths = paths)
    }
}
