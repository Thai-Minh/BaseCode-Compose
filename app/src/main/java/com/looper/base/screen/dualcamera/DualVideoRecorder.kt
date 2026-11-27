package com.looper.base.screen.dualcamera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import com.looper.base.screen.dualcamera.data.LayoutRect
import com.looper.base.screen.dualcamera.data.PreviewLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.FutureTask
import kotlin.math.roundToInt

private const val TAG = "DualVideoRecorder"

private const val VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC
private const val VIDEO_BIT_RATE = 6_000_000
private const val VIDEO_FRAME_RATE = 30
private const val FRAME_INTERVAL_NS = 1_000_000_000L / VIDEO_FRAME_RATE

private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
private const val AUDIO_SAMPLE_RATE = 44_100
private const val AUDIO_BIT_RATE = 128_000

/** Max time video waits for the audio track before recording without sound. */
private const val MAX_AUDIO_WAIT_US = 1_000_000L

/**
 * Recording waits this long for every camera's first frame (their sessions are being
 * reconfigured with the recorder surface). A camera still without a frame is then failed (red).
 */
private const val FIRST_FRAME_GRACE_NS = 3_000_000_000L

/** A camera that stops delivering frames for this long is considered failed (red). */
private const val STALE_FRAME_NS = 1_000_000_000L

private val FAILED_COLOR = glColor(PreviewLayout.FAILED_CAMERA_COLOR)

/** An opaque ARGB color int as GL RGBA components. */
private fun glColor(argb: Int) = floatArrayOf(
    ((argb shr 16) and 0xFF) / 255f,
    ((argb shr 8) and 0xFF) / 255f,
    (argb and 0xFF) / 255f,
    1f,
)

/**
 * Records the Camera2 dual preview into one MP4 that looks exactly like the screen, in any
 * [PreviewLayout] (split halves or full screen + PiP).
 *
 * Each camera streams into its own [SurfaceTexture] ([backSurface] / [frontSurface]); a GL
 * thread composites the latest frames at 30 fps into the encoder's input surface. A camera
 * that has no stream, never delivers a frame, or stops delivering frames is painted red.
 * [secondPlaceholderColor] fills the second slot instead of a camera (single-camera mode).
 * Audio (optional) is recorded from the microphone as AAC.
 *
 * Call [stop] to finalize and save into `Movies/DualCamera`, or [cancel] to discard.
 */
class DualVideoRecorder(
    private val context: Context,
    layout: PreviewLayout,
    isSwapped: Boolean,
    backStream: Camera2StreamInfo?,
    frontStream: Camera2StreamInfo?,
    withAudio: Boolean,
    secondPlaceholderColor: Int? = null,
) {
    /** Camera output for the back / front camera, null if that camera has no stream. */
    val backSurface: Surface?
    val frontSurface: Surface?

    /**
     * True once the first frame is recorded (cameras need time to restart their sessions with
     * the recorder surface). The recording's duration counts from this moment.
     */
    val isStarted: StateFlow<Boolean>
        field = MutableStateFlow(false)

    private val outputFile = File(context.cacheDir, "dual_${System.nanoTime()}.mp4")
    private val glThread = HandlerThread("DualVideoGl").apply { start() }
    private val glHandler = Handler(glThread.looper)

    private val outputSize: Size
    private val videoEncoder: MediaCodec
    private val encoderSurface: Surface
    private val muxer: MediaMuxer
    private val muxerLock = Any()

    private var audio: AudioPipeline? = null

    // Muxer state, guarded by muxerLock.
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private var firstVideoPtsUs = -1L
    private var lastVideoPtsUs = -1L
    private var lastAudioPtsUs = -1L
    private var videoSamples = 0
    private var audioSamples = 0

    /** Encoded samples produced before the muxer could start (it needs every track format). */
    private val pendingSamples = ArrayDeque<PendingSample>()
    private var firstPendingVideoPtsUs = -1L

    // GL state, only touched on the GL thread.
    private lateinit var gl: GlCompositor
    private val backInput: CameraInput?
    private val frontInput: CameraInput?
    private val mainSlot: Slot
    private val secondSlot: Slot
    private var startNanos = 0L
    private var rendering = false
    private var renderedFrames = 0
    private var nextFrameAtNanos = 0L

    init {
        val aspect = layout.viewportHeight / layout.viewportWidth
        val (codecName, size) = chooseEncoder(aspect)
        outputSize = size
        videoEncoder = (codecName?.let(MediaCodec::createByCodecName)
            ?: MediaCodec.createEncoderByType(VIDEO_MIME)).apply {
            configure(videoFormat(size), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        encoderSurface = videoEncoder.createInputSurface()
        muxer = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Map the on-screen layout onto the output frame (GL coords: origin bottom-left).
        val scale = size.width / layout.viewportWidth
        val height = size.height.toFloat()
        fun LayoutRect.toGl() = floatArrayOf(
            left * scale,
            height - bottom * scale,
            right * scale,
            height - top * scale,
        )
        val mainRect = layout.main.toGl()
        val secondRect = layout.second.toGl()

        try {
            val inputs = runOnGl {
                gl = GlCompositor(encoderSurface)
                backStream?.let(::createInput) to frontStream?.let(::createInput)
            }
            backInput = inputs.first
            frontInput = inputs.second
            backSurface = backInput?.surface
            frontSurface = frontInput?.surface

            val (mainInput, secondInput) =
                if (isSwapped) frontInput to backInput else backInput to frontInput
            mainSlot = Slot(mainInput, mainRect, radius = 0f)
            secondSlot = Slot(
                secondInput,
                secondRect,
                radius = layout.secondCornerRadius * scale,
                placeholderColor = secondPlaceholderColor?.let(::glColor),
            )

            videoEncoder.start()
            if (withAudio) {
                // Sound is optional: a broken microphone must not prevent recording video.
                audio = runCatching { AudioPipeline().also { it.start() } }
                    .onFailure { Log.e(TAG, "Audio unavailable, recording without sound", it) }
                    .getOrNull()
            }
            Log.i(TAG, "Recording ${size.width}x${size.height}, back=$backStream, front=$frontStream, audio=${audio != null}")

            runOnGl {
                startNanos = System.nanoTime()
                nextFrameAtNanos = startNanos
                rendering = true
                glHandler.post(::renderLoop)
            }
        } catch (e: Exception) {
            releaseAll()
            outputFile.delete()
            throw e
        }
    }

    /** Stops recording, finalizes the MP4 and saves it to the gallery. */
    suspend fun stop(): Uri = withContext(Dispatchers.IO) {
        try {
            runOnGl {
                rendering = false // Pending renderLoop callbacks become no-ops.
                videoEncoder.signalEndOfInputStream()
                drain(videoEncoder, isVideo = true, endOfStream = true)
            }
            audio?.finish()
            synchronized(muxerLock) {
                // Audio never produced a format: save the video without sound.
                if (!muxerStarted && videoTrack >= 0) startMuxerLocked()
                Log.i(
                    TAG,
                    "Stopped: rendered=$renderedFrames muxer=$muxerStarted " +
                        "video=$videoSamples audio=$audioSamples",
                )
                check(muxerStarted && videoSamples > 0) { "No video frame was recorded" }
                muxer.stop()
            }
            DualCameraMedia.saveVideoFile(context, outputFile)
        } finally {
            releaseAll()
            outputFile.delete()
        }
    }

    /** Discards the recording (e.g. the screen is gone). */
    fun cancel() {
        audio?.abort()
        glHandler.post { rendering = false }
        Thread {
            releaseAll()
            outputFile.delete()
        }.start()
    }

    // region Rendering (GL thread)

    private class CameraInput(
        val stream: Camera2StreamInfo,
        val textureId: Int,
        val surfaceTexture: SurfaceTexture,
        val surface: Surface,
    ) {
        val texMatrix = FloatArray(16)
        var frameAvailable = false
        var lastFrameNanos = 0L
    }

    private class Slot(
        val input: CameraInput?,
        val rect: FloatArray,
        val radius: Float,
        /** Drawn instead of [input] (the window in single-camera mode). */
        val placeholderColor: FloatArray? = null,
    )

    private fun createInput(stream: Camera2StreamInfo): CameraInput {
        val textureId = gl.createExternalTexture()
        val surfaceTexture = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(stream.bufferSize.width, stream.bufferSize.height)
        }
        val input = CameraInput(stream, textureId, surfaceTexture, Surface(surfaceTexture))
        surfaceTexture.setOnFrameAvailableListener({ input.frameAvailable = true }, glHandler)
        return input
    }

    /**
     * Renders one frame and schedules the next one; stops once [rendering] is false.
     * A function (not a Runnable property) so it can be used from `init`, which runs before
     * properties declared below it are initialized.
     */
    private fun renderLoop() {
        if (!rendering) return
        try {
            renderFrame()
        } catch (e: Exception) {
            Log.e(TAG, "Render failed", e)
        }
        // Absolute schedule: a steady 30 fps without accumulating render-time drift.
        val now = System.nanoTime()
        nextFrameAtNanos = maxOf(nextFrameAtNanos + FRAME_INTERVAL_NS, now)
        glHandler.postDelayed(::renderLoop, (nextFrameAtNanos - now) / 1_000_000)
    }

    private fun renderFrame() {
        val now = System.nanoTime()
        listOfNotNull(backInput, frontInput).forEach { input ->
            if (input.frameAvailable) {
                input.frameAvailable = false
                input.surfaceTexture.updateTexImage()
                input.surfaceTexture.getTransformMatrix(input.texMatrix)
                input.lastFrameNanos = now
            }
        }

        // Don't encode anything until the cameras deliver: the video must start on real
        // footage, not on placeholder frames while the sessions restart.
        if (renderedFrames == 0 && !camerasReady(now)) return
        isStarted.value = true

        gl.beginFrame(outputSize.width, outputSize.height)
        drawSlot(mainSlot, now)
        drawSlot(secondSlot, now)
        if (!gl.swap(now) && renderedFrames == 0) {
            Log.e(TAG, "eglSwapBuffers failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
        }
        renderedFrames++
        drain(videoEncoder, isVideo = true, endOfStream = false)
    }

    /** True once every streaming camera has a frame, or the grace period is over. */
    private fun camerasReady(now: Long): Boolean =
        now - startNanos > FIRST_FRAME_GRACE_NS ||
            listOfNotNull(backInput, frontInput).all { it.lastFrameNanos != 0L }

    private fun drawSlot(slot: Slot, now: Long) {
        val input = slot.input
        val color = when {
            slot.placeholderColor != null -> slot.placeholderColor
            input == null -> FAILED_COLOR
            input.lastFrameNanos == 0L -> FAILED_COLOR
            now - input.lastFrameNanos > STALE_FRAME_NS -> FAILED_COLOR
            else -> null
        }
        if (color != null) {
            gl.drawColor(slot.rect, slot.radius, color)
        } else if (input != null) {
            gl.drawTexture(
                rect = slot.rect,
                radius = slot.radius,
                textureId = input.textureId,
                texMatrix = input.texMatrix,
                contentWidth = input.stream.uprightWidth,
                contentHeight = input.stream.uprightHeight,
            )
        }
    }

    // endregion

    // region Encoding / muxing

    private fun drain(codec: MediaCodec, isVideo: Boolean, endOfStream: Boolean) {
        val info = MediaCodec.BufferInfo()
        var idleTries = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // ~2 s max wait for the end-of-stream buffer.
                    if (!endOfStream || ++idleTries > 200) return
                }

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onOutputFormat(isVideo, codec.outputFormat)

                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (buffer != null && info.size > 0 && !isConfig) {
                        writeSample(isVideo, buffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private class PendingSample(
        val isVideo: Boolean,
        val data: ByteBuffer,
        val info: MediaCodec.BufferInfo,
    )

    private fun onOutputFormat(isVideo: Boolean, format: MediaFormat) = synchronized(muxerLock) {
        Log.i(TAG, "${if (isVideo) "Video" else "Audio"} format: $format")
        if (muxerStarted) {
            // Too late (we gave up waiting for it): this track is not recorded.
            Log.w(TAG, "Ignoring late ${if (isVideo) "video" else "audio"} format")
            return@synchronized
        }
        if (isVideo) videoTrack = muxer.addTrack(format) else audioTrack = muxer.addTrack(format)
        if (videoTrack >= 0 && (audio == null || audioTrack >= 0)) startMuxerLocked()
    }

    /** Starts the muxer and writes the samples buffered so far. Hold [muxerLock]. */
    private fun startMuxerLocked() {
        muxer.start()
        muxerStarted = true
        Log.i(TAG, "Muxer started, flushing ${pendingSamples.size} buffered samples")
        while (pendingSamples.isNotEmpty()) {
            val sample = pendingSamples.removeFirst()
            writeStartedLocked(sample.isVideo, sample.data, sample.info)
        }
    }

    private fun writeSample(isVideo: Boolean, buffer: ByteBuffer, info: MediaCodec.BufferInfo) =
        synchronized(muxerLock) {
            if (muxerStarted) {
                writeStartedLocked(isVideo, buffer, info)
                return@synchronized
            }

            // Keep a copy until every track format is known, so no frame is lost.
            val copy = ByteBuffer.allocateDirect(info.size)
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            copy.put(buffer).flip()
            val infoCopy = MediaCodec.BufferInfo().apply {
                set(0, info.size, info.presentationTimeUs, info.flags)
            }
            pendingSamples.addLast(PendingSample(isVideo, copy, infoCopy))

            if (isVideo && firstPendingVideoPtsUs < 0) firstPendingVideoPtsUs = info.presentationTimeUs
            // The audio encoder never produced a format: don't hold the video back forever.
            val waitedUs = info.presentationTimeUs - firstPendingVideoPtsUs
            if (isVideo && videoTrack >= 0 && audioTrack < 0 && waitedUs > MAX_AUDIO_WAIT_US) {
                Log.w(TAG, "No audio format after ${waitedUs / 1000} ms, recording without sound")
                startMuxerLocked()
            }
        }

    /** Writes one sample to the started muxer, keeping both tracks aligned. Hold [muxerLock]. */
    private fun writeStartedLocked(isVideo: Boolean, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val pts = info.presentationTimeUs
        if (isVideo) {
            if (firstVideoPtsUs < 0) {
                // A playable file must start on a key frame.
                if (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME == 0) return
                firstVideoPtsUs = pts
            }
            if (pts <= lastVideoPtsUs) return
            lastVideoPtsUs = pts
            muxer.writeSampleData(videoTrack, buffer, info)
            videoSamples++
        } else {
            if (audioTrack < 0) return
            // Keep audio aligned with the first written video frame.
            if (firstVideoPtsUs < 0 || pts < firstVideoPtsUs || pts <= lastAudioPtsUs) return
            lastAudioPtsUs = pts
            muxer.writeSampleData(audioTrack, buffer, info)
            audioSamples++
        }
    }

    /** Microphone -> AAC encoder, on its own thread. */
    private inner class AudioPipeline {
        private val minBuffer = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        @SuppressLint("MissingPermission") // Only created when RECORD_AUDIO is granted.
        private val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.CAMCORDER,
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 4,
        )
        private val encoder = MediaCodec.createEncoderByType(AUDIO_MIME).apply {
            val format = MediaFormat.createAudioFormat(AUDIO_MIME, AUDIO_SAMPLE_RATE, 1).apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBuffer * 2)
            }
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        @Volatile
        private var running = true
        private var lastPtsUs = -1L
        private var readErrorLogged = false
        private val thread = Thread(::loop, "DualVideoAudio")

        fun start() {
            check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }
            encoder.start()
            audioRecord.startRecording()
            thread.start()
        }

        /** Flushes the remaining audio and waits for the thread. */
        fun finish() {
            running = false
            thread.join()
        }

        fun abort() {
            running = false
        }

        private fun loop() {
            try {
                while (running) {
                    feed(endOfStream = false)
                    drain(encoder, isVideo = false, endOfStream = false)
                }
                var tries = 0
                while (!feed(endOfStream = true) && tries < 100) tries++
                drain(encoder, isVideo = false, endOfStream = true)
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording failed", e)
            } finally {
                runCatching { audioRecord.stop() }
            }
        }

        /** Returns true once a buffer was queued. */
        private fun feed(endOfStream: Boolean): Boolean {
            val index = encoder.dequeueInputBuffer(10_000L)
            if (index < 0) return false
            val buffer = encoder.getInputBuffer(index) ?: return false
            buffer.clear()
            val nowUs = System.nanoTime() / 1000
            if (endOfStream) {
                encoder.queueInputBuffer(
                    index, 0, 0, nextPts(nowUs), MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                return true
            }
            val read = audioRecord.read(buffer, buffer.remaining())
            if (read < 0 && !readErrorLogged) {
                readErrorLogged = true
                Log.e(TAG, "AudioRecord.read failed: $read")
            }
            val size = read.coerceAtLeast(0)
            // Timestamp of the first sample in this chunk (16-bit mono).
            val durationUs = size / 2 * 1_000_000L / AUDIO_SAMPLE_RATE
            encoder.queueInputBuffer(index, 0, size, nextPts(nowUs - durationUs), 0)
            return true
        }

        private fun nextPts(candidateUs: Long): Long =
            (if (candidateUs <= lastPtsUs) lastPtsUs + 1 else candidateUs).also { lastPtsUs = it }

        fun release() {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { audioRecord.release() }
        }
    }

    // endregion

    private fun releaseAll() {
        audio?.let {
            it.abort()
            it.release()
        }
        runCatching {
            runOnGl {
                listOfNotNull(backInput, frontInput).forEach {
                    it.surface.release()
                    it.surfaceTexture.release()
                }
                if (::gl.isInitialized) gl.release()
            }
        }.onFailure { Log.w(TAG, "GL release failed", it) }
        glThread.quitSafely()
        runCatching { videoEncoder.stop() }
        runCatching { videoEncoder.release() }
        runCatching { encoderSurface.release() }
        runCatching { muxer.release() }
    }

    private fun <T> runOnGl(block: () -> T): T {
        val task = FutureTask(block)
        check(glHandler.post(task)) { "GL thread is gone" }
        return try {
            task.get()
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause ?: e
        }
    }

    private fun videoFormat(size: Size) =
        MediaFormat.createVideoFormat(VIDEO_MIME, size.width, size.height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

    /**
     * Picks an AVC encoder and the largest output size (width 720 and down) that keeps the
     * screen aspect ratio. Dimensions are multiples of 16 for encoder compatibility.
     */
    private fun chooseEncoder(heightOverWidth: Float): Pair<String?, Size> {
        val encoder = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(VIDEO_MIME, ignoreCase = true) }
        }
        val capabilities = encoder?.getCapabilitiesForType(VIDEO_MIME)?.videoCapabilities
        val sizes = intArrayOf(720, 640, 576, 480).map { width ->
            Size(width, ((width * heightOverWidth) / 16f).roundToInt() * 16)
        }
        val size = sizes.firstOrNull { capabilities?.isSizeSupported(it.width, it.height) ?: true }
            ?: sizes.last()
        return encoder?.name to size
    }
}

/**
 * Minimal GLES 2 compositor drawing camera textures / solid colors into rounded rectangles.
 * Must be created, used and released on the same thread.
 */
private class GlCompositor(outputSurface: Surface) {
    private val display: EGLDisplay
    private val context: EGLContext
    private val surface: EGLSurface
    private val program: Int
    private val aPosition: Int
    private val aTexCoord: Int
    private val uTexMatrix: Int
    private val uRect: Int
    private val uRadius: Int
    private val uUseColor: Int
    private val uColor: Int
    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val positions = floatBuffer(8)
    private val texCoords = floatBuffer(8)
    private var width = 0
    private var height = 0

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }

        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            "No recordable EGL config"
        }
        val config = configs[0]
        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0,
        )
        surface = EGL14.eglCreateWindowSurface(
            display, config, outputSurface, intArrayOf(EGL14.EGL_NONE), 0
        )
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent failed" }

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uRect = GLES20.glGetUniformLocation(program, "uRect")
        uRadius = GLES20.glGetUniformLocation(program, "uRadius")
        uUseColor = GLES20.glGetUniformLocation(program, "uUseColor")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
    }

    fun createExternalTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    fun beginFrame(width: Int, height: Int) {
        this.width = width
        this.height = height
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)
    }

    /** [rect] = left, bottom, right, top in output pixels. */
    fun drawColor(rect: FloatArray, radius: Float, color: FloatArray) {
        GLES20.glUniform1i(uUseColor, 1)
        GLES20.glUniform4fv(uColor, 1, color, 0)
        draw(rect, radius, identity, floatArrayOf(0f, 0f, 1f, 1f))
    }

    /**
     * Draws the camera texture center-cropped into [rect] (like `ContentScale.Crop`).
     * [contentWidth] x [contentHeight] is the upright frame size after [texMatrix].
     */
    fun drawTexture(
        rect: FloatArray,
        radius: Float,
        textureId: Int,
        texMatrix: FloatArray,
        contentWidth: Int,
        contentHeight: Int,
    ) {
        val slotAspect = (rect[2] - rect[0]) / (rect[3] - rect[1])
        val contentAspect = contentWidth.toFloat() / contentHeight
        // Crop in upright texture space, before texMatrix maps it onto the buffer.
        val crop = if (contentAspect > slotAspect) {
            val keep = slotAspect / contentAspect
            floatArrayOf((1 - keep) / 2, 0f, (1 + keep) / 2, 1f)
        } else {
            val keep = contentAspect / slotAspect
            floatArrayOf(0f, (1 - keep) / 2, 1f, (1 + keep) / 2)
        }
        GLES20.glUniform1i(uUseColor, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        draw(rect, radius, texMatrix, crop)
    }

    private fun draw(rect: FloatArray, radius: Float, texMatrix: FloatArray, crop: FloatArray) {
        val left = rect[0] / width * 2 - 1
        val bottom = rect[1] / height * 2 - 1
        val right = rect[2] / width * 2 - 1
        val top = rect[3] / height * 2 - 1
        positions.put(floatArrayOf(left, bottom, right, bottom, left, top, right, top)).position(0)
        texCoords.put(
            floatArrayOf(
                crop[0], crop[1],
                crop[2], crop[1],
                crop[0], crop[3],
                crop[2], crop[3],
            )
        ).position(0)

        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES20.glUniform4f(uRect, rect[0], rect[1], rect[2], rect[3])
        GLES20.glUniform1f(uRadius, radius)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, positions)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texCoords)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** Submits the frame to the encoder; false if EGL refused it. */
    fun swap(presentationTimeNanos: Long): Boolean {
        EGLExt.eglPresentationTimeANDROID(display, surface, presentationTimeNanos)
        return EGL14.eglSwapBuffers(display, surface)
    }

    fun release() {
        GLES20.glDeleteProgram(program)
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(display)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Link failed: ${GLES20.glGetProgramInfoLog(program)}" }
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Shader failed: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    private companion object {
        const val EGL_RECORDABLE_ANDROID = 0x3142

        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        // Rounded-rect mask from a signed distance in window pixels (anti-aliased edge).
        const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            uniform vec4 uRect;
            uniform float uRadius;
            uniform int uUseColor;
            uniform vec4 uColor;
            void main() {
                vec2 center = (uRect.xy + uRect.zw) * 0.5;
                vec2 halfSize = (uRect.zw - uRect.xy) * 0.5;
                vec2 q = abs(gl_FragCoord.xy - center) - halfSize + vec2(uRadius);
                float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - uRadius;
                float alpha = clamp(0.5 - dist, 0.0, 1.0);
                vec4 color = uUseColor == 1 ? uColor : texture2D(sTexture, vTexCoord);
                gl_FragColor = vec4(color.rgb, alpha);
            }
        """

        fun floatBuffer(size: Int): FloatBuffer = ByteBuffer.allocateDirect(size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }
}
