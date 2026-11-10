package com.looper.base.screen.audiopreview.data

import com.looper.base.base.mvi.UiEffect
import com.looper.base.base.mvi.UiIntent
import com.looper.base.base.mvi.UiState
import com.looper.base.library.model.PlaylistGenre

enum class LinkError { PLAYLIST, DRM, NO_STREAM, FAILED }

enum class PreviewDownloadState { IDLE, DOWNLOADING, DONE }

data class PreviewTrackUi(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: Any?,
    val sourceUrl: String,
    val sourceLabel: String,
    val platformName: String,
)

data class PlaylistOptionUi(
    val id: String,
    val name: String,
    val itemCount: Int,
    val cover: Any?,
    val thumbnails: List<String>,
    val isSelected: Boolean,
)

data class CreatePlaylistUi(
    val name: String = "",
    val genre: PlaylistGenre? = null,
) {
    val canCreate: Boolean
        get() = name.isNotBlank()
}

data class AudioPreviewUiState(
    val progress: Int = 0,
    val track: PreviewTrackUi? = null,
    val isFavorite: Boolean = false,
    val isPlaying: Boolean = false,
    val download: PreviewDownloadState = PreviewDownloadState.IDLE,
    val isPlaylistSheetVisible: Boolean = false,
    val playlists: List<PlaylistOptionUi> = emptyList(),
    val createPlaylist: CreatePlaylistUi? = null,
) : UiState

sealed interface AudioPreviewIntent : UiIntent {
    data object BackClick : AudioPreviewIntent
    data object FavoriteClick : AudioPreviewIntent
    data object PlayClick : AudioPreviewIntent
    data object DownloadClick : AudioPreviewIntent
    data object SourceClick : AudioPreviewIntent
    data object AddToPlaylistClick : AudioPreviewIntent
    data object PlaylistSheetDismiss : AudioPreviewIntent
    data class PlaylistToggle(val playlistId: String) : AudioPreviewIntent
    data object CreatePlaylistClick : AudioPreviewIntent
    data class CreatePlaylistNameChange(val name: String) : AudioPreviewIntent
    data class CreatePlaylistGenreSelect(val genre: PlaylistGenre) : AudioPreviewIntent
    data object CreatePlaylistDismiss : AudioPreviewIntent
    data object CreatePlaylistConfirm : AudioPreviewIntent
}

sealed interface AudioPreviewEffect : UiEffect {
    data object NavigateBack : AudioPreviewEffect
    data class ShowLinkError(val error: LinkError) : AudioPreviewEffect
    data class OpenUrl(val url: String) : AudioPreviewEffect
    data object ShowDownloadFailed : AudioPreviewEffect
}
