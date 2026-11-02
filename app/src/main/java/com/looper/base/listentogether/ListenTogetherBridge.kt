package com.looper.base.listentogether

import android.os.SystemClock
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
import com.looper.base.listentogether.model.ListenTogetherEvent
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

    private enum class SeekKind { BUFFERED, NETWORK, RESUME }

    private companion object {
        const val HARD_SYNC_MS = 750L
        const val PAUSED_SYNC_MS = 100L
        const val SOFT_SYNC_START_MS = 120L
        const val SOFT_SYNC_STOP_MS = 40L
        const val SYNC_INTERVAL_MS = 1_000L
        const val RATE_GAIN_MS = 4_000f
        const val MAX_RATE_ADJUST = 0.05f
        const val HARD_SEEK_COOLDOWN_MS = 3_000L
        const val SEEK_SETTLE_MS = 1_500L
        const val LEAD_LEARNING_GAIN = 0.8f
        const val INITIAL_BUFFERED_LEAD_MS = 200L
        const val INITIAL_NETWORK_LEAD_MS = 450L
        const val INITIAL_RESUME_LEAD_MS = 500L
        const val MAX_SEEK_LEAD_MS = 1_500L
        const val HOST_REANCHOR_MS = 120L
        const val HOST_STALL_HOLD_MS = 300L
        const val SETTLE_WINDOW_MS = 4_000L
        const val SETTLE_HARD_SYNC_MS = 250L
        const val BUFFERED_SEEK_MARGIN_MS = 200L
        const val HOST_REANCHOR_COOLDOWN_MS = 5_000L
    }

    private var role = Role.NONE
    private val trackToMediaItem = mutableMapOf<String, String>()
    private var appliedTrackId: String? = null
    private var appliedUpcoming: List<String> = emptyList()
    private var appliedRevision = -1L
    private var loadJob: Job? = null
    private var upcomingJob: Job? = null
    private var syncJob: Job? = null
    private var nudging = false
    private var reportedReadyTrack: String? = null
    private var pendingSeekAt = 0L
    private var lastHardSeekAt = 0L
    private var bufferedLeadMs = INITIAL_BUFFERED_LEAD_MS
    private var networkLeadMs = INITIAL_NETWORK_LEAD_MS
    private var resumeLeadMs = INITIAL_RESUME_LEAD_MS
    private var pendingSeekKind: SeekKind? = null
    private var lastHostReanchorAt = 0L
    private var lastEventAt = 0L
    private var hostStallJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (role == Role.HOST) {
                publishTrackChange()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (role == Role.HOST && !playWhenReady) {
                publishPlayState(playing = false)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (role == Role.HOST && isPlaying && player.playWhenReady) {
                publishPlayState(playing = true)
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
            if (role == Role.HOST) {
                onHostPlaybackState(playbackState = playbackState)
            }
            if (playbackState == Player.STATE_READY && role == Role.GUEST) {
                session.currentRoom?.let(block = ::reportReadyIfLoaded)
            }
        }
    }

    init {
        player.addListener(listener)
        scope.launch {
            session.state.collect(::onState)
        }
        scope.launch {
            session.events.collect { event ->
                if (event is ListenTogetherEvent.SuggestionAccepted && role == Role.HOST) {
                    insertNext(track = event.track)
                }
            }
        }
    }

    fun release() {
        hostStallJob?.cancel()
        loadJob?.cancel()
        upcomingJob?.cancel()
        syncJob?.cancel()
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
    }

    private fun onRoleChanged(previous: Role) {
        syncJob?.cancel()
        loadJob?.cancel()
        upcomingJob?.cancel()
        hostStallJob?.cancel()
        lastEventAt = 0L
        pendingSeekKind = null
        appliedTrackId = null
        appliedUpcoming = emptyList()
        appliedRevision = -1L
        reportedReadyTrack = null
        pendingSeekAt = 0L
        lastHardSeekAt = 0L
        lastHostReanchorAt = 0L
        if (previous == Role.GUEST) {
            restoreSpeed()
        }
        when (role) {
            Role.HOST -> {
                publishSnapshot()
                startSyncLoop(check = ::checkHostTimeline)
            }

            Role.GUEST -> {
                player.playbackParameters = PlaybackParameters(1f, player.playbackParameters.pitch)
                player.shuffleModeEnabled = false
                startSyncLoop(check = ::checkGuestDrift)
            }

            Role.NONE -> Unit
        }
    }

    private fun startSyncLoop(check: () -> Unit) {
        syncJob = scope.launch {
            while (isActive) {
                delay(timeMillis = SYNC_INTERVAL_MS)
                check()
            }
        }
    }

    private fun publishSnapshot() {
        if (player.currentMediaItem == null) {
            return
        }
        publishTrackChange()
        if (!player.isPlaying) {
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
        if (player.isPlaying) {
            publishPlayState(playing = true)
        }
    }

    private fun publishPlayState(playing: Boolean) {
        lastHostReanchorAt = SystemClock.elapsedRealtime()
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

    private fun onHostPlaybackState(playbackState: Int) {
        hostStallJob?.cancel()
        if (playbackState != Player.STATE_BUFFERING || !player.playWhenReady) {
            return
        }
        hostStallJob = scope.launch {
            delay(timeMillis = HOST_STALL_HOLD_MS)
            if (role == Role.HOST && player.playbackState == Player.STATE_BUFFERING && player.playWhenReady) {
                publishPlayState(playing = false)
            }
        }
    }

    private fun checkHostTimeline() {
        val playback = session.currentRoom?.playback ?: return
        val currentTrackId = player.currentMediaItem?.let {
            trackInfo(item = it, durationMs = C.TIME_UNSET)?.id
        }
        if (currentTrackId == null || playback.track?.id != currentTrackId) {
            return
        }
        if (player.playbackState != Player.STATE_READY || !player.isPlaying) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastHostReanchorAt < HOST_REANCHOR_COOLDOWN_MS) {
            return
        }
        val actual = player.currentPosition
        val expected = playback.positionAt(serverNow = session.clock.serverNow())
        val gap = actual - expected
        when {
            !playback.isPlaying -> publishPlayState(playing = true)
            abs(n = gap) > HOST_REANCHOR_MS -> {
                lastHostReanchorAt = now
                session.publish(
                    action = LtPlaybackAction(
                        action = LtAction.SEEK,
                        position = actual
                    )
                )
            }
        }
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

    private suspend fun insertNext(track: LtTrackInfo) {
        val mediaItemId = runCatching {
            mediaItemIdFor(track = track)
        }.getOrNull() ?: return
        val item = itemFactory.build(mediaItemId = mediaItemId, resolve = false) ?: return
        val index = if (player.mediaItemCount == 0) {
            0
        } else {
            player.currentMediaItemIndex + 1
        }
        player.addMediaItem(index, item)
        if (player.mediaItemCount == 1) {
            player.prepare()
        }
    }

    private fun follow(room: ListenTogetherRoom) {
        val playback = room.playback
        val track = playback.track ?: return
        val upcoming = playback.queue.map {
            it.id
        }
        if (track.id != appliedTrackId) {
            appliedTrackId = track.id
            appliedUpcoming = upcoming
            appliedRevision = playback.revision
            reportedReadyTrack = null
            upcomingJob?.cancel()
            loadJob?.cancel()
            loadJob = scope.launch {
                load(playback = playback)
            }
            return
        }
        if (loadJob?.isActive == true) {
            return
        }
        if (upcoming != appliedUpcoming) {
            appliedUpcoming = upcoming
            upcomingJob?.cancel()
            upcomingJob = scope.launch {
                syncUpcoming(queue = playback.queue)
            }
        }
        val isNewAction = playback.revision != appliedRevision
        appliedRevision = playback.revision
        if (isNewAction) {
            lastEventAt = SystemClock.elapsedRealtime()
        }
        applyPlayState(playback = playback, force = isNewAction)
    }

    private suspend fun load(playback: RoomPlayback) {
        val track = playback.track ?: return
        val currentId = runCatching {
            mediaItemIdFor(track = track)
        }.getOrNull() ?: return
        val existingIndex = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId == currentId
        }
        if (existingIndex == null) {
            val item = itemFactory.build(mediaItemId = currentId, resolve = true) ?: return
            val latest = session.currentRoom?.playback ?: playback
            player.setMediaItems(listOf(item), 0, startPosition(playback = latest))
            player.prepare()
            markSeekPending(kind = SeekKind.NETWORK.takeIf {
                latest.isPlaying
            })
        } else if (existingIndex != player.currentMediaItemIndex) {
            val latest = session.currentRoom?.playback ?: playback
            player.seekTo(existingIndex, startPosition(playback = latest))
            markSeekPending(kind = SeekKind.NETWORK.takeIf {
                latest.isPlaying
            })
        }
        val latest = session.currentRoom?.playback ?: playback
        player.playWhenReady = latest.isPlaying
        appliedRevision = latest.revision
        lastEventAt = SystemClock.elapsedRealtime()
        syncUpcoming(queue = latest.queue)
        session.currentRoom?.let(block = ::reportReadyIfLoaded)
    }

    private suspend fun syncUpcoming(queue: List<LtTrackInfo>) {
        val ids = queue.mapNotNull {
            runCatching {
                mediaItemIdFor(track = it)
            }.getOrNull()
        }
        val currentIndex = player.currentMediaItemIndex
        if (player.mediaItemCount == 0 || currentIndex == C.INDEX_UNSET) {
            return
        }
        val existing = (currentIndex + 1 until player.mediaItemCount).map {
            player.getMediaItemAt(it).mediaId
        }
        if (existing == ids && currentIndex == 0) {
            return
        }
        val items = itemFactory.buildAll(ids = ids)
        val index = player.currentMediaItemIndex
        if (index + 1 < player.mediaItemCount) {
            player.removeMediaItems(index + 1, player.mediaItemCount)
        }
        if (index > 0) {
            player.removeMediaItems(0, index)
        }
        player.addMediaItems(items)
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

    private fun isOnRoomTrack(playback: RoomPlayback): Boolean {
        val expectedId = playback.track?.let {
            trackToMediaItem[it.id]
        } ?: return false
        return player.currentMediaItem?.mediaId == expectedId
    }

    private fun startPosition(playback: RoomPlayback): Long {
        val position = playback.positionAt(serverNow = session.clock.serverNow())
        return if (playback.isPlaying) {
            position + networkLeadMs
        } else {
            position
        }
    }

    private fun applyPlayState(playback: RoomPlayback, force: Boolean) {
        if (!isOnRoomTrack(playback = playback)) {
            return
        }
        if (playback.isPlaying && !player.playWhenReady && player.playbackState == Player.STATE_READY) {
            val now = SystemClock.elapsedRealtime()
            resetNudge()
            player.seekTo(playback.positionAt(serverNow = session.clock.serverNow()) + resumeLeadMs)
            player.playWhenReady = true
            lastHardSeekAt = now
            markSeekPending(kind = SeekKind.RESUME)
            return
        }
        if (player.playWhenReady != playback.isPlaying) {
            player.playWhenReady = playback.isPlaying
        }
        correctDrift(playback = playback, force = force)
    }

    private fun checkGuestDrift() {
        val playback = session.currentRoom?.playback ?: return
        if (loadJob?.isActive == true || !isOnRoomTrack(playback = playback)) {
            return
        }
        settlePendingSeek(playback = playback)
        session.currentRoom?.let(block = ::reportReadyIfLoaded)
        correctDrift(playback = playback, force = false)
    }

    private fun correctDrift(playback: RoomPlayback, force: Boolean) {
        if (player.playbackState != Player.STATE_READY || (pendingSeekAt > 0L && !force)) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        val expected = playback.positionAt(serverNow = session.clock.serverNow())
        val drift = expected - player.currentPosition
        val magnitude = abs(n = drift)
        val canHardSeek = force || now - lastHardSeekAt >= HARD_SEEK_COOLDOWN_MS
        val hardThreshold = if (now - lastEventAt < SETTLE_WINDOW_MS) {
            SETTLE_HARD_SYNC_MS
        } else {
            HARD_SYNC_MS
        }
        when {
            !playback.isPlaying -> {
                resetNudge()
                if (magnitude > PAUSED_SYNC_MS) {
                    hardSeek(target = expected, now = now)
                }
            }

            magnitude > hardThreshold && canHardSeek -> hardSeek(target = expected, now = now)
            player.isPlaying && (magnitude > SOFT_SYNC_START_MS || (nudging && magnitude > SOFT_SYNC_STOP_MS)) -> {
                val adjust = (drift / RATE_GAIN_MS).coerceIn(
                    minimumValue = -MAX_RATE_ADJUST,
                    maximumValue = MAX_RATE_ADJUST
                )
                player.playbackParameters =
                    PlaybackParameters(1f + adjust, player.playbackParameters.pitch)
                nudging = true
            }

            else -> resetNudge()
        }
    }

    private fun hardSeek(target: Long, now: Long) {
        resetNudge()
        val kind = when {
            !player.playWhenReady -> null
            target >= player.currentPosition && target <= player.bufferedPosition - BUFFERED_SEEK_MARGIN_MS -> SeekKind.BUFFERED
            else -> SeekKind.NETWORK
        }
        val lead = when (kind) {
            SeekKind.BUFFERED -> bufferedLeadMs
            SeekKind.NETWORK -> networkLeadMs
            SeekKind.RESUME -> resumeLeadMs
            null -> 0L
        }
        player.seekTo((target + lead).coerceAtLeast(minimumValue = 0L))
        lastHardSeekAt = now
        markSeekPending(kind = kind)
    }

    private fun markSeekPending(kind: SeekKind?) {
        pendingSeekAt = SystemClock.elapsedRealtime()
        pendingSeekKind = kind
    }

    private fun settlePendingSeek(playback: RoomPlayback) {
        if (pendingSeekAt == 0L || player.playbackState != Player.STATE_READY) {
            return
        }
        val kind = pendingSeekKind
        if (kind == null || !playback.isPlaying) {
            pendingSeekAt = 0L
            pendingSeekKind = null
            return
        }
        if (!player.isPlaying || SystemClock.elapsedRealtime() - pendingSeekAt < SEEK_SETTLE_MS) {
            return
        }
        val residual =
            playback.positionAt(serverNow = session.clock.serverNow()) - player.currentPosition
        val step = (residual * LEAD_LEARNING_GAIN).toLong()
        when (kind) {
            SeekKind.BUFFERED -> bufferedLeadMs = (bufferedLeadMs + step).coerceIn(
                minimumValue = 0L,
                maximumValue = MAX_SEEK_LEAD_MS
            )

            SeekKind.NETWORK -> networkLeadMs = (networkLeadMs + step).coerceIn(
                minimumValue = 0L,
                maximumValue = MAX_SEEK_LEAD_MS
            )

            SeekKind.RESUME -> resumeLeadMs = (resumeLeadMs + step).coerceIn(
                minimumValue = 0L,
                maximumValue = MAX_SEEK_LEAD_MS
            )
        }
        pendingSeekAt = 0L
        pendingSeekKind = null
    }

    private fun resetNudge() {
        if (nudging) {
            player.playbackParameters = PlaybackParameters(1f, player.playbackParameters.pitch)
            nudging = false
        }
    }

    private fun reportReadyIfLoaded(room: ListenTogetherRoom) {
        val trackId = room.playback.track?.id ?: return
        if (reportedReadyTrack == trackId || loadJob?.isActive == true) {
            return
        }
        if (player.playbackState == Player.STATE_READY && isOnRoomTrack(playback = room.playback)) {
            reportedReadyTrack = trackId
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
