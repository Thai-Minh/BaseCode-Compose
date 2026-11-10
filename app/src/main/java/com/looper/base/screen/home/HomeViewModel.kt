package com.looper.base.screen.home

import androidx.lifecycle.viewModelScope
import com.looper.base.base.mvi.MviViewModel
import com.looper.base.base.storage.homeTourDone
import com.looper.base.base.storage.updateHomeTourDone
import com.looper.base.deeplink.IncomingLink
import com.looper.base.deeplink.IncomingLinkDispatcher
import com.looper.base.deeplink.IncomingLinkParser
import com.looper.base.library.data.LibraryRepository
import com.looper.base.player.PlayerConnection
import com.looper.base.screen.home.data.HomeEffect
import com.looper.base.screen.home.data.HomeIntent
import com.looper.base.screen.home.data.HomeUiState
import com.looper.base.screen.home.data.HomeTourStep
import com.looper.base.screen.home.data.NowPlayingUi
import com.looper.base.screen.home.data.SocialAppLauncher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val socialAppLauncher: SocialAppLauncher,
    private val playerConnection: PlayerConnection,
    private val libraryRepository: LibraryRepository,
    private val incomingLinkDispatcher: IncomingLinkDispatcher,
) : MviViewModel<HomeUiState, HomeIntent, HomeEffect>(
    initialState = HomeUiState(),
) {

    init {
        viewModelScope.launch {
            if (!homeTourDone.first()) {
                setState {
                    copy(tourStep = HomeTourStep.INTRO)
                }
            }
        }
        viewModelScope.launch {
            val isFavorite = playerConnection.state
                .map { player ->
                    player.mediaId
                }
                .distinctUntilChanged()
                .flatMapLatest { mediaId ->
                    if (mediaId == null) {
                        flowOf(value = false)
                    } else {
                        libraryRepository.observeMediaItem(id = mediaId).map { item ->
                            item?.isFavorite == true
                        }
                    }
                }
            combine(
                playerConnection.state,
                playerConnection.position,
                isFavorite,
            ) { player, positionMs, favorite ->
                if (player.hasMedia) {
                    NowPlayingUi(
                        title = player.title.orEmpty(),
                        artist = player.artist.orEmpty(),
                        positionMs = positionMs,
                        durationMs = player.durationMs,
                        isFavorite = favorite,
                        isPlaying = player.isPlaying,
                    )
                } else {
                    null
                }
            }
                .distinctUntilChanged()
                .collect { nowPlaying ->
                    setState {
                        copy(nowPlaying = nowPlaying)
                    }
                }
        }
        viewModelScope.launch {
            incomingLinkDispatcher.pending
                .filterIsInstance<IncomingLink.SharedUrl>()
                .collect { link ->
                    incomingLinkDispatcher.consume(link = link)
                    submitLink(text = link.url)
                }
        }
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.LinkChange -> setState {
                copy(link = intent.link)
            }

            is HomeIntent.LinkPaste -> {
                setState {
                    copy(link = intent.link)
                }
                submitLink(text = intent.link)
            }

            HomeIntent.LinkClear -> setState {
                copy(link = "")
            }

            HomeIntent.LinkSubmit -> submitLink(text = currentState.link)

            HomeIntent.SocialAppsCardClick -> sendEffect(effect = HomeEffect.NavigateToSocialApps)

            is HomeIntent.SocialAppClick -> socialAppLauncher.open(app = intent.app)

            HomeIntent.NowPlayingFavoriteClick -> if (currentState.nowPlaying != null) {
                playerConnection.toggleFavorite()
            }

            HomeIntent.PreviousClick -> if (currentState.nowPlaying != null) {
                playerConnection.skipToPrevious()
            }

            HomeIntent.PlayPauseClick -> if (currentState.nowPlaying != null) {
                playerConnection.togglePlayPause()
            }

            HomeIntent.NextClick -> if (currentState.nowPlaying != null) {
                playerConnection.skipToNext()
            }

            HomeIntent.TourNext -> advanceTour()

            HomeIntent.TourSkip -> finishTour()

            HomeIntent.PlaylistsSeeAllClick,
            is HomeIntent.PlaylistClick,
            is HomeIntent.PlaylistFavoriteClick,
            HomeIntent.RecentDownloadsSeeAllClick,
            is HomeIntent.RecentDownloadClick,
            is HomeIntent.RecentDownloadMoreClick -> Unit
        }
    }

    private fun submitLink(text: String) {
        val sourceUrl = IncomingLinkParser.extractUrl(text = text)
        if (sourceUrl == null) {
            sendEffect(effect = HomeEffect.ShowInvalidLink)
            return
        }
        setState {
            copy(link = "")
        }
        sendEffect(effect = HomeEffect.NavigateToAudioPreview(sourceUrl = sourceUrl))
    }

    private fun advanceTour() {
        val next = when (currentState.tourStep) {
            HomeTourStep.INTRO -> HomeTourStep.PASTE_LINK
            HomeTourStep.PASTE_LINK -> HomeTourStep.NOW_PLAYING
            HomeTourStep.NOW_PLAYING -> if (currentState.socialApps.isNotEmpty()) {
                HomeTourStep.SOCIAL_APPS
            } else {
                null
            }

            HomeTourStep.SOCIAL_APPS,
            null -> null
        }
        if (next == null) {
            finishTour()
        } else {
            setState {
                copy(tourStep = next)
            }
        }
    }

    private fun finishTour() {
        setState {
            copy(tourStep = null)
        }
        updateHomeTourDone(value = true)
    }
}
