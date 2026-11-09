package com.looper.base.screen.home

import androidx.lifecycle.viewModelScope
import com.looper.base.base.mvi.MviViewModel
import com.looper.base.deeplink.IncomingLink
import com.looper.base.deeplink.IncomingLinkDispatcher
import com.looper.base.deeplink.IncomingLinkParser
import com.looper.base.player.PlayerConnection
import com.looper.base.player.source.MediaResolver
import com.looper.base.player.source.StreamRepository
import com.looper.base.screen.home.data.HomeEffect
import com.looper.base.screen.home.data.HomeIntent
import com.looper.base.screen.home.data.HomeUiState
import com.looper.base.screen.home.data.LinkError
import com.looper.base.screen.home.data.SocialAppLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

class HomeViewModel(
    private val socialAppLauncher: SocialAppLauncher,
    private val streamRepository: StreamRepository,
    private val playerConnection: PlayerConnection,
    private val incomingLinkDispatcher: IncomingLinkDispatcher,
) : MviViewModel<HomeUiState, HomeIntent, HomeEffect>(
    initialState = HomeUiState(),
) {

    private var resolveJob: Job? = null
    private var resolvingUrl: String? = null

    init {
        viewModelScope.launch {
            incomingLinkDispatcher.pending
                .filterIsInstance<IncomingLink.SharedUrl>()
                .collect { link ->
                    incomingLinkDispatcher.consume(link = link)
                    setState {
                        copy(link = link.url)
                    }
                    submitLink(text = link.url)
                }
        }
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.LinkChange -> setState {
                copy(link = intent.link)
            }

            HomeIntent.LinkClear -> {
                cancelResolve()
                setState {
                    copy(link = "")
                }
            }

            HomeIntent.LinkSubmit -> submitLink(text = currentState.link)

            HomeIntent.SocialAppsCardClick -> sendEffect(effect = HomeEffect.NavigateToSocialApps)

            is HomeIntent.SocialAppClick -> socialAppLauncher.open(app = intent.app)

            HomeIntent.NowPlayingFavoriteClick,
            HomeIntent.PreviousClick,
            HomeIntent.PlayPauseClick,
            HomeIntent.NextClick,
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
            sendEffect(effect = HomeEffect.ShowLinkError(error = LinkError.INVALID_LINK))
            return
        }
        if (resolveJob?.isActive == true && resolvingUrl == sourceUrl) {
            return
        }
        resolveJob?.cancel()
        resolvingUrl = sourceUrl
        setState {
            copy(isResolvingLink = true)
        }
        resolveJob = viewModelScope.launch {
            try {
                val item = streamRepository.importFromUrl(sourceUrl = sourceUrl)
                playerConnection.playMediaItems(mediaItemIds = listOf(item.id))
                setState {
                    copy(link = "", isResolvingLink = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState {
                    copy(isResolvingLink = false)
                }
                sendEffect(effect = HomeEffect.ShowLinkError(error = e.toLinkError()))
            }
        }
    }

    private fun cancelResolve() {
        resolveJob?.cancel()
        resolveJob = null
        resolvingUrl = null
        setState {
            copy(isResolvingLink = false)
        }
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
