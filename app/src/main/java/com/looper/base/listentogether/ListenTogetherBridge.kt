package com.looper.base.listentogether

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.looper.base.base.storage.playerSettings
import com.looper.base.listentogether.model.ListenTogetherConstants
import com.looper.base.listentogether.model.ListenTogetherRoom
import com.looper.base.listentogether.model.ListenTogetherState
import com.looper.base.listentogether.model.RoomPlayback
import com.looper.base.listentogether.protocol.LtAction
import com.looper.base.listentogether.protocol.LtPlaybackAction
import com.looper.base.listentogether.protocol.LtTrackInfo
import com.looper.base.player.source.MediaItemKeys
import com.looper.base.player.source.PlaybackItemFactory
import com.looper.base.player.source.StreamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(UnstableApi::class)
class ListenTogetherBridge(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val session: ListenTogetherSession,
    private val streamRepository: StreamRepository,
    private val itemFactory: PlaybackItemFactory,
) {

    private enum class Role { NONE, HOST, GUEST }

    private companion object {
        const val HARD_SYNC_MS = 750L
        const val SOFT_SYNC_MS = 60L
        const val DRIFT_CHECK_INTERVAL_MS = 2_000L
        const val RATE_GAIN_MS = 4_000f
        const val MAX_RATE_ADJUST = 0.05f
    }

    private var role = Role.NONE
    private val trackToMediaItem = mutableMapOf<String, String>()
    private var appliedQueue: List<String> = emptyList()
    private var loadJob: Job? = null
    private var driftJob: Job? = null
    private var nudging = false
    private var reportedBufferTrack: String? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (role == Role.HOST) {
                publishTrackChange()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (role == Role.HOST) {
                publishPlayState(playing = playWhenReady)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (role == Role.HOST && reason == Player.DISCONTINUITY_REASON_SEEK && oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
                session.publish(
                    action = LtPlaybackAction(
                        action = LtAction.SEEK,
                        position = newPosition.positionMs
                    )
                )
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (role == Role.HOST && reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                session.publish(
                    action = LtPlaybackAction(
                        action = LtAction.SYNC_QUEUE,
                        queue = upcomingTracks()
                    )
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                session.currentRoom?.let(block = ::reportBufferIfWaiting)
            }
        }
    }

    init {
        player.addListener(listener)
        scope.launch {
            session.state.collect(::onState)
        }
    }

    fun release() {
        loadJob?.cancel()
        driftJob?.cancel()
        player.removeListener(listener)
        if (role == Role.GUEST) {
            restoreSpeed()
        }
    }

    private fun onState(state: ListenTogetherState) {
        val room = (state as? ListenTogetherState.InRoom)?.room
        val nextRole = when {
            room == null -> Role.NONE
            room.isHost -> Role.HOST
            else -> Role.GUEST
        }
        if (nextRole != role) {
            val previous = role
            role = nextRole
            onRoleChanged(previous = previous)
        }
        room ?: return
        if (role == Role.GUEST) {
            follow(room = room)
        }
        reportBufferIfWaiting(room = room)
    }

    private fun onRoleChanged(previous: Role) {
        driftJob?.cancel()
        loadJob?.cancel()
        appliedQueue = emptyList()
        reportedBufferTrack = null
        if (previous == Role.GUEST) {
            restoreSpeed()
        }
        when (role) {
            Role.HOST -> publishSnapshot()
            Role.GUEST -> {
                player.playbackParameters = PlaybackParameters(1f, player.playbackParameters.pitch)
                startDriftLoop()
            }

            Role.NONE -> Unit
        }
    }

    private fun publishSnapshot() {
        if (player.currentMediaItem == null) {
            return
        }
        publishTrackChange()
        if (!player.playWhenReady) {
            publishPlayState(playing = false)
        }
    }

    private fun publishTrackChange() {
        val track = player.currentMediaItem?.let {
            trackInfo(item = it, durationMs = player.duration)
        } ?: return
        session.publish(
            action = LtPlaybackAction(
                action = LtAction.CHANGE_TRACK,
                trackId = track.id,
                trackInfo = track,
                queue = upcomingTracks(),
            )
        )
        if (player.playWhenReady) {
            publishPlayState(playing = true)
        }
    }

    private fun publishPlayState(playing: Boolean) {
        session.publish(
            action = LtPlaybackAction(
                action = if (playing) {
                    LtAction.PLAY
                } else {
                    LtAction.PAUSE
                },
                position = player.currentPosition.coerceAtLeast(minimumValue = 0L),
            )
        )
    }

    private fun upcomingTracks(): List<LtTrackInfo> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) {
            return emptyList()
        }
        val tracks = mutableListOf<LtTrackInfo>()
        var index = timeline.getNextWindowIndex(
            player.currentMediaItemIndex,
            Player.REPEAT_MODE_OFF,
            player.shuffleModeEnabled
        )
        while (index != C.INDEX_UNSET && tracks.size < ListenTogetherConstants.MAX_QUEUE) {
            trackInfo(
                item = player.getMediaItemAt(index),
                durationMs = C.TIME_UNSET
            )?.let(block = tracks::add)
            index = timeline.getNextWindowIndex(
                index,
                Player.REPEAT_MODE_OFF,
                player.shuffleModeEnabled
            )
        }
        return tracks
    }

    private fun trackInfo(item: MediaItem, durationMs: Long): LtTrackInfo? {
        val metadata = item.mediaMetadata
        val sourceUrl = metadata.extras?.getString(MediaItemKeys.SOURCE_URL) ?: return null
        val artwork = metadata.artworkUri?.toString()?.takeIf {
            it.startsWith(prefix = "http")
        }
        return LtTrackInfo(
            id = LtTrackIds.fromSourceUrl(sourceUrl = sourceUrl),
            title = metadata.title?.toString().orEmpty().ifBlank {
                sourceUrl
            },
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            duration = metadata.durationMs ?: durationMs.takeIf {
                it != C.TIME_UNSET
            } ?: 0L,
            thumbnail = artwork.orEmpty(),
        )
    }

    private fun follow(room: ListenTogetherRoom) {
        val playback = room.playback
        val track = playback.track ?: return
        val queue = listOf(track.id) + playback.queue.map {
            it.id
        }
        if (queue != appliedQueue) {
            appliedQueue = queue
            reportedBufferTrack = null
            loadJob?.cancel()
            loadJob = scope.launch {
                load(playback = playback)
            }
            return
        }
        if (loadJob?.isActive != true) {
            applyPlayState(playback = playback)
        }
    }

    private suspend fun load(playback: RoomPlayback) {
        val tracks = listOfNotNull(element = playback.track) + playback.queue
        val ids = tracks.mapNotNull {
            runCatching {
                mediaItemIdFor(track = it)
            }.getOrNull()
        }
        val currentId = playback.track?.let {
            trackToMediaItem[it.id]
        } ?: return
        val items = itemFactory.buildAll(ids = ids, resolveId = currentId)
        if (items.isEmpty()) {
            return
        }
        val latest = session.currentRoom?.playback ?: playback
        player.setMediaItems(items, 0, latest.positionAt(serverNow = session.clock.serverNow()))
        player.prepare()
        player.playWhenReady = latest.isPlaying
    }

    private suspend fun mediaItemIdFor(track: LtTrackInfo): String? {
        trackToMediaItem[track.id]?.let {
            return it
        }
        val sourceUrl = LtTrackIds.toSourceUrl(trackId = track.id) ?: return null
        val entity = streamRepository.ensureMediaItem(
            sourceUrl = sourceUrl,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.duration,
            thumbnailUrl = track.thumbnail,
        )
        trackToMediaItem[track.id] = entity.id
        return entity.id
    }

    private fun applyPlayState(playback: RoomPlayback) {
        val expectedMediaItemId = playback.track?.let { trackToMediaItem[it.id] }
        if (expectedMediaItemId != null && player.currentMediaItem?.mediaId != expectedMediaItemId && player.mediaItemCount > 0) {
            player.seekTo(0, playback.positionAt(serverNow = session.clock.serverNow()))
        }
        if (player.playWhenReady != playback.isPlaying) {
            player.playWhenReady = playback.isPlaying
        }
        correctDrift(playback = playback, allowNudge = false)
    }

    private fun startDriftLoop() {
        driftJob = scope.launch {
            while (isActive) {
                delay(timeMillis = DRIFT_CHECK_INTERVAL_MS)
                val playback = session.currentRoom?.playback ?: continue
                if (loadJob?.isActive != true && player.playbackState == Player.STATE_READY) {
                    correctDrift(playback = playback, allowNudge = playback.isPlaying)
                }
            }
        }
    }

    private fun correctDrift(playback: RoomPlayback, allowNudge: Boolean) {
        if (player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_BUFFERING) {
            return
        }
        val expected = playback.positionAt(serverNow = session.clock.serverNow())
        val drift = expected - player.currentPosition
        when {
            abs(n = drift) > HARD_SYNC_MS -> {
                player.seekTo(expected)
                resetNudge()
            }

            allowNudge && abs(n = drift) > SOFT_SYNC_MS -> {
                val adjust = (drift / RATE_GAIN_MS).coerceIn(
                    minimumValue = -MAX_RATE_ADJUST,
                    maximumValue = MAX_RATE_ADJUST
                )
                player.playbackParameters =
                    PlaybackParameters(1f + adjust, player.playbackParameters.pitch)
                nudging = true
            }

            nudging -> resetNudge()
        }
    }

    private fun resetNudge() {
        if (nudging) {
            player.playbackParameters = PlaybackParameters(1f, player.playbackParameters.pitch)
            nudging = false
        }
    }

    private fun reportBufferIfWaiting(room: ListenTogetherRoom) {
        val trackId = room.playback.track?.id ?: return
        if (room.myUserId !in room.waitingFor || reportedBufferTrack == trackId) {
            return
        }
        if (player.playbackState == Player.STATE_READY && loadJob?.isActive != true) {
            reportedBufferTrack = trackId
            session.bufferReady(trackId = trackId)
        }
    }

    private fun restoreSpeed() {
        nudging = false
        scope.launch {
            val settings = playerSettings.first()
            player.playbackParameters =
                PlaybackParameters(settings.playbackSpeed, settings.pitchMultiplier)
        }
    }
}
