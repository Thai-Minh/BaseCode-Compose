package com.looper.base.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.glance.appwidget.updateAll
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.looper.base.base.storage.audioEffectsSettings
import com.looper.base.base.storage.playerSettings
import com.looper.base.database.AppDatabase
import com.looper.base.equalizer.audio.AudioEffectController
import com.looper.base.equalizer.audio.EchoProcessor
import com.looper.base.equalizer.audio.ReverbProcessor
import com.looper.base.equalizer.audio.TenBandEqualizerProcessor
import com.looper.base.equalizer.data.EqRepository
import com.looper.base.equalizer.model.forAudioFx
import com.looper.base.lastfm.data.LastFmClient
import com.looper.base.library.data.LibraryRepository
import com.looper.base.listentogether.ListenTogetherBridge
import com.looper.base.listentogether.ListenTogetherSession
import com.looper.base.lastfm.data.LastFmRepository
import com.looper.base.lastfm.data.LastFmScrobbler
import com.looper.base.player.audio.AudioOutputRouter
import com.looper.base.player.audio.LoudnessNormalizerProcessor
import com.looper.base.player.audio.LoudnessTracker
import com.looper.base.player.library.LibrarySelection
import com.looper.base.player.model.PlayerSettings
import com.looper.base.player.library.MediaLibraryTree
import com.looper.base.player.queue.ShuffleController
import com.looper.base.player.queue.UpcomingItemPreparer
import com.looper.base.player.sleeptimer.SleepTimerCommands
import com.looper.base.player.sleeptimer.SleepTimerCommands.writeTo
import com.looper.base.player.sleeptimer.SleepTimerController
import com.looper.base.player.sleeptimer.SleepTimerMode
import com.looper.base.player.sleeptimer.SleepTimerState
import com.looper.base.player.source.PlaybackItemFactory
import com.looper.base.player.source.PlaybackMediaSourceFactory
import com.looper.base.player.source.StreamRepository
import com.looper.base.widget.PlaybackWidgetUpdater
import com.looper.base.widget.StatsWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val writeScope = CoroutineScope(context = SupervisorJob() + Dispatchers.IO)

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private val effectController: AudioEffectController by inject()
    private val eqRepository: EqRepository by inject()
    private val database: AppDatabase by inject()
    private val cache: Cache by inject()
    private val streamRepository: StreamRepository by inject()
    private val itemFactory: PlaybackItemFactory by inject()
    private val tenBandProcessor: TenBandEqualizerProcessor by inject()
    private val audioOutputRouter: AudioOutputRouter by inject()
    private val lastFmClient: LastFmClient by inject()
    private val lastFmRepository: LastFmRepository by inject()
    private val loudnessProcessor = LoudnessNormalizerProcessor()
    private val echoProcessor = EchoProcessor()
    private val reverbProcessor = ReverbProcessor()
    private val libraryTree: MediaLibraryTree by inject()
    private val libraryRepository: LibraryRepository by inject()
    private val listenTogetherSession: ListenTogetherSession by inject()
    private val searchResults = mutableMapOf<String, List<MediaItem>>()

    private val serviceScope = CoroutineScope(context = SupervisorJob() + Dispatchers.Main)

    private var player: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null
    private var sleepTimer: SleepTimerController? = null
    private var recovery: PlaybackRecovery? = null
    private var persistence: PlaybackPersistence? = null
    private var listeningTracker: ListeningTracker? = null
    private var shuffleController: ShuffleController? = null
    private var upcomingItemPreparer: UpcomingItemPreparer? = null
    private var loudnessTracker: LoudnessTracker? = null
    private var lastFmScrobbler: LastFmScrobbler? = null
    private var widgetUpdater: PlaybackWidgetUpdater? = null
    private var listenTogetherBridge: ListenTogetherBridge? = null

    private var restoreJob: Deferred<RestoredQueue?>? = null
    private var resumptionRequested = false
    private var broadcastAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    private val currentMediaItemId = MutableStateFlow<String?>(value = null)
    private var isCurrentFavorite = false
    private var latestPlayerSettings = PlayerSettings()

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            effectController.attach(audioSessionId)
            broadcastAudioSession(audioSessionId = audioSessionId)
            sleepTimer?.state?.value?.let(block = ::publishSessionExtras)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && !effectController.capabilities.value.attached) {
                player?.audioSessionId?.let(block = effectController::attach)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentMediaItemId.value = mediaItem?.mediaId
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateMediaButtons()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateMediaButtons()
        }
    }

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .setAudioProcessors(
                    arrayOf(
                        tenBandProcessor,
                        echoProcessor,
                        reverbProcessor,
                        loudnessProcessor
                    )
                )
                .build()
        }
        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(
                PlaybackMediaSourceFactory(
                    context = this,
                    cache = cache,
                    repository = streamRepository,
                )
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player = exoPlayer

        exoPlayer.addListener(playerListener)
        effectController.attach(exoPlayer.audioSessionId)
        broadcastAudioSession(audioSessionId = exoPlayer.audioSessionId)

        val timer = SleepTimerController(
            player = exoPlayer,
            scope = serviceScope,
            onStateChanged = ::publishSessionExtras,
        )
        sleepTimer = timer
        recovery = PlaybackRecovery(
            player = exoPlayer,
            scope = serviceScope,
            repository = streamRepository,
            itemFactory = itemFactory,
        )
        val shuffle = ShuffleController(player = exoPlayer)
        shuffleController = shuffle
        upcomingItemPreparer = UpcomingItemPreparer(
            player = exoPlayer,
            scope = serviceScope,
            itemFactory = itemFactory,
        )
        loudnessTracker = LoudnessTracker(
            player = exoPlayer,
            processor = loudnessProcessor,
            mediaItemDao = database.mediaItemDao(),
            scope = writeScope,
        )
        lastFmScrobbler = LastFmScrobbler(
            player = exoPlayer,
            scope = serviceScope,
            client = lastFmClient,
            repository = lastFmRepository,
        )
        val playbackPersistence = PlaybackPersistence(
            player = exoPlayer,
            scope = serviceScope,
            queueDao = database.queueDao(),
            repository = streamRepository,
            itemFactory = itemFactory,
            shuffleController = shuffle,
        )
        persistence = playbackPersistence
        listeningTracker = ListeningTracker(
            player = exoPlayer,
            scope = writeScope,
            playbackEventDao = database.playbackEventDao(),
            mediaItemDao = database.mediaItemDao(),
            onRecorded = {
                StatsWidget().updateAll(applicationContext)
            },
        )
        listenTogetherBridge = ListenTogetherBridge(
            player = exoPlayer,
            scope = serviceScope,
            session = listenTogetherSession,
            streamRepository = streamRepository,
            itemFactory = itemFactory,
        )
        widgetUpdater = PlaybackWidgetUpdater(
            context = applicationContext,
            player = exoPlayer,
            scope = serviceScope,
            releaseScope = writeScope,
        )

        val sessionBuilder = MediaLibrarySession.Builder(this, exoPlayer, SessionCallback())
            .setSessionExtras(buildSessionExtras(timer = timer.state.value))
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            sessionBuilder.setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        }
        mediaSession = sessionBuilder.build()
        updateMediaButtons()

        serviceScope.launch {
            combine(
                flow = eqRepository.settings,
                flow2 = eqRepository.tenBandSettings
            ) { eq, tenBand ->
                eq.forAudioFx(tenBand = tenBand)
            }.collect {
                effectController.apply(settings = it)
            }
        }
        serviceScope.launch {
            playerSettings.collect {
                latestPlayerSettings = it
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguage(it.preferredAudioLanguage)
                    .setPreferredTextLanguage(it.subtitleLanguage)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, it.subtitleLanguage == null)
                    .build()
                exoPlayer.playbackParameters =
                    PlaybackParameters(it.playbackSpeed, it.pitchMultiplier)
                exoPlayer.skipSilenceEnabled = it.skipSilence
                loudnessProcessor.enabled = it.normalizeVolume
            }
        }
        serviceScope.launch {
            audioEffectsSettings.collect {
                echoProcessor.update(
                    enabled = it.echoEnabled,
                    delayMs = it.echoDelayMs,
                    feedback = it.echoFeedback,
                    mix = it.echoMix,
                )
                reverbProcessor.update(
                    enabled = it.reverbEnabled,
                    preset = it.reverbPreset,
                    mix = it.reverbMix
                )
            }
        }
        serviceScope.launch {
            audioOutputRouter.selected.collect {
                exoPlayer.setPreferredAudioDevice(audioOutputRouter.deviceInfo(output = it))
            }
        }
        serviceScope.launch {
            currentMediaItemId
                .flatMapLatest { id ->
                    if (id == null) {
                        flowOf(value = null)
                    } else {
                        database.mediaItemDao()
                            .observe(id = id)
                    }
                }.collect {
                    isCurrentFavorite = it?.isFavorite == true
                    updateMediaButtons()
                }
        }

        val restore = serviceScope.async {
            runCatching {
                playbackPersistence.load()
            }.getOrNull()
        }
        restoreJob = restore
        serviceScope.launch {
            val restored = restore.await() ?: return@launch
            if (!resumptionRequested && exoPlayer.mediaItemCount == 0) {
                playbackPersistence.apply(restored = restored)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistence?.saveSnapshot()
        val current = player
        if (latestPlayerSettings.stopOnTaskRemoved || current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        persistence?.release()
        persistence = null
        listeningTracker?.release()
        listeningTracker = null
        loudnessTracker?.release()
        loudnessTracker = null
        lastFmScrobbler?.release()
        lastFmScrobbler = null
        widgetUpdater?.release()
        widgetUpdater = null
        listenTogetherBridge?.release()
        listenTogetherBridge = null
        upcomingItemPreparer?.release()
        upcomingItemPreparer = null
        shuffleController?.release()
        shuffleController = null
        recovery?.release()
        recovery = null
        sleepTimer?.release()
        sleepTimer = null
        serviceScope.cancel()
        effectController.detach()
        broadcastAudioSession(audioSessionId = C.AUDIO_SESSION_ID_UNSET)
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private fun buildSessionExtras(timer: SleepTimerState): Bundle =
        timer.writeTo(Bundle()).apply {
            putInt(
                PlayerSessionCommands.EXTRA_AUDIO_SESSION_ID,
                player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
            )
        }

    private fun publishSessionExtras(timer: SleepTimerState) {
        mediaSession?.sessionExtras = buildSessionExtras(timer = timer)
    }

    private fun updateMediaButtons() {
        val session = mediaSession ?: return
        val current = player ?: return
        session.setMediaButtonPreferences(
            PlayerSessionCommands.buttons(
                context = this,
                isFavorite = isCurrentFavorite,
                shuffleEnabled = current.shuffleModeEnabled,
                repeatMode = current.repeatMode,
            )
        )
    }

    private fun broadcastAudioSession(audioSessionId: Int) {
        if (audioSessionId == broadcastAudioSessionId) {
            return
        }
        if (broadcastAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            sendBroadcast(
                audioEffectIntent(
                    action = AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION,
                    audioSessionId = broadcastAudioSessionId,
                )
            )
        }
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            sendBroadcast(
                audioEffectIntent(
                    action = AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION,
                    audioSessionId = audioSessionId,
                )
            )
        }
        broadcastAudioSessionId = audioSessionId
    }

    private fun audioEffectIntent(action: String, audioSessionId: Int): Intent = Intent(action)
        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)

    private fun toggleFavorite() {
        val mediaItemId = player?.currentMediaItem?.mediaId ?: return
        val favorite = !isCurrentFavorite
        writeScope.launch {
            runCatching {
                libraryRepository.setFavorite(
                    mediaItemIds = listOf(mediaItemId),
                    favorite = favorite
                )
            }
        }
    }

    private suspend fun buildRequestedItems(
        requested: List<MediaItem>,
        resolveIndex: Int,
    ): MediaItemsWithStartPosition? {
        val resolveId = requested.getOrNull(index = resolveIndex)
            ?.takeIf {
                it.localConfiguration == null
            }?.mediaId
        val built = itemFactory.buildAll(
            ids = requested
                .filter {
                    it.localConfiguration == null
                }.map {
                    it.mediaId
                }.distinct(),
            resolveId = resolveId,
        ).associateBy {
            it.mediaId
        }
        val items = mutableListOf<MediaItem>()
        var startIndex = 0
        requested.forEachIndexed { index, item ->
            val playable = if (item.localConfiguration != null) {
                item
            } else {
                built[item.mediaId]
            }
            if (playable != null) {
                if (index == resolveIndex) {
                    startIndex = items.size
                }
                items += playable
            }
        }
        if (items.isEmpty()) {
            return null
        }
        return MediaItemsWithStartPosition(items, startIndex, C.TIME_UNSET)
    }

    private fun requestItem(mediaItemId: String): MediaItem = MediaItem.Builder()
        .setMediaId(mediaItemId)
        .build()

    private fun List<MediaItem>.page(page: Int, pageSize: Int): List<MediaItem> {
        if (pageSize <= 0) {
            return this
        }
        val from = (page * pageSize).coerceAtMost(maximumValue = size)
        return subList(from, (from + pageSize).coerceAtMost(maximumValue = size))
    }

    private fun <T> launchFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        serviceScope.launch {
            try {
                future.set(block())
            } catch (e: CancellationException) {
                future.cancel(false)
                throw e
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ConnectionResult {
            val sessionCommands = ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .apply {
                    SleepTimerCommands.all.forEach(action = ::add)
                    PlayerSessionCommands.all.forEach(action = ::add)
                }
                .build()
            return ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = launchFuture {
            val requested = mediaItems.map {
                if (it.localConfiguration == null) requestItem(
                    mediaItemId = libraryTree.stripParent(
                        mediaId = it.mediaId
                    )
                ) else it
            }
            buildRequestedItems(requested = requested, resolveIndex = C.INDEX_UNSET)
                ?.mediaItems
                ?.toMutableList()
                ?: mutableListOf()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> = launchFuture {
            val searchQuery = mediaItems.singleOrNull()?.requestMetadata?.searchQuery?.trim()
            if (searchQuery != null && searchQuery.isEmpty()) {
                val restored = restoreJob?.await() ?: persistence?.load()
                ?: throw UnsupportedOperationException("No saved queue")
                return@launchFuture MediaItemsWithStartPosition(
                    restored.items,
                    restored.startIndex,
                    restored.positionMs
                )
            }
            val selection = if (searchQuery != null) {
                LibrarySelection(
                    mediaItemIds = libraryTree.searchMediaItemIds(query = searchQuery),
                    startIndex = 0
                )
            } else {
                mediaItems.singleOrNull()
                    ?.takeIf {
                        it.localConfiguration == null
                    }
                    ?.let {
                        libraryTree.resolveSelection(mediaId = it.mediaId)
                    }
            }
            val requested = selection?.mediaItemIds?.map {
                requestItem(mediaItemId = it)
            } ?: mediaItems
            val resolveIndex =
                selection?.startIndex ?: if (startIndex == C.INDEX_UNSET) {
                    0
                } else {
                    startIndex
                }
            val built = buildRequestedItems(requested = requested, resolveIndex = resolveIndex)
                ?: throw IllegalArgumentException("No playable media items")
            MediaItemsWithStartPosition(built.mediaItems, built.startIndex, startPositionMs)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(libraryTree.root(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = launchFuture {
            LibraryResult.ofItemList(
                libraryTree.children(parentId = parentId).page(page = page, pageSize = pageSize),
                params,
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = launchFuture {
            libraryTree.item(mediaId = mediaId)?.let {
                LibraryResult.ofItem(it, null)
            } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = launchFuture {
            val results = libraryTree.search(query = query)
            searchResults[query] = results
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = launchFuture {
            val results = searchResults[query] ?: libraryTree.search(query = query)
            LibraryResult.ofItemList(results.page(page = page, pageSize = pageSize), params)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            resumptionRequested = true
            return launchFuture {
                val restored = restoreJob?.await() ?: persistence?.load()
                ?: throw UnsupportedOperationException("No saved queue")
                player?.let {
                    it.repeatMode = restored.repeatMode
                    it.shuffleModeEnabled = restored.shuffleEnabled
                }
                MediaItemsWithStartPosition(
                    restored.items,
                    restored.startIndex,
                    restored.positionMs
                )
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlayerSessionCommands.ACTION_TOGGLE_FAVORITE -> {
                    toggleFavorite()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                PlayerSessionCommands.ACTION_TOGGLE_SHUFFLE -> {
                    player?.let {
                        it.shuffleModeEnabled = !it.shuffleModeEnabled
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                PlayerSessionCommands.ACTION_CYCLE_REPEAT -> {
                    player?.let {
                        it.repeatMode =
                            PlayerSessionCommands.nextRepeatMode(current = it.repeatMode)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }

            val timer = sleepTimer
                ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))

            when (customCommand.customAction) {
                SleepTimerCommands.ACTION_START -> {
                    val mode = args.getString(SleepTimerCommands.EXTRA_MODE)
                        ?.let { name ->
                            SleepTimerMode.entries.firstOrNull {
                                it.name == name
                            }
                        } ?: SleepTimerMode.DURATION
                    timer.start(
                        mode = mode,
                        durationMs = args.getLong(SleepTimerCommands.EXTRA_DURATION_MS, 0L),
                    )
                }

                SleepTimerCommands.ACTION_EXTEND ->
                    timer.extend(byMs = args.getLong(SleepTimerCommands.EXTRA_DURATION_MS, 0L))

                SleepTimerCommands.ACTION_CANCEL -> timer.cancel()

                else -> return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}
