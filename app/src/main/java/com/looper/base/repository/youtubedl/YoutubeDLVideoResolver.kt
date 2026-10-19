package com.looper.base.repository.youtubedl

import android.content.Context
import android.util.Log
import com.looper.base.repository.youtubedl.data.YoutubeDLRequestContext
import com.looper.base.repository.youtubedl.data.YoutubeDLResolvedAudio
import com.looper.base.repository.youtubedl.data.YoutubeDLResolvedVideo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

interface YoutubeDLVideoResolver {
    suspend fun resolve(
        url: String,
        requestContext: YoutubeDLRequestContext = YoutubeDLRequestContext(),
    ): YoutubeDLResolvedVideo

    /** Resolves audio metadata only. Downloading and MP3 conversion happen after confirmation. */
    suspend fun resolveV2(
        url: String,
        requestContext: YoutubeDLRequestContext = YoutubeDLRequestContext(),
    ): YoutubeDLResolvedAudio
}

class YoutubeDLVideoResolverImpl(context: Context) : YoutubeDLVideoResolver {
    private val appContext = context.applicationContext

    override suspend fun resolve(
        url: String,
        requestContext: YoutubeDLRequestContext,
    ): YoutubeDLResolvedVideo = withContext(Dispatchers.IO) {
        val requestedUrl = url.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Video URL must not be blank")

        val sourceUrl = requestedUrl.canonicalizeYouTubeUrl()
        val isYouTube = sourceUrl.isYouTubeSourceUrl()
        var cookieFile: File? = null

        Log.i(
            TAG,
            "YTDL_RESOLVER start requested=$requestedUrl source=$sourceUrl isYouTube=$isYouTube " +
                    "hasCookies=${!requestContext.rawCookieHeader.isNullOrBlank()} " +
                    "hasUserAgent=${!requestContext.userAgent.isNullOrBlank()} " +
                    "referer=${requestContext.referer ?: "-"}",
        )

        try {
            ensureYoutubeDLInitialized(appContext)
            Log.d(
                TAG,
                "YTDL_RESOLVER runtime ready version=${YoutubeDL.version(appContext) ?: "unknown"}",
            )

            fun resolveAttempt(mode: String, cookiePath: String?): YoutubeDLResolvedVideo {
                Log.i(
                    TAG,
                    "YTDL_RESOLVER attempt mode=$mode source=$sourceUrl " +
                            "hasCookies=${!cookiePath.isNullOrBlank()} playerClient=yt-dlp-default",
                )

                val info = YoutubeDL.getInfo(
                    YoutubeDLRequest(sourceUrl)
                        .applyYoutubeDLCommonOptions()
                        .applyRequestContext(
                            sourceUrl = sourceUrl,
                            cookieFilePath = cookiePath,
                            userAgent = requestContext.userAgent,
                            referer = requestContext.referer,
                        )
                )

                Log.i(
                    TAG,
                    "YTDL_RESOLVER getInfo success mode=$mode id=${info.id ?: "?"} " +
                            "extractor=${info.extractorKey ?: info.extractor ?: "?"} " +
                            "formats=${info.formats?.size ?: 0} " +
                            "requestedFormats=${info.requestedFormats?.size ?: 0}",
                )

                return info.toResolvedVideo(
                    sourceUrl = sourceUrl,
                    cookieFilePath = cookiePath,
                    userAgent = sourceUrl.youtubeDLUserAgent(requestContext.userAgent),
                    referer = requestContext.referer
                        ?.takeIf { !isYouTube && it.isNotBlank() },
                ).also { resolved ->
                    Log.i(
                        TAG,
                        "YTDL_RESOLVER attempt success mode=$mode id=${resolved.id} " +
                                "options=${resolved.formats.size}",
                    )
                }
            }

            val resolved = if (isYouTube) {
                try {
                    resolveAttempt(mode = "youtube-public", cookiePath = null)
                } catch (error: CancellationException) {
                    throw error
                } catch (publicError: Throwable) {
                    val rawCookies = requestContext.rawCookieHeader?.takeIf(String::isNotBlank)
                        ?: throw publicError
                    cookieFile = createNetscapeCookieFile(appContext, sourceUrl, rawCookies)
                    val authenticatedCookiePath = cookieFile?.absolutePath ?: throw publicError

                    Log.w(
                        TAG,
                        "YTDL_RESOLVER public attempt failed; retrying authenticated " +
                                "type=${publicError.javaClass.simpleName} " +
                                "message=${publicError.message?.lineSequence()?.lastOrNull()}",
                    )

                    try {
                        resolveAttempt(
                            mode = "youtube-authenticated-fallback",
                            cookiePath = authenticatedCookiePath,
                        )
                    } catch (error: Throwable) {
                        error.addSuppressed(publicError)
                        throw error
                    }
                }
            } else {
                cookieFile = requestContext.rawCookieHeader
                    ?.takeIf(String::isNotBlank)
                    ?.let { createNetscapeCookieFile(appContext, sourceUrl, it) }
                resolveAttempt(mode = "standard", cookiePath = cookieFile?.absolutePath)
            }

            Log.i(
                TAG,
                "YTDL_RESOLVER complete id=${resolved.id} options=${resolved.formats.size} " +
                        "usesCookies=${!resolved.cookieFilePath.isNullOrBlank()}",
            )
            resolved
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "YTDL_RESOLVER failed source=$sourceUrl " +
                        "type=${error.javaClass.simpleName} message=${error.message}",
                error,
            )
            cookieFile?.delete()
            throw error
        }
    }

    override suspend fun resolveV2(
        url: String,
        requestContext: YoutubeDLRequestContext,
    ): YoutubeDLResolvedAudio {
        val requestedUrl = url.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Media URL must not be blank")
        val sourceUrl = requestedUrl.canonicalizeYouTubeUrl()
        val isYouTube = sourceUrl.isYouTubeSourceUrl()
        val infoDirectory = if (sourceUrl.isTikTokSourceUrl()) {
            File(appContext.cacheDir, "${YOUTUBE_DL_FOLDER}resolve/${UUID.randomUUID()}")
        } else {
            null
        }
        var cookieFile: File? = null

        try {
            return withContext(Dispatchers.IO) {
                runInterruptible { MusicPageAudioResolver().resolve(sourceUrl) }?.let {
                    return@withContext it
                }
                ensureYoutubeDLInitialized(appContext)
                infoDirectory?.let {
                    check(it.mkdirs() || it.isDirectory) { "Unable to create metadata directory" }
                }

                suspend fun resolveAttempt(
                    cookiePath: String?,
                    attemptUrl: String = sourceUrl,
                ): YoutubeDLResolvedAudio {
                    val referer = requestContext.referer?.takeIf(String::isNotBlank)
                        ?: sourceUrl.takeIf { attemptUrl != sourceUrl }
                    val request = YoutubeDLRequest(attemptUrl)
                        .applyYoutubeDLCommonOptions()
                        .applyRequestContext(
                            sourceUrl = attemptUrl,
                            cookieFilePath = cookiePath,
                            userAgent = requestContext.userAgent,
                            referer = referer,
                        )
                        .applyYoutubeDLAudioResolveOptions(infoDirectory)
                        .applyVimeoAudioOptions(attemptUrl)

                    Log.i(
                        TAG,
                        "YTDL_AUDIO_RESOLVER start source=$attemptUrl hasCookies=${cookiePath != null} " +
                                "hasUserAgent=${request.hasOption("--user-agent")} " +
                                "version=${YoutubeDL.version(appContext) ?: "unknown"}",
                    )
                    val info = runInterruptible { YoutubeDL.getInfo(request) }
                    val downloadInfoJson = infoDirectory?.let {
                        val file = File(it, YOUTUBE_DL_RESOLVED_INFO_NAME)
                        check(file.isFile && file.length() > 0L) { "yt-dlp did not save download information" }
                        file.readText()
                    }
                    return info.toResolvedAudio(
                        sourceUrl = attemptUrl,
                        cookieFilePath = cookiePath,
                        userAgent = attemptUrl.youtubeDLUserAgent(requestContext.userAgent),
                        referer = referer?.takeIf { !isYouTube },
                    ).copy(
                        formatSelector = request.getOption("--format") ?: "bestaudio/best",
                        downloadInfoJson = downloadInfoJson,
                    )
                }

                val resolved = if (isYouTube) {
                    try {
                        resolveAttempt(cookiePath = null)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (publicError: Throwable) {
                        val rawCookies = requestContext.rawCookieHeader?.takeIf(String::isNotBlank)
                            ?: throw publicError
                        cookieFile = createNetscapeCookieFile(appContext, sourceUrl, rawCookies)
                        val cookiePath = cookieFile?.absolutePath ?: throw publicError

                        Log.w(TAG, "YTDL_AUDIO_RESOLVER public attempt failed; retrying authenticated", publicError)
                        try {
                            resolveAttempt(cookiePath)
                        } catch (error: Throwable) {
                            error.addSuppressed(publicError)
                            throw error
                        }
                    }
                } else {
                    cookieFile = requestContext.rawCookieHeader
                        ?.takeIf(String::isNotBlank)
                        ?.let { createNetscapeCookieFile(appContext, sourceUrl, it) }
                    try {
                        resolveAttempt(cookiePath = cookieFile?.absolutePath)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (pageError: Throwable) {
                        val playerUrl = pageError.vimeoPlayerFallbackUrl(sourceUrl) ?: throw pageError
                        currentCoroutineContext().ensureActive()
                        Log.i(TAG, "YTDL_AUDIO_RESOLVER retry using Vimeo player")
                        try {
                            resolveAttempt(cookiePath = cookieFile?.absolutePath, attemptUrl = playerUrl)
                        } catch (error: Throwable) {
                            if (error !is CancellationException) error.addSuppressed(pageError)
                            throw error
                        }
                    }
                }

                Log.i(TAG, "YTDL_AUDIO_RESOLVER complete id=${resolved.id} selector=${resolved.formatSelector}")
                resolved
            }
        } catch (error: Throwable) {
            cookieFile?.delete()
            Log.e(TAG, "YTDL_AUDIO_RESOLVER failed source=$sourceUrl", error)
            throw error
        } finally {
            infoDirectory?.deleteRecursively()
        }
    }

    private companion object {
        const val TAG = "MTHAI"
    }
}
