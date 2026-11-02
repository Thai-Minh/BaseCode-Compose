package com.looper.base.deeplink

import android.content.Intent
import com.looper.base.database.dao.MediaItemDao
import com.looper.base.lastfm.data.LastFmRepository
import com.looper.base.player.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IncomingLinkDispatcher(
    private val playerConnection: PlayerConnection,
    private val mediaItemDao: MediaItemDao,
    private val lastFmRepository: LastFmRepository,
) {

    private companion object {
        const val SEARCH_PLAY_LIMIT = 50
    }

    private val scope = CoroutineScope(context = SupervisorJob() + Dispatchers.Main.immediate)

    val pending: StateFlow<IncomingLink?>
        field = MutableStateFlow(value = null)

    fun handle(intent: Intent?) {
        when (val link = IncomingLinkParser.parse(intent = intent) ?: return) {
            is IncomingLink.PlayFromSearch -> playFromSearch(query = link.query)
            is IncomingLink.LastFmAuth -> scope.launch {
                runCatching {
                    lastFmRepository.completeAuth(token = link.token)
                }
            }

            else -> pending.value = link
        }
    }

    fun consume(link: IncomingLink) {
        if (pending.value == link) {
            pending.value = null
        }
    }

    private fun playFromSearch(query: String) {
        scope.launch {
            if (query.isEmpty()) {
                playerConnection.play()
                return@launch
            }
            val matches = mediaItemDao.search(query = query).first().take(n = SEARCH_PLAY_LIMIT)
            if (matches.isEmpty()) {
                pending.value = IncomingLink.Open(destination = AppDestination.SEARCH)
                return@launch
            }
            playerConnection.playMediaItems(
                mediaItemIds = matches.map {
                    it.id
                }
            )
        }
    }
}
