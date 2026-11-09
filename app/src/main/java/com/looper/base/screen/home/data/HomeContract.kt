package com.looper.base.screen.home.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.looper.base.R
import com.looper.base.base.mvi.UiEffect
import com.looper.base.base.mvi.UiIntent
import com.looper.base.base.mvi.UiState

enum class SocialApp(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
    val packageNames: List<String>,
) {
    TikTok(
        iconRes = R.drawable.ic_home_tiktok,
        labelRes = R.string.home_social_tiktok,
        packageNames = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
    ),
    YouTube(
        iconRes = R.drawable.ic_social_youtube,
        labelRes = R.string.home_social_youtube,
        packageNames = listOf("com.google.android.youtube"),
    ),
    YouTubeMusic(
        iconRes = R.drawable.ic_social_youtube_music,
        labelRes = R.string.home_social_youtube_music,
        packageNames = listOf("com.google.android.apps.youtube.music"),
    ),
    ZingMp3(
        iconRes = R.drawable.ic_social_zing_mp3,
        labelRes = R.string.home_social_zing_mp3,
        packageNames = listOf("com.zing.mp3"),
    ),
    SoundCloud(
        iconRes = R.drawable.ic_social_soundcloud,
        labelRes = R.string.home_social_soundcloud,
        packageNames = listOf("com.soundcloud.android"),
    ),
    Facebook(
        iconRes = R.drawable.ic_home_facebook,
        labelRes = R.string.home_social_facebook,
        packageNames = listOf("com.facebook.katana", "com.facebook.lite"),
    ),
    Instagram(
        iconRes = R.drawable.ic_home_instagram,
        labelRes = R.string.home_social_instagram,
        packageNames = listOf("com.instagram.android"),
    ),
    Bandcamp(
        iconRes = R.drawable.ic_social_bandcamp,
        labelRes = R.string.home_social_bandcamp,
        packageNames = listOf("com.bandcamp.android"),
    ),
    Dailymotion(
        iconRes = R.drawable.ic_social_dailymotion,
        labelRes = R.string.home_social_dailymotion,
        packageNames = listOf("com.dailymotion.dailymotion"),
    ),
}

data class NowPlayingUi(
    val title: String,
    val artist: String,
    val positionMs: Long,
    val durationMs: Long,
) {
    val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs).coerceIn(minimumValue = 0f, maximumValue = 1f)
        } else {
            0f
        }

    val remainingMs: Long
        get() = (durationMs - positionMs).coerceAtLeast(minimumValue = 0L)
}

data class PlaylistUi(
    val id: String,
    val artwork: Any?,
    val isFavorite: Boolean,
)

data class RecentDownloadUi(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: Any?,
)

enum class LinkError { INVALID_LINK, PLAYLIST, DRM, NO_STREAM, FAILED }

data class HomeUiState(
    val link: String = "",
    val isResolvingLink: Boolean = false,
    val nowPlaying: NowPlayingUi? = null,
    val socialApps: List<SocialApp> = SocialApp.entries.take(n = 3),
    val playlists: List<PlaylistUi> = emptyList(),
    val recentDownloads: List<RecentDownloadUi> = emptyList(),
) : UiState

sealed interface HomeIntent : UiIntent {
    data class LinkChange(val link: String) : HomeIntent
    data object LinkClear : HomeIntent
    data object LinkSubmit : HomeIntent
    data object NowPlayingFavoriteClick : HomeIntent
    data object PreviousClick : HomeIntent
    data object PlayPauseClick : HomeIntent
    data object NextClick : HomeIntent
    data object SocialAppsCardClick : HomeIntent
    data class SocialAppClick(val app: SocialApp) : HomeIntent
    data object PlaylistsSeeAllClick : HomeIntent
    data class PlaylistClick(val id: String) : HomeIntent
    data class PlaylistFavoriteClick(val id: String) : HomeIntent
    data object RecentDownloadsSeeAllClick : HomeIntent
    data class RecentDownloadClick(val id: String) : HomeIntent
    data class RecentDownloadMoreClick(val id: String) : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data object NavigateToSocialApps : HomeEffect
    data class ShowLinkError(val error: LinkError) : HomeEffect
}
