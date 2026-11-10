package com.looper.base.screen.audiopreview

import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.looper.base.base.mvi.MviViewModel
import com.looper.base.database.data.DownloadKind
import com.looper.base.database.data.DownloadStatus
import com.looper.base.database.data.MediaItemEntity
import com.looper.base.download.data.DownloadRepository
import com.looper.base.library.data.LibraryRepository
import com.looper.base.player.PlayerConnection
import com.looper.base.player.source.MediaResolver
import com.looper.base.player.source.StreamRepository
import com.looper.base.screen.audiopreview.data.AudioPreviewEffect
import com.looper.base.screen.audiopreview.data.AudioPreviewIntent
import com.looper.base.screen.audiopreview.data.AudioPreviewUiState
import com.looper.base.screen.audiopreview.data.CreatePlaylistUi
import com.looper.base.screen.audiopreview.data.LinkError
import com.looper.base.screen.audiopreview.data.PlaylistOptionUi
import com.looper.base.screen.audiopreview.data.PreviewDownloadState
import com.looper.base.screen.audiopreview.data.PreviewTrackUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPreviewViewModel(
    private val sourceUrl: String,
    private val streamRepository: StreamRepository,
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository,
    private val playerConnection: PlayerConnection,
) : MviViewModel<AudioPreviewUiState, AudioPreviewIntent, AudioPreviewEffect>(
    initialState = AudioPreviewUiState(),
) {

    private companion object {
        const val PROGRESS_TICK_MS = 150L
        const val PROGRESS_CAP = 92
        const val PROGRESS_EASING = 12
        const val COMPLETE_HOLD_MS = 300L
        const val COLLAGE_SIZE = 3
    }

    init {
        extract()
    }

    override fun onIntent(intent: AudioPreviewIntent) {
        when (intent) {
            AudioPreviewIntent.BackClick -> sendEffect(effect = AudioPreviewEffect.NavigateBack)

            AudioPreviewIntent.FavoriteClick -> currentState.track?.let { track ->
                val favorite = !currentState.isFavorite
                viewModelScope.launch {
                    libraryRepository.setFavorite(mediaItemIds = listOf(track.id), favorite = favorite)
                }
            }

            AudioPreviewIntent.PlayClick -> currentState.track?.let { track ->
                if (playerConnection.state.value.mediaId == track.id) {
                    playerConnection.togglePlayPause()
                } else {
                    playerConnection.playMediaItems(mediaItemIds = listOf(track.id))
                }
            }

            AudioPreviewIntent.DownloadClick -> currentState.track?.let { track ->
                if (currentState.download == PreviewDownloadState.IDLE) {
                    setState {
                        copy(download = PreviewDownloadState.DOWNLOADING)
                    }
                    viewModelScope.launch {
                        val job = downloadRepository.enqueue(mediaItemId = track.id, kind = DownloadKind.AUDIO)
                        if (job == null && currentState.download == PreviewDownloadState.DOWNLOADING) {
                            setState {
                                copy(download = PreviewDownloadState.IDLE)
                            }
                        }
                    }
                }
            }

            AudioPreviewIntent.SourceClick -> currentState.track?.let { track ->
                sendEffect(effect = AudioPreviewEffect.OpenUrl(url = track.sourceUrl))
            }

            AudioPreviewIntent.AddToPlaylistClick -> setState {
                copy(isPlaylistSheetVisible = true)
            }

            AudioPreviewIntent.PlaylistSheetDismiss -> setState {
                copy(isPlaylistSheetVisible = false)
            }

            is AudioPreviewIntent.PlaylistToggle -> togglePlaylist(playlistId = intent.playlistId)

            AudioPreviewIntent.CreatePlaylistClick -> setState {
                copy(createPlaylist = CreatePlaylistUi())
            }

            is AudioPreviewIntent.CreatePlaylistNameChange -> setState {
                copy(createPlaylist = createPlaylist?.copy(name = intent.name))
            }

            is AudioPreviewIntent.CreatePlaylistGenreSelect -> setState {
                copy(createPlaylist = createPlaylist?.copy(genre = intent.genre))
            }

            AudioPreviewIntent.CreatePlaylistDismiss -> setState {
                copy(createPlaylist = null)
            }

            AudioPreviewIntent.CreatePlaylistConfirm -> createPlaylist()
        }
    }

    private fun extract() {
        viewModelScope.launch {
            val ticker = launch {
                while (true) {
                    delay(timeMillis = PROGRESS_TICK_MS)
                    setState {
                        val step = ((PROGRESS_CAP - progress) / PROGRESS_EASING).coerceAtLeast(minimumValue = 1)
                        copy(progress = (progress + step).coerceAtMost(maximumValue = PROGRESS_CAP))
                    }
                }
            }
            try {
                val item = streamRepository.importFromUrl(sourceUrl = sourceUrl)
                ticker.cancel()
                setState {
                    copy(progress = 100)
                }
                delay(timeMillis = COMPLETE_HOLD_MS)
                val platformName = item.platformId?.let { platformId ->
                    libraryRepository.platformName(platformId = platformId)
                }
                setState {
                    copy(track = item.toPreviewTrack(platformName = platformName))
                }
                observeTrack(mediaItemId = item.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ticker.cancel()
                sendEffect(effect = AudioPreviewEffect.ShowLinkError(error = e.toLinkError()))
                sendEffect(effect = AudioPreviewEffect.NavigateBack)
            }
        }
    }

    private fun observeTrack(mediaItemId: String) {
        viewModelScope.launch {
            libraryRepository.observeMediaItem(id = mediaItemId)
                .map { item ->
                    item?.isFavorite == true
                }
                .distinctUntilChanged()
                .collect { favorite ->
                    setState {
                        copy(isFavorite = favorite)
                    }
                }
        }
        viewModelScope.launch {
            playerConnection.state
                .map { player ->
                    player.mediaId == mediaItemId && player.isPlaying
                }
                .distinctUntilChanged()
                .collect { playing ->
                    setState {
                        copy(isPlaying = playing)
                    }
                }
        }
        viewModelScope.launch {
            combine(
                downloadRepository.files.map { files ->
                    files.any { file ->
                        file.mediaItemId == mediaItemId && file.kind == DownloadKind.AUDIO
                    }
                },
                downloadRepository.jobs.map { jobs ->
                    jobs.filter { job ->
                        job.mediaItemId == mediaItemId && job.targetKind == DownloadKind.AUDIO
                    }.maxByOrNull { job ->
                        job.createdAt
                    }?.status
                },
            ) { downloaded, status ->
                downloaded to status
            }
                .distinctUntilChanged()
                .collect { (downloaded, status) ->
                    val download = when {
                        downloaded -> PreviewDownloadState.DONE
                        status == DownloadStatus.QUEUED || status == DownloadStatus.RUNNING -> PreviewDownloadState.DOWNLOADING
                        else -> PreviewDownloadState.IDLE
                    }
                    if (currentState.download == PreviewDownloadState.DOWNLOADING && status == DownloadStatus.FAILED) {
                        sendEffect(effect = AudioPreviewEffect.ShowDownloadFailed)
                    }
                    if (download != PreviewDownloadState.IDLE || status != null) {
                        setState {
                            copy(download = download)
                        }
                    }
                }
        }
        viewModelScope.launch {
            combine(
                observePlaylistsWithThumbnails(),
                libraryRepository.observePlaylistIdsContaining(mediaItemId = mediaItemId),
            ) { playlists, selectedIds ->
                playlists.map { option ->
                    option.copy(isSelected = option.id in selectedIds)
                }
            }.collect { playlists ->
                setState {
                    copy(playlists = playlists)
                }
            }
        }
    }

    private fun observePlaylistsWithThumbnails(): Flow<List<PlaylistOptionUi>> =
        libraryRepository.observePlaylistSummaries().flatMapLatest { summaries ->
            if (summaries.isEmpty()) {
                flowOf(value = emptyList())
            } else {
                combine(
                    summaries.map { summary ->
                        libraryRepository.observePlaylistThumbnails(
                            playlistId = summary.playlist.id,
                            limit = COLLAGE_SIZE,
                        ).map { thumbnails ->
                            PlaylistOptionUi(
                                id = summary.playlist.id,
                                name = summary.playlist.name,
                                itemCount = summary.itemCount,
                                cover = summary.playlist.thumbnailPath?.let { path ->
                                    File(path)
                                },
                                thumbnails = thumbnails,
                                isSelected = false,
                            )
                        }
                    }
                ) { options ->
                    options.toList()
                }
            }
        }

    private fun togglePlaylist(playlistId: String) {
        val track = currentState.track ?: return
        val selected = currentState.playlists.any { option ->
            option.id == playlistId && option.isSelected
        }
        viewModelScope.launch {
            if (selected) {
                libraryRepository.removeFromPlaylist(playlistId = playlistId, mediaItemIds = listOf(track.id))
            } else {
                libraryRepository.addToPlaylist(playlistId = playlistId, mediaItemIds = listOf(track.id))
            }
        }
    }

    private fun createPlaylist() {
        val track = currentState.track ?: return
        val form = currentState.createPlaylist ?: return
        if (!form.canCreate) {
            return
        }
        setState {
            copy(createPlaylist = null)
        }
        viewModelScope.launch {
            val playlistId = libraryRepository.createPlaylist(
                name = form.name.trim(),
                genre = form.genre?.name,
            )
            libraryRepository.addToPlaylist(playlistId = playlistId, mediaItemIds = listOf(track.id))
        }
    }

    private fun MediaItemEntity.toPreviewTrack(platformName: String?): PreviewTrackUi {
        val host = sourceUrl.toUri().host?.removePrefix(prefix = "www.")
        return PreviewTrackUi(
            id = id,
            title = title,
            artist = artist.orEmpty(),
            artwork = thumbnailUrl,
            sourceUrl = sourceUrl,
            sourceLabel = sourceUrl.substringAfter(delimiter = "://").removePrefix(prefix = "www."),
            platformName = platformName ?: host ?: sourceUrl,
        )
    }

    private fun Exception.toLinkError(): LinkError =
        when ((this as? MediaResolver.ResolveException)?.reason) {
            MediaResolver.Reason.PLAYLIST -> LinkError.PLAYLIST
            MediaResolver.Reason.DRM -> LinkError.DRM
            MediaResolver.Reason.NO_STREAM -> LinkError.NO_STREAM
            MediaResolver.Reason.NOT_FOUND,
            MediaResolver.Reason.UNKNOWN,
            null -> LinkError.FAILED
        }
}
