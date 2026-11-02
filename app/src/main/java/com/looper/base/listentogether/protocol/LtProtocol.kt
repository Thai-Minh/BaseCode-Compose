@file:OptIn(ExperimentalSerializationApi::class)

package com.looper.base.listentogether.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

object LtMessageType {
    const val CLIENT_CAPABILITIES = "client_capabilities"
    const val CREATE_ROOM = "create_room"
    const val JOIN_ROOM = "join_room"
    const val LEAVE_ROOM = "leave_room"
    const val APPROVE_JOIN = "approve_join"
    const val REJECT_JOIN = "reject_join"
    const val PLAYBACK_ACTION = "playback_action"
    const val BUFFER_READY = "buffer_ready"
    const val KICK_USER = "kick_user"
    const val TRANSFER_HOST = "transfer_host"
    const val PING = "ping"
    const val REQUEST_SYNC = "request_sync"
    const val RECONNECT = "reconnect"
    const val SUGGEST_TRACK = "suggest_track"
    const val APPROVE_SUGGESTION = "approve_suggestion"
    const val REJECT_SUGGESTION = "reject_suggestion"

    const val SERVER_CAPABILITIES = "server_capabilities"
    const val ROOM_CREATED = "room_created"
    const val JOIN_REQUEST = "join_request"
    const val JOIN_APPROVED = "join_approved"
    const val JOIN_REJECTED = "join_rejected"
    const val USER_JOINED = "user_joined"
    const val USER_LEFT = "user_left"
    const val USER_DISCONNECTED = "user_disconnected"
    const val USER_RECONNECTED = "user_reconnected"
    const val SYNC_PLAYBACK = "sync_playback"
    const val BUFFER_WAIT = "buffer_wait"
    const val BUFFER_COMPLETE = "buffer_complete"
    const val ERROR = "error"
    const val PONG = "pong"
    const val HOST_CHANGED = "host_changed"
    const val KICKED = "kicked"
    const val SYNC_STATE = "sync_state"
    const val RECONNECTED = "reconnected"
    const val SUGGESTION_RECEIVED = "suggestion_received"
    const val SUGGESTION_APPROVED = "suggestion_approved"
    const val SUGGESTION_REJECTED = "suggestion_rejected"
    const val SERVER_RESTARTING = "server_restarting"
}

object LtAction {
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val SEEK = "seek"
    const val CHANGE_TRACK = "change_track"
    const val SYNC_QUEUE = "sync_queue"
}

@Serializable
data class LtEnvelope(
    @ProtoNumber(1) val type: String = "",
    @ProtoNumber(2) val payload: ByteArray = ByteArray(0),
    @ProtoNumber(3) val compressed: Boolean = false,
    @ProtoNumber(4) val requestId: Long = 0L,
)

@Serializable
data class LtTrackInfo(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val title: String = "",
    @ProtoNumber(3) val artist: String = "",
    @ProtoNumber(4) val album: String = "",
    @ProtoNumber(5) val duration: Long = 0L,
    @ProtoNumber(6) val thumbnail: String = "",
    @ProtoNumber(7) val suggestedBy: String = "",
)

@Serializable
data class LtUserInfo(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val username: String = "",
    @ProtoNumber(3) val isHost: Boolean = false,
    @ProtoNumber(4) val isConnected: Boolean = false,
)

@Serializable
data class LtRoomState(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val hostId: String = "",
    @ProtoNumber(3) val users: List<LtUserInfo> = emptyList(),
    @ProtoNumber(4) val currentTrack: LtTrackInfo? = null,
    @ProtoNumber(5) val isPlaying: Boolean = false,
    @ProtoNumber(6) val position: Long = 0L,
    @ProtoNumber(7) val lastUpdate: Long = 0L,
    @ProtoNumber(8) val volume: Float = 0f,
    @ProtoNumber(9) val queue: List<LtTrackInfo> = emptyList(),
    @ProtoNumber(10) val revision: Long = 0L,
    @ProtoNumber(11) val queueTitle: String = "",
)

@Serializable
data class LtCreateRoom(@ProtoNumber(1) val username: String = "")

@Serializable
data class LtJoinRoom(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val username: String = "",
)

@Serializable
class LtEmpty

@Serializable
data class LtUserTarget(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val reason: String = "",
)

@Serializable
data class LtPlaybackAction(
    @ProtoNumber(1) val action: String = "",
    @ProtoNumber(2) val trackId: String = "",
    @ProtoNumber(3) val position: Long = 0L,
    @ProtoNumber(4) val trackInfo: LtTrackInfo? = null,
    @ProtoNumber(5) val insertNext: Boolean = false,
    @ProtoNumber(6) val queue: List<LtTrackInfo> = emptyList(),
    @ProtoNumber(7) val queueTitle: String = "",
    @ProtoNumber(8) val volume: Float = 0f,
    @ProtoNumber(9) val serverTime: Long = 0L,
    @ProtoNumber(10) val revision: Long = 0L,
    @ProtoNumber(11) val capturedAtServerTime: Long = 0L,
)

@Serializable
data class LtPing(
    @ProtoNumber(1) val clientTime: Long = 0L,
    @ProtoNumber(2) val sequence: Long = 0L,
)

@Serializable
data class LtBufferReady(@ProtoNumber(1) val trackId: String = "")

@Serializable
data class LtTransferHost(@ProtoNumber(1) val newHostId: String = "")

@Serializable
data class LtSuggestTrack(@ProtoNumber(1) val trackInfo: LtTrackInfo? = null)

@Serializable
data class LtSuggestionTarget(
    @ProtoNumber(1) val suggestionId: String = "",
    @ProtoNumber(2) val reason: String = "",
)

@Serializable
data class LtReconnect(@ProtoNumber(1) val sessionToken: String = "")

@Serializable
data class LtRoomCreated(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val userId: String = "",
    @ProtoNumber(3) val sessionToken: String = "",
)

@Serializable
data class LtUserEvent(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val username: String = "",
)

@Serializable
data class LtJoinApproved(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val userId: String = "",
    @ProtoNumber(3) val sessionToken: String = "",
    @ProtoNumber(4) val state: LtRoomState? = null,
)

@Serializable
data class LtReason(@ProtoNumber(1) val reason: String = "")

@Serializable
data class LtBufferWait(
    @ProtoNumber(1) val trackId: String = "",
    @ProtoNumber(2) val waitingFor: List<String> = emptyList(),
)

@Serializable
data class LtError(
    @ProtoNumber(1) val code: String = "",
    @ProtoNumber(2) val message: String = "",
)

@Serializable
data class LtPong(
    @ProtoNumber(1) val clientTime: Long = 0L,
    @ProtoNumber(2) val serverReceiveTime: Long = 0L,
    @ProtoNumber(3) val serverSendTime: Long = 0L,
    @ProtoNumber(4) val sequence: Long = 0L,
)

@Serializable
data class LtHostChanged(
    @ProtoNumber(1) val newHostId: String = "",
    @ProtoNumber(2) val newHostName: String = "",
)

@Serializable
data class LtSyncState(
    @ProtoNumber(1) val currentTrack: LtTrackInfo? = null,
    @ProtoNumber(2) val isPlaying: Boolean = false,
    @ProtoNumber(3) val position: Long = 0L,
    @ProtoNumber(4) val lastUpdate: Long = 0L,
    @ProtoNumber(5) val queue: List<LtTrackInfo> = emptyList(),
    @ProtoNumber(6) val volume: Float = 0f,
    @ProtoNumber(7) val revision: Long = 0L,
    @ProtoNumber(8) val queueTitle: String = "",
)

@Serializable
data class LtReconnected(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val userId: String = "",
    @ProtoNumber(3) val state: LtRoomState? = null,
    @ProtoNumber(4) val isHost: Boolean = false,
)

@Serializable
data class LtSuggestionReceived(
    @ProtoNumber(1) val suggestionId: String = "",
    @ProtoNumber(2) val fromUserId: String = "",
    @ProtoNumber(3) val fromUsername: String = "",
    @ProtoNumber(4) val trackInfo: LtTrackInfo? = null,
)

@Serializable
data class LtSuggestionResult(
    @ProtoNumber(1) val suggestionId: String = "",
    @ProtoNumber(2) val reason: String = "",
)

@Serializable
data class LtClientCapabilities(
    @ProtoNumber(1) val supportsProtobuf: Boolean = false,
    @ProtoNumber(2) val supportsCompression: Boolean = false,
    @ProtoNumber(3) val clientVersion: String = "",
)

@Serializable
data class LtServerCapabilities(
    @ProtoNumber(1) val supportsProtobuf: Boolean = false,
    @ProtoNumber(2) val supportsCompression: Boolean = false,
    @ProtoNumber(3) val serverVersion: String = "",
    @ProtoNumber(5) val canHost: Boolean = false,
)
