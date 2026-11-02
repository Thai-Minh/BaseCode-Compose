package com.looper.base.listentogether.model

import com.looper.base.listentogether.net.LtConnectionState
import com.looper.base.listentogether.protocol.LtTrackInfo

object ListenTogetherConstants {
    const val DEFAULT_SERVER_URL = "wss://metroserverx.meowery.eu/ws"
    const val ROOM_CODE_LENGTH = 8
    const val MAX_USERNAME_LENGTH = 50
    const val MAX_QUEUE = 200
}

data class ListenTogetherSettings(
    val serverUrl: String? = null,
    val displayName: String = "",
    val autoApproveJoins: Boolean = false,
    val autoApproveSuggestions: Boolean = false,
    val blockedNames: Set<String> = emptySet(),
) {
    val effectiveServerUrl: String
        get() = serverUrl?.takeIf {
            it.startsWith(prefix = "wss://") || it.startsWith(prefix = "ws://")
        } ?: ListenTogetherConstants.DEFAULT_SERVER_URL
}

data class RoomMember(
    val userId: String,
    val username: String,
    val isHost: Boolean,
    val isConnected: Boolean,
    val isMe: Boolean,
    val isBuffering: Boolean,
)

data class JoinRequest(
    val userId: String,
    val username: String,
)

data class TrackSuggestion(
    val suggestionId: String,
    val fromUserId: String,
    val fromUsername: String,
    val track: LtTrackInfo,
)

data class RoomPlayback(
    val track: LtTrackInfo? = null,
    val queue: List<LtTrackInfo> = emptyList(),
    val queueTitle: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val referenceServerTime: Long = 0L,
    val revision: Long = 0L,
) {
    fun positionAt(serverNow: Long): Long =
        if (isPlaying && referenceServerTime > 0L) {
            positionMs + (serverNow - referenceServerTime).coerceAtLeast(minimumValue = 0L)
        } else {
            positionMs
        }
}

data class ListenTogetherRoom(
    val roomCode: String,
    val myUserId: String,
    val hostId: String,
    val members: List<RoomMember> = emptyList(),
    val playback: RoomPlayback = RoomPlayback(),
    val joinRequests: List<JoinRequest> = emptyList(),
    val suggestions: List<TrackSuggestion> = emptyList(),
    val waitingFor: List<String> = emptyList(),
) {
    val isHost: Boolean
        get() = myUserId == hostId
}

sealed interface ListenTogetherState {
    val connection: LtConnectionState

    data class Idle(override val connection: LtConnectionState) : ListenTogetherState

    data class WaitingApproval(
        override val connection: LtConnectionState,
        val roomCode: String,
    ) : ListenTogetherState

    data class InRoom(
        override val connection: LtConnectionState,
        val room: ListenTogetherRoom,
    ) : ListenTogetherState
}

sealed interface ListenTogetherEvent {
    data class JoinRejected(val reason: String) : ListenTogetherEvent

    data class Kicked(val reason: String) : ListenTogetherEvent

    data class Error(val code: String, val message: String) : ListenTogetherEvent

    data class HostChanged(val newHostName: String, val isMe: Boolean) : ListenTogetherEvent

    data class SuggestionRejected(val reason: String) : ListenTogetherEvent

    data object SuggestionApproved : ListenTogetherEvent

    data object ServerRestarting : ListenTogetherEvent
}
