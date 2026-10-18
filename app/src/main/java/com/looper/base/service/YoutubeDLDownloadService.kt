package com.looper.base.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.looper.base.R
import com.looper.base.repository.youtubedl.YOUTUBE_DL_PATH_MARKER
import com.looper.base.repository.youtubedl.YoutubeDLOutputNaming
import com.looper.base.repository.youtubedl.applyRequestContext
import com.looper.base.repository.youtubedl.applyYoutubeDLCommonOptions
import com.looper.base.repository.youtubedl.applyYoutubeDLDownloadOptions
import com.looper.base.repository.youtubedl.createYoutubeDLDownloadRequest
import com.looper.base.repository.youtubedl.data.TERMINAL_YOUTUBE_DL_STATUSES
import com.looper.base.repository.youtubedl.data.YoutubeDLDownloadStatus
import com.looper.base.repository.youtubedl.ensureYoutubeDLInitialized
import com.looper.base.repository.youtubedl.findMarkedOutput
import com.looper.base.repository.youtubedl.isYouTubeSourceUrl
import com.looper.videodownloader.database.dao.YoutubeDLDownloadDao
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class YoutubeDLDownloadService : Service() {
    private val dao: YoutubeDLDownloadDao by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val canceled = ConcurrentHashMap.newKeySet<String>()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "YTDL_SERVICE onCreate")
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID)

        Log.d(
            TAG,
            "YTDL_SERVICE onStartCommand action=${intent?.action} id=$id startId=$startId running=$running"
        )

        if (id == null) {
            Log.e(TAG, "YTDL_SERVICE missing task id")
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_CANCEL) {
            Log.d(TAG, "YTDL_SERVICE dispatching cancel id=$id")
            scope.launch { this@YoutubeDLDownloadService.cancel(id) }
            return START_NOT_STICKY
        }

        startForeground(id.notificationId(), notification("Preparing download", 0, id))

        if (running.add(id)) {
            Log.d(TAG, "YTDL_SERVICE dispatching execute id=$id")
            scope.launch { execute(id) }
        } else {
            Log.w(TAG, "YTDL_SERVICE execute ignored because id is already running id=$id")
        }

        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun execute(id: String) {
        Log.d(TAG, "YTDL_SERVICE execute entered id=$id")
        val task = dao.get(id)

        if (task == null) {
            Log.e(TAG, "YTDL_SERVICE task not found id=$id")
            return finish(id)
        }

        if (task.status in TERMINAL_YOUTUBE_DL_STATUSES || canceled.contains(id)) {
            return finish(id)
        }

        val tempPath = task.tempDirectory
        if (tempPath == null) {
            Log.e(TAG, "YTDL_SERVICE missing temp directory id=$id")
            fail(id, "Missing temporary directory")
            task.cookieFilePath.deleteCookieFile(id)
            return finish(id)
        }

        val tempDir = File(tempPath)
        val isAudio = task.suggestedExtension.equals("mp3", ignoreCase = true)
        var publishedUri: Uri? = null

        try {
            tempDir.mkdirs()
            ensureYoutubeDLInitialized(this)
            if (!isTaskActive(id)) return
            dao.updateState(id, YoutubeDLDownloadStatus.RUNNING.name, 0, 0, now(), null)

            val request = createYoutubeDLDownloadRequest(task.sourceUrl, tempDir)
                .applyYoutubeDLCommonOptions()
                .applyRequestContext(
                    sourceUrl = task.sourceUrl,
                    cookieFilePath = task.cookieFilePath?.takeIf {
                        File(it).isFile
                    },
                    userAgent = task.userAgent,
                    referer = task.referer,
                )
                .applyYoutubeDLDownloadOptions(
                    formatSelector = task.formatSelector,
                    outputDirectory = tempDir,
                    isAudio = isAudio,
                )

            Log.i(
                TAG,
                "YTDL_PROCESS starting id=$id processId=${task.processId} " + "source=${task.sourceUrl} selector=${task.formatSelector} " + "temp=${tempDir.absolutePath} ytDlpVersion=${
                    YoutubeDL.version(this) ?: "unknown"
                }",
            )

            Log.d(
                TAG,
                "YTDL_PROCESS options id=$id " + "format=${request.getOption("--format")} " + "mergeOutput=${
                    request.getOption("--merge-output-format")
                } " + "hasCookies=${request.hasOption("--cookies")} " + "hasUserAgent=${
                    request.hasOption(
                        "--user-agent"
                    )
                } " + "hasReferer=${request.hasOption("--referer")}",
            )

            if (!isTaskActive(id)) return
            var persistedProgress = 0
            val response = YoutubeDL.execute(
                request = request,
                processId = task.processId,
                redirectErrorStream = false,
            ) { progress, eta, line ->
                if (canceled.contains(id)) {
                    // Cancellation may arrive just before the native process is registered.
                    YoutubeDL.destroyProcessById(task.processId)
                    return@execute
                }
                val outputLine = line.trim()
                if (outputLine.isNotEmpty() && !outputLine.startsWith("[download]") && (outputLine.startsWith(
                        "["
                    ) || outputLine.startsWith("YTDL_SELECTED:") || outputLine.startsWith(
                        YOUTUBE_DL_PATH_MARKER
                    ))
                ) {
                    Log.d(TAG, "YTDL_PROCESS line id=$id $outputLine")
                }

                val value = nextYoutubeDLProgress(
                    currentProgress = persistedProgress,
                    reportedProgress = progress,
                ) ?: return@execute

                persistedProgress = value

                Log.d(
                    TAG,
                    "YTDL_PROCESS progress id=$id percent=$value etaSeconds=${eta.coerceAtLeast(0L)}"
                )

                val updated = runBlocking {
                    if (!isTaskActive(id)) return@runBlocking false
                    dao.updateState(
                        id,
                        YoutubeDLDownloadStatus.RUNNING.name,
                        value,
                        eta.coerceAtLeast(0L),
                        now(),
                        null,
                    )
                    true
                }
                if (!updated) return@execute

                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
                    id.notificationId(), notification(task.title, value, id)
                )
            }

            Log.i(
                TAG,
                "YTDL_PROCESS finished id=$id exitCode=${response.exitCode} " + "elapsedMs=${response.elapsedTime} outputTail=${
                    response.out.takeLast(4_000)
                } " + "diagnosticTail=${response.err.takeLast(4_000)}",
            )

            if (!isTaskActive(id)) return
            val output = findMarkedOutput(response.out, tempDir)
                ?: throw IllegalStateException("yt-dlp did not report a final output")

            Log.i(
                TAG,
                "YTDL_PROCESS final file id=$id path=${output.absolutePath} " + "extension=${output.extension} size=${output.length()}",
            )

            if (isAudio) {
                validateAudio(output)
            } else {
                validateVideoAndAudio(output, isYouTube = task.sourceUrl.isYouTubeSourceUrl())
            }
            Log.i(TAG, "YTDL_MEDIA validation success id=$id")

            val displayName = if (isAudio) {
                YoutubeDLOutputNaming.finalAudioName(task.requestedName)
            } else {
                YoutubeDLOutputNaming.finalName(task.requestedName, output.extension)
            }
            val mime = if (isAudio) "audio/mpeg" else mimeFor(output.extension)
            if (!isTaskActive(id)) return
            val uri = publish(output, displayName, mime, isAudio)
            publishedUri = uri

            Log.d(TAG, "YTDL_SERVICE publish success id=$id uri=$uri name=$displayName mime=$mime")

            if (!isTaskActive(id)) return
            val savedName = if (uri.scheme == "file") uri.lastPathSegment ?: displayName else displayName
            dao.complete(id, uri.toString(), savedName, mime, output.length(), now())
            if (canceled.contains(id) || dao.get(id)?.status != YoutubeDLDownloadStatus.COMPLETED.name) return
            publishedUri = null

            Log.d(TAG, "YTDL_SERVICE database completed id=$id")

        } catch (error: Throwable) {
            Log.e(
                TAG,
                "YTDL_PROCESS failed id=$id source=${task.sourceUrl} " + "selector=${task.formatSelector} type=${error.javaClass.simpleName} " + "message=${error.message}",
                error,
            )

            if (isTaskActive(id)) {
                fail(id, error.message ?: error.javaClass.simpleName)
            }

        } finally {
            publishedUri?.let { uri ->
                runCatching {
                    if (uri.scheme == "file") {
                        uri.path?.let { File(it).delete() }
                    } else {
                        contentResolver.delete(uri, null, null)
                    }
                }
            }
            tempDir.deleteRecursively()
            task.cookieFilePath.deleteCookieFile(id)
            finish(id)
        }
    }

    private suspend fun cancel(id: String) {
        Log.d(TAG, "YTDL_SERVICE cancel entered id=$id running=${running.contains(id)}")
        val task = dao.get(id)

        if (task == null) {
            Log.w(TAG, "YTDL_SERVICE cancel task already removed id=$id")
            if (!running.contains(id)) finish(id)
            return
        }

        if (task.status in TERMINAL_YOUTUBE_DL_STATUSES) {
            if (!running.contains(id)) finish(id)
            return
        }
        canceled.add(id)
        dao.updateState(id, YoutubeDLDownloadStatus.CANCELED.name, task.progress, 0, now(), null)

        Log.d(
            TAG,
            "YTDL_SERVICE destroying process id=$id processId=${task.processId} status=${task.status} progress=${task.progress}"
        )

        val destroyed = YoutubeDL.destroyProcessById(task.processId)

        Log.d(
            TAG,
            "YTDL_SERVICE destroyProcessById returned id=$id processId=${task.processId} destroyed=$destroyed"
        )

        task.tempDirectory?.let { File(it).deleteRecursively() }
        task.cookieFilePath.deleteCookieFile(id)

        dao.delete(id)
        Log.d(TAG, "YTDL_SERVICE database task deleted after cancel id=$id")
        if (!running.contains(id)) finish(id)
    }

    private suspend fun isTaskActive(id: String): Boolean =
        !canceled.contains(id) && dao.get(id)?.status?.let {
            it !in TERMINAL_YOUTUBE_DL_STATUSES
        } == true

    private fun validateAudio(file: File) {
        check(file.extension.equals("mp3", ignoreCase = true) && file.length() > 0L) {
            "Audio output must be a non-empty MP3 file"
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var mp3Audio = false
            var video = false
            repeat(extractor.trackCount) { index ->
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.equals("audio/mpeg", ignoreCase = true)) mp3Audio = true
                if (mime.startsWith("video/", ignoreCase = true)) video = true
            }
            check(mp3Audio && !video) { "Output must contain MP3 audio without a video stream" }
        } finally {
            extractor.release()
        }
    }

    private fun validateVideoAndAudio(file: File, isYouTube: Boolean) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)

            var video = false
            var audio = false
            var compatibleYouTubeVideo = false
            var compatibleYouTubeAudio = false

            repeat(extractor.trackCount) { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                val width = format.intValueOrNull(MediaFormat.KEY_WIDTH)
                val height = format.intValueOrNull(MediaFormat.KEY_HEIGHT)
                val codec = format.getString("codecs") ?: format.getString("codec-string")
                ?: format.getString("codec") ?: mime.substringAfter(
                    '/', missingDelimiterValue = "unknown"
                )

                Log.i(
                    TAG,
                    "YTDL_SERVICE media track index=$index mime=$mime codec=$codec " + "resolution=${width ?: "?"}x${height ?: "?"}",
                )

                when (mime.substringBefore('/')) {
                    "video" -> {
                        video = true
                        if (mime.equals("video/avc", ignoreCase = true)) compatibleYouTubeVideo =
                            true
                    }

                    "audio" -> {
                        audio = true

                        if (mime.equals(
                                "audio/mp4a-latm", ignoreCase = true
                            ) || mime.equals("audio/aac", ignoreCase = true)
                        ) {
                            compatibleYouTubeAudio = true
                        }
                    }
                }
            }

            Log.i(
                TAG,
                "YTDL_MEDIA track summary file=${file.name} tracks=${extractor.trackCount} " + "hasVideo=$video hasAudio=$audio isYouTube=$isYouTube " + "youtubeAvc=$compatibleYouTubeVideo youtubeAac=$compatibleYouTubeAudio",
            )

            check(video && audio) { "Output must contain both video and audio streams" }

            if (isYouTube) {
                check(compatibleYouTubeVideo) { "YouTube output must use H.264/AVC video" }
                check(compatibleYouTubeAudio) { "YouTube output must use AAC audio" }
            }
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.intValueOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private fun publish(file: File, name: String, mime: String, isAudio: Boolean): Uri {
        if (isAudio && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // App-specific storage does not require legacy external-storage permission.
            val music = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: File(filesDir, Environment.DIRECTORY_MUSIC)
            val directory = File(music, "Audio Downloader")
            check(directory.mkdirs() || directory.isDirectory) { "Unable to create audio directory" }
            var destination = File(directory, name)
            var suffix = 1
            while (!destination.createNewFile()) {
                destination = File(directory, "${name.substringBeforeLast('.')} (${suffix++}).mp3")
            }
            try {
                file.inputStream().use { input -> destination.outputStream().use { input.copyTo(it) } }
                return Uri.fromFile(destination)
            } catch (error: Throwable) {
                destination.delete()
                throw error
            }
        }

        val collection = if (isAudio) {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (isAudio) Environment.DIRECTORY_MUSIC + "/Audio Downloader"
                else Environment.DIRECTORY_MOVIES + "/Video Downloader"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(collection, values)
            ?: error("Unable to create MediaStore output")

        try {
            checkNotNull(contentResolver.openOutputStream(uri, "w")) { "Unable to open MediaStore output" }
                .use { output -> file.inputStream().use { it.copyTo(output) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            check(contentResolver.update(uri, values, null, null) > 0) { "Unable to publish MediaStore output" }
            return uri
        } catch (error: Throwable) {
            contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private suspend fun fail(id: String, message: String) {
        Log.e(TAG, "YTDL_SERVICE marking FAILED id=$id message=$message")
        dao.updateState(
            id = id,
            status = YoutubeDLDownloadStatus.FAILED.name,
            progress = 0,
            eta = 0,
            updatedAt = now(),
            error = message.take(500)
        )
    }

    private fun String?.deleteCookieFile(id: String) {
        this?.takeIf(String::isNotBlank)?.let { path ->
            val deleted = runCatching {
                File(path).delete()
            }.getOrDefault(false)

            Log.d(TAG, "YTDL_SERVICE cookie file cleanup id=$id success=$deleted")
        }
    }

    private fun finish(id: String) {
        val removed = running.remove(id)
        canceled.remove(id)

        Log.d(TAG, "YTDL_SERVICE finish id=$id removed=$removed remaining=$running")

        if (running.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.d(TAG, "YTDL_SERVICE stopSelf because no tasks remain")
            stopSelf()
        }
    }

    private fun notification(title: String, progress: Int, id: String): Notification {
        val intent = Intent(this, YoutubeDLDownloadService::class.java).setAction(ACTION_CANCEL)
            .putExtra(EXTRA_ID, id)

        val cancel = PendingIntent.getService(
            this,
            id.notificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_audio).setContentTitle(title)
            .setContentText("$progress%").setProgress(100, progress, false).setOngoing(true)
            .addAction(0, getString(R.string.app_name), cancel).build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Video downloads", NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "YTDL_SERVICE onDestroy running=$running")
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.looper.videodownloader.YTDL_CANCEL"
        const val EXTRA_ID = "download_id"
        private const val CHANNEL_ID = "youtube_dl_downloads"
        private const val TAG = "MTHAI"

        fun start(context: Context, id: String) {
            Log.d(TAG, "YTDL_SERVICE startForegroundService requested id=$id")
            val intent =
                Intent(context, YoutubeDLDownloadService::class.java).putExtra(EXTRA_ID, id)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

internal fun nextYoutubeDLProgress(
    currentProgress: Int,
    reportedProgress: Float,
): Int? {
    if (!reportedProgress.isFinite() || reportedProgress < 0f) return null
    val normalized = reportedProgress.toInt().coerceIn(0, 99)
    if (normalized <= currentProgress) return null
    return normalized
}

private fun String.notificationId() = hashCode() and 0x7fffffff
private fun now() = System.currentTimeMillis()
private fun mimeFor(extension: String) = when (extension.lowercase()) {
    "webm" -> "video/webm"
    "mkv" -> "video/x-matroska"
    "mov" -> "video/quicktime"
    else -> "video/mp4"
}
