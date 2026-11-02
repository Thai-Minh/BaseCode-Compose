package com.looper.base.listentogether.net

import com.looper.base.listentogether.protocol.LtClientCapabilities
import com.looper.base.listentogether.protocol.LtCodec
import com.looper.base.listentogether.protocol.LtFrame
import com.looper.base.listentogether.protocol.LtMessageType
import com.looper.base.listentogether.protocol.LtPing
import com.looper.base.listentogether.protocol.LtPong
import com.looper.base.listentogether.protocol.LtServerCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.atomic.AtomicLong

sealed interface LtConnectionState {
    data object Disconnected : LtConnectionState

    data object Connecting : LtConnectionState

    data class Connected(
        val serverVersion: String,
        val canHost: Boolean,
        val isReconnect: Boolean,
    ) : LtConnectionState

    data class Reconnecting(val attempt: Int) : LtConnectionState

    data class Failed(val reason: String) : LtConnectionState
}

class LtConnection(
    private val okHttpClient: OkHttpClient,
    private val clock: LtClock,
    private val userAgent: String,
    private val scope: CoroutineScope,
) {

    private companion object {
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
        const val QUICK_PINGS = 3
        const val QUICK_PING_INTERVAL_MS = 1_000L
        const val PING_INTERVAL_MS = 15_000L
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val MAX_BACKOFF_MS = 60_000L
        const val NORMAL_CLOSURE = 1000
    }

    val state: StateFlow<LtConnectionState>
        field = MutableStateFlow<LtConnectionState>(value = LtConnectionState.Disconnected)

    val frames: SharedFlow<LtFrame>
        field = MutableSharedFlow<LtFrame>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var compress = false

    @Volatile
    private var userClosed = false

    private var url: String? = null
    private var attempt = 0
    private var hasConnectedOnce = false
    private var pingJob: Job? = null
    private var handshakeJob: Job? = null
    private var reconnectJob: Job? = null
    private val pingSequence = AtomicLong(0L)

    fun connect(url: String) {
        if (socket != null && this.url == url && state.value !is LtConnectionState.Failed) {
            return
        }
        close(code = NORMAL_CLOSURE)
        this.url = url
        userClosed = false
        attempt = 0
        hasConnectedOnce = false
        clock.reset()
        open()
    }

    fun disconnect() {
        userClosed = true
        reconnectJob?.cancel()
        close(code = NORMAL_CLOSURE)
        state.value = LtConnectionState.Disconnected
    }

    inline fun <reified T> send(type: String, payload: T): Boolean =
        sendBytes(
            bytes = LtCodec.encode(
                type = type,
                payload = payload,
                compress = isCompressionEnabled()
            )
        )

    fun isCompressionEnabled(): Boolean = compress

    fun sendBytes(bytes: ByteArray): Boolean = socket?.send(bytes.toByteString()) == true

    private fun open() {
        val target = url ?: return
        state.value = if (hasConnectedOnce) {
            LtConnectionState.Reconnecting(attempt = attempt)
        } else {
            LtConnectionState.Connecting
        }
        val request = Request.Builder()
            .url(target)
            .header(name = "User-Agent", value = userAgent)
            .build()
        socket = okHttpClient.newWebSocket(request = request, listener = Listener())
    }

    private fun close(code: Int) {
        pingJob?.cancel()
        handshakeJob?.cancel()
        socket?.close(code = code, reason = null)
        socket = null
    }

    private fun onHandshake(capabilities: LtServerCapabilities) {
        handshakeJob?.cancel()
        compress = capabilities.supportsCompression
        val isReconnect = hasConnectedOnce
        hasConnectedOnce = true
        attempt = 0
        state.value = LtConnectionState.Connected(
            serverVersion = capabilities.serverVersion,
            canHost = capabilities.canHost,
            isReconnect = isReconnect,
        )
        startPings()
    }

    private fun startPings() {
        pingJob?.cancel()
        pingJob = scope.launch {
            repeat(times = QUICK_PINGS) {
                sendPing()
                delay(timeMillis = QUICK_PING_INTERVAL_MS)
            }
            while (isActive) {
                delay(timeMillis = PING_INTERVAL_MS)
                sendPing()
            }
        }
    }

    private fun sendPing() {
        send(
            type = LtMessageType.PING,
            payload = LtPing(
                clientTime = System.currentTimeMillis(),
                sequence = pingSequence.incrementAndGet()
            ),
        )
    }

    private fun scheduleReconnect(reason: String) {
        close(code = NORMAL_CLOSURE)
        if (userClosed) {
            return
        }
        if (!hasConnectedOnce || attempt >= MAX_RECONNECT_ATTEMPTS) {
            state.value = LtConnectionState.Failed(reason = reason)
            return
        }
        attempt++
        state.value = LtConnectionState.Reconnecting(attempt = attempt)
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(timeMillis = (1_000L shl (attempt - 1)).coerceAtMost(maximumValue = MAX_BACKOFF_MS))
            if (!userClosed) {
                open()
            }
        }
    }

    private inner class Listener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (webSocket !== socket) {
                return
            }
            webSocket.send(
                bytes = LtCodec.encode(
                    type = LtMessageType.CLIENT_CAPABILITIES,
                    payload = LtClientCapabilities(
                        supportsProtobuf = true,
                        supportsCompression = true,
                        clientVersion = userAgent
                    ),
                    compress = false,
                ).toByteString()
            )
            handshakeJob = scope.launch {
                delay(timeMillis = HANDSHAKE_TIMEOUT_MS)
                if (state.value !is LtConnectionState.Connected) {
                    scheduleReconnect(reason = "handshake_timeout")
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (webSocket !== socket) {
                return
            }
            val frame = runCatching {
                LtCodec.decode(bytes = bytes.toByteArray())
            }.getOrNull() ?: return
            when (frame.type) {
                LtMessageType.SERVER_CAPABILITIES -> runCatching {
                    frame.decode<LtServerCapabilities>()
                }.getOrNull()?.let {
                    scope.launch {
                        onHandshake(capabilities = it)
                    }
                }

                LtMessageType.PONG -> runCatching {
                    val pong = frame.decode<LtPong>()
                    clock.onPong(
                        clientSentAt = pong.clientTime,
                        serverReceivedAt = pong.serverReceiveTime,
                        serverSentAt = pong.serverSendTime,
                    )
                }

                else -> frames.tryEmit(value = frame)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== socket) {
                return
            }
            scope.launch {
                scheduleReconnect(reason = t.message ?: "connection_failed")
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== socket) {
                return
            }
            scope.launch {
                scheduleReconnect(reason = reason.ifEmpty {
                    "closed"
                })
            }
        }
    }
}
