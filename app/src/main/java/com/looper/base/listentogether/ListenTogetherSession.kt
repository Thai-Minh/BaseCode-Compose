package com.looper.base.listentogether

import com.looper.base.base.storage.listenTogetherSettings
import com.looper.base.base.storage.updateListenTogetherSettings
import com.looper.base.listentogether.model.JoinRequest
import com.looper.base.listentogether.model.ListenTogetherConstants
import com.looper.base.listentogether.model.ListenTogetherEvent
import com.looper.base.listentogether.model.ListenTogetherRoom
import com.looper.base.listentogether.model.ListenTogetherSettings
import com.looper.base.listentogether.model.ListenTogetherState
import com.looper.base.listentogether.model.RoomMember
import com.looper.base.listentogether.model.RoomPlayback
import com.looper.base.listentogether.model.TrackSuggestion
import com.looper.base.listentogether.net.LtClock
import com.looper.base.listentogether.net.LtConnection
import com.looper.base.listentogether.net.LtConnectionState
import com.looper.base.listentogether.protocol.LtAction
import com.looper.base.listentogether.protocol.LtBufferReady
import com.looper.base.listentogether.protocol.LtBufferWait
import com.looper.base.listentogether.protocol.LtCreateRoom
import com.looper.base.listentogether.protocol.LtEmpty
import com.looper.base.listentogether.protocol.LtError
import com.looper.base.listentogether.protocol.LtFrame
import com.looper.base.listentogether.protocol.LtHostChanged
import com.looper.base.listentogether.protocol.LtJoinApproved
import com.looper.base.listentogether.protocol.LtJoinRoom
import com.looper.base.listentogether.protocol.LtMessageType
import com.looper.base.listentogether.protocol.LtPlaybackAction
import com.looper.base.listentogether.protocol.LtReason
import com.looper.base.listentogether.protocol.LtReconnect
import com.looper.base.listentogether.protocol.LtReconnected
import com.looper.base.listentogether.protocol.LtRoomCreated
import com.looper.base.listentogether.protocol.LtRoomState
import com.looper.base.listentogether.protocol.LtSuggestTrack
import com.looper.base.listentogether.protocol.LtSuggestionReceived
import com.looper.base.listentogether.protocol.LtSuggestionResult
import com.looper.base.listentogether.protocol.LtSuggestionTarget
import com.looper.base.listentogether.protocol.LtSyncState
import com.looper.base.listentogether.protocol.LtTrackInfo
import com.looper.base.listentogether.protocol.LtTransferHost
import com.looper.base.listentogether.protocol.LtUserEvent
import com.looper.base.listentogether.protocol.LtUserTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class ListenTogetherSession(
    okHttpClient: OkHttpClient,
    userAgent: String,
) {

    private val scope = CoroutineScope(context = SupervisorJob() + Dispatchers.Main.immediate)

    val clock = LtClock()

    private val connection = LtConnection(
        okHttpClient = okHttpClient,
        clock = clock,
        userAgent = userAgent,
        scope = scope,
    )

    val state: StateFlow<ListenTogetherState>
        field = MutableStateFlow<ListenTogetherState>(value = ListenTogetherState.Idle(connection = LtConnectionState.Disconnected))

    val events: SharedFlow<ListenTogetherEvent>
        field = MutableSharedFlow<ListenTogetherEvent>(
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private var settings = ListenTogetherSettings()
    private var sessionToken: String? = null
    private var pendingCommand: (() -> Unit)? = null

    init {
        scope.launch {
            listenTogetherSettings.collect {
                settings = it
            }
        }
        scope.launch {
            connection.state.collect(::onConnectionState)
        }
        scope.launch {
            connection.frames.collect(::onFrame)
        }
    }

    val currentRoom: ListenTogetherRoom?
        get() = (state.value as? ListenTogetherState.InRoom)?.room

    fun createRoom(username: String) {
        val name = rememberName(username = username)
        runWhenConnected {
            connection.send(
                type = LtMessageType.CREATE_ROOM,
                payload = LtCreateRoom(username = name)
            )
        }
    }

    fun joinRoom(roomCode: String, username: String) {
        val name = rememberName(username = username)
        val code = roomCode.trim().uppercase()
        state.value = ListenTogetherState.WaitingApproval(
            connection = state.value.connection,
            roomCode = code
        )
        runWhenConnected {
            connection.send(
                type = LtMessageType.JOIN_ROOM,
                payload = LtJoinRoom(roomCode = code, username = name)
            )
        }
    }

    fun cancelJoin() {
        if (state.value is ListenTogetherState.WaitingApproval) {
            state.value = ListenTogetherState.Idle(connection = state.value.connection)
        }
    }

    fun leaveRoom() {
        connection.send(type = LtMessageType.LEAVE_ROOM, payload = LtEmpty())
        clearRoom()
        connection.disconnect()
    }

    fun approveJoin(userId: String) {
        connection.send(type = LtMessageType.APPROVE_JOIN, payload = LtUserTarget(userId = userId))
        updateRoom { room ->
            room.copy(joinRequests = room.joinRequests.filterNot {
                it.userId == userId
            })
        }
    }

    fun rejectJoin(userId: String, reason: String = "") {
        connection.send(
            type = LtMessageType.REJECT_JOIN,
            payload = LtUserTarget(userId = userId, reason = reason)
        )
        updateRoom { room ->
            room.copy(joinRequests = room.joinRequests.filterNot {
                it.userId == userId
            })
        }
    }

    fun kick(userId: String, block: Boolean = false) {
        val member = currentRoom?.members?.firstOrNull {
            it.userId == userId
        }
        connection.send(type = LtMessageType.KICK_USER, payload = LtUserTarget(userId = userId))
        if (block && member != null) {
            saveSettings(value = settings.copy(blockedNames = settings.blockedNames + member.username))
        }
    }

    fun unblock(username: String) {
        saveSettings(value = settings.copy(blockedNames = settings.blockedNames - username))
    }

    fun transferHost(userId: String) {
        connection.send(
            type = LtMessageType.TRANSFER_HOST,
            payload = LtTransferHost(newHostId = userId)
        )
    }

    fun suggestTrack(track: LtTrackInfo) {
        connection.send(
            type = LtMessageType.SUGGEST_TRACK,
            payload = LtSuggestTrack(trackInfo = track)
        )
    }

    fun approveSuggestion(suggestionId: String) {
        connection.send(
            type = LtMessageType.APPROVE_SUGGESTION,
            payload = LtSuggestionTarget(suggestionId = suggestionId)
        )
        removeSuggestion(suggestionId = suggestionId)
    }

    fun rejectSuggestion(suggestionId: String, reason: String = "") {
        connection.send(
            type = LtMessageType.REJECT_SUGGESTION,
            payload = LtSuggestionTarget(suggestionId = suggestionId, reason = reason),
        )
        removeSuggestion(suggestionId = suggestionId)
    }

    fun requestSync() {
        connection.send(type = LtMessageType.REQUEST_SYNC, payload = LtEmpty())
    }

    fun bufferReady(trackId: String) {
        connection.send(
            type = LtMessageType.BUFFER_READY,
            payload = LtBufferReady(trackId = trackId)
        )
    }

    fun publish(action: LtPlaybackAction) {
        if (currentRoom?.isHost != true) {
            return
        }
        connection.send(
            type = LtMessageType.PLAYBACK_ACTION,
            payload = action.copy(
                capturedAtServerTime = if (clock.isCalibrated) {
                    clock.serverNow()
                } else {
                    0L
                }
            ),
        )
    }

    fun updateSettings(value: ListenTogetherSettings) {
        saveSettings(value = value)
    }

    private fun saveSettings(value: ListenTogetherSettings) {
        settings = value
        updateListenTogetherSettings(value = value)
    }

    private fun rememberName(username: String): String {
        val name = username.trim().take(n = ListenTogetherConstants.MAX_USERNAME_LENGTH)
        if (name != settings.displayName) {
            saveSettings(value = settings.copy(displayName = name))
        }
        return name
    }

    private fun runWhenConnected(command: () -> Unit) {
        if (connection.state.value is LtConnectionState.Connected) {
            command()
            return
        }
        pendingCommand = command
        scope.launch {
            connection.connect(url = listenTogetherSettings.first().effectiveServerUrl)
        }
    }

    private fun onConnectionState(connectionState: LtConnectionState) {
        state.value = when (val current = state.value) {
            is ListenTogetherState.Idle -> current.copy(connection = connectionState)
            is ListenTogetherState.WaitingApproval -> current.copy(connection = connectionState)
            is ListenTogetherState.InRoom -> current.copy(connection = connectionState)
        }
        when (connectionState) {
            is LtConnectionState.Connected -> {
                val token = sessionToken
                if (connectionState.isReconnect && token != null) {
                    connection.send(
                        type = LtMessageType.RECONNECT,
                        payload = LtReconnect(sessionToken = token)
                    )
                }
                pendingCommand?.invoke()
                pendingCommand = null
            }

            is LtConnectionState.Failed -> {
                pendingCommand = null
                clearRoom()
                events.tryEmit(
                    value = ListenTogetherEvent.Error(
                        code = "connection_failed",
                        message = connectionState.reason
                    )
                )
            }

            else -> Unit
        }
    }

    private fun onFrame(frame: LtFrame) {
        runCatching {
            handleFrame(frame = frame)
        }
    }

    private fun handleFrame(frame: LtFrame) {
        when (frame.type) {
            LtMessageType.ROOM_CREATED -> {
                val payload = frame.decode<LtRoomCreated>()
                sessionToken = payload.sessionToken
                state.value = ListenTogetherState.InRoom(
                    connection = state.value.connection,
                    room = ListenTogetherRoom(
                        roomCode = payload.roomCode,
                        myUserId = payload.userId,
                        hostId = payload.userId,
                        members = listOf(
                            RoomMember(
                                userId = payload.userId,
                                username = settings.displayName,
                                isHost = true,
                                isConnected = true,
                                isMe = true,
                                isBuffering = false,
                            )
                        ),
                    ),
                )
            }

            LtMessageType.JOIN_APPROVED -> {
                val payload = frame.decode<LtJoinApproved>()
                sessionToken = payload.sessionToken
                enterRoom(
                    roomCode = payload.roomCode,
                    myUserId = payload.userId,
                    roomState = payload.state
                )
                requestSync()
            }

            LtMessageType.RECONNECTED -> {
                val payload = frame.decode<LtReconnected>()
                enterRoom(
                    roomCode = payload.roomCode,
                    myUserId = payload.userId,
                    roomState = payload.state
                )
                requestSync()
            }

            LtMessageType.JOIN_REJECTED -> {
                state.value = ListenTogetherState.Idle(connection = state.value.connection)
                events.tryEmit(value = ListenTogetherEvent.JoinRejected(reason = frame.decode<LtReason>().reason))
            }

            LtMessageType.JOIN_REQUEST -> onJoinRequest(payload = frame.decode<LtUserEvent>())

            LtMessageType.USER_JOINED, LtMessageType.USER_RECONNECTED -> {
                val payload = frame.decode<LtUserEvent>()
                updateRoom { room ->
                    room.copy(members = room.members.upsert(userId = payload.userId) { existing ->
                        (existing ?: newMember(
                            room = room,
                            userId = payload.userId,
                            username = payload.username
                        ))
                            .copy(username = payload.username, isConnected = true)
                    })
                }
            }

            LtMessageType.USER_DISCONNECTED -> {
                val payload = frame.decode<LtUserEvent>()
                updateRoom { room ->
                    room.copy(members = room.members.map {
                        if (it.userId == payload.userId) {
                            it.copy(isConnected = false)
                        } else {
                            it
                        }
                    })
                }
            }

            LtMessageType.USER_LEFT -> {
                val payload = frame.decode<LtUserEvent>()
                updateRoom { room ->
                    room.copy(members = room.members.filterNot {
                        it.userId == payload.userId
                    })
                }
            }

            LtMessageType.HOST_CHANGED -> {
                val payload = frame.decode<LtHostChanged>()
                updateRoom { room ->
                    room.copy(
                        hostId = payload.newHostId,
                        members = room.members.map {
                            it.copy(isHost = it.userId == payload.newHostId)
                        },
                    )
                }
                events.tryEmit(
                    value = ListenTogetherEvent.HostChanged(
                        newHostName = payload.newHostName,
                        isMe = currentRoom?.myUserId == payload.newHostId,
                    )
                )
            }

            LtMessageType.KICKED -> {
                sessionToken = null
                clearRoom()
                connection.disconnect()
                events.tryEmit(value = ListenTogetherEvent.Kicked(reason = frame.decode<LtReason>().reason))
            }

            LtMessageType.SYNC_PLAYBACK -> onPlaybackAction(action = frame.decode<LtPlaybackAction>())

            LtMessageType.SYNC_STATE -> {
                val payload = frame.decode<LtSyncState>()
                updateRoom { room ->
                    room.copy(
                        playback = RoomPlayback(
                            track = payload.currentTrack,
                            queue = payload.queue,
                            queueTitle = payload.queueTitle,
                            isPlaying = payload.isPlaying,
                            positionMs = payload.position,
                            referenceServerTime = payload.lastUpdate,
                            revision = payload.revision,
                        )
                    )
                }
            }

            LtMessageType.BUFFER_WAIT -> {
                val payload = frame.decode<LtBufferWait>()
                updateRoom { room ->
                    room.copy(
                        waitingFor = payload.waitingFor,
                        members = room.members.map {
                            it.copy(isBuffering = it.userId in payload.waitingFor)
                        },
                    )
                }
            }

            LtMessageType.BUFFER_COMPLETE -> updateRoom { room ->
                room.copy(
                    waitingFor = emptyList(),
                    members = room.members.map {
                        it.copy(isBuffering = false)
                    },
                )
            }

            LtMessageType.SUGGESTION_RECEIVED -> onSuggestion(payload = frame.decode<LtSuggestionReceived>())

            LtMessageType.SUGGESTION_APPROVED -> events.tryEmit(value = ListenTogetherEvent.SuggestionApproved)

            LtMessageType.SUGGESTION_REJECTED -> events.tryEmit(
                ListenTogetherEvent.SuggestionRejected(reason = frame.decode<LtSuggestionResult>().reason)
            )

            LtMessageType.SERVER_RESTARTING -> events.tryEmit(value = ListenTogetherEvent.ServerRestarting)

            LtMessageType.ERROR -> {
                val payload = frame.decode<LtError>()
                if (state.value is ListenTogetherState.WaitingApproval) {
                    state.value = ListenTogetherState.Idle(connection = state.value.connection)
                }
                events.tryEmit(
                    value = ListenTogetherEvent.Error(
                        code = payload.code,
                        message = payload.message
                    )
                )
            }
        }
    }

    private fun onPlaybackAction(action: LtPlaybackAction) {
        updateRoom { room ->
            val playback = room.playback
            val reference = action.serverTime.takeIf {
                it > 0L
            } ?: action.capturedAtServerTime.takeIf {
                it > 0L
            } ?: clock.serverNow()
            val next = when (action.action) {
                LtAction.CHANGE_TRACK -> playback.copy(
                    track = action.trackInfo ?: playback.track,
                    queue = action.queue.ifEmpty {
                        playback.queue
                    },
                    queueTitle = action.queueTitle.ifEmpty {
                        playback.queueTitle
                    },
                    isPlaying = false,
                    positionMs = action.position,
                    referenceServerTime = reference,
                )

                LtAction.PLAY -> playback.copy(
                    isPlaying = true,
                    positionMs = action.position,
                    referenceServerTime = reference
                )

                LtAction.PAUSE -> playback.copy(
                    isPlaying = false,
                    positionMs = action.position,
                    referenceServerTime = reference
                )

                LtAction.SEEK -> playback.copy(
                    positionMs = action.position,
                    referenceServerTime = reference
                )

                LtAction.SYNC_QUEUE -> playback.copy(
                    queue = action.queue,
                    queueTitle = action.queueTitle.ifEmpty {
                        playback.queueTitle
                    },
                )

                else -> playback
            }
            room.copy(
                playback = next.copy(
                    revision = maxOf(
                        a = next.revision,
                        b = action.revision
                    )
                )
            )
        }
    }

    private fun onJoinRequest(payload: LtUserEvent) {
        when {
            payload.username in settings.blockedNames -> rejectJoin(userId = payload.userId)
            settings.autoApproveJoins -> approveJoin(userId = payload.userId)
            else -> updateRoom { room ->
                room.copy(
                    joinRequests = room.joinRequests + JoinRequest(
                        userId = payload.userId,
                        username = payload.username
                    )
                )
            }
        }
    }

    private fun onSuggestion(payload: LtSuggestionReceived) {
        val track = payload.trackInfo ?: return
        if (settings.autoApproveSuggestions) {
            connection.send(
                type = LtMessageType.APPROVE_SUGGESTION,
                payload = LtSuggestionTarget(suggestionId = payload.suggestionId),
            )
            return
        }
        updateRoom { room ->
            room.copy(
                suggestions = room.suggestions + TrackSuggestion(
                    suggestionId = payload.suggestionId,
                    fromUserId = payload.fromUserId,
                    fromUsername = payload.fromUsername,
                    track = track,
                )
            )
        }
    }

    private fun removeSuggestion(suggestionId: String) {
        updateRoom { room ->
            room.copy(suggestions = room.suggestions.filterNot {
                it.suggestionId == suggestionId
            })
        }
    }

    private fun enterRoom(roomCode: String, myUserId: String, roomState: LtRoomState?) {
        state.value = ListenTogetherState.InRoom(
            connection = state.value.connection,
            room = ListenTogetherRoom(
                roomCode = roomCode,
                myUserId = myUserId,
                hostId = roomState?.hostId.orEmpty(),
                members = roomState?.users.orEmpty().map {
                    RoomMember(
                        userId = it.userId,
                        username = it.username,
                        isHost = it.isHost,
                        isConnected = it.isConnected,
                        isMe = it.userId == myUserId,
                        isBuffering = false,
                    )
                },
                playback = RoomPlayback(
                    track = roomState?.currentTrack,
                    queue = roomState?.queue.orEmpty(),
                    queueTitle = roomState?.queueTitle.orEmpty(),
                    isPlaying = roomState?.isPlaying == true,
                    positionMs = roomState?.position ?: 0L,
                    referenceServerTime = roomState?.lastUpdate ?: 0L,
                    revision = roomState?.revision ?: 0L,
                ),
            ),
        )
    }

    private fun newMember(room: ListenTogetherRoom, userId: String, username: String) = RoomMember(
        userId = userId,
        username = username,
        isHost = userId == room.hostId,
        isConnected = true,
        isMe = userId == room.myUserId,
        isBuffering = false,
    )

    private fun List<RoomMember>.upsert(
        userId: String,
        transform: (RoomMember?) -> RoomMember
    ): List<RoomMember> {
        val index = indexOfFirst {
            it.userId == userId
        }
        return if (index >= 0) {
            toMutableList().also {
                it[index] = transform(it[index])
            }
        } else {
            this + transform(null)
        }
    }

    private fun updateRoom(transform: (ListenTogetherRoom) -> ListenTogetherRoom) {
        val current = state.value as? ListenTogetherState.InRoom ?: return
        state.value = current.copy(room = transform(current.room))
    }

    private fun clearRoom() {
        sessionToken = null
        state.value = ListenTogetherState.Idle(connection = state.value.connection)
    }
}
