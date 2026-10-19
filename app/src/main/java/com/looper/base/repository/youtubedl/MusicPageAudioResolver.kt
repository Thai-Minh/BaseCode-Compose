package com.looper.base.repository.youtubedl

import com.looper.base.repository.youtubedl.data.YoutubeDLResolvedAudio
import org.json.JSONArray
import org.json.JSONObject
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToLong

internal class MusicPageAudioResolver(
    private val get: (String) -> String = MusicPageClient()::get,
    private val retryDelay: () -> Unit = { Thread.sleep(250) },
) {
    fun resolve(sourceUrl: String): YoutubeDLResolvedAudio? {
        val uri = runCatching { URI(sourceUrl) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https") || uri.userInfo != null || uri.port != -1) return null
        val host = uri.host?.lowercase() ?: return null
        return when (host) {
            "open.spotify.com", "www.open.spotify.com" -> {
                if (!Regex("^/(?:intl-[A-Za-z-]+/)?track/[A-Za-z0-9]{22}/?$")
                        .matches(uri.path.orEmpty())) return null
                error(
                    "Spotify tracks are DRM-protected and cannot be downloaded as MP3 by this app. " +
                            "Open the track in Spotify or use a supported, non-DRM audio URL."
                )
            }
            "zingmp3.vn", "www.zingmp3.vn", "m.zingmp3.vn", "mp3.zing.vn" -> {
                val match = Regex("^/bai-hat/[^/]+/([A-Za-z0-9]+)\\.html$")
                    .matchEntire(uri.path.orEmpty()) ?: return null
                resolveZing(sourceUrl, uri.rawPath, match.groupValues[1])
            }
            "nhaccuatui.com", "www.nhaccuatui.com" -> {
                val match = Regex("^/song/([A-Za-z0-9]+)/?$").matchEntire(uri.path.orEmpty()) ?: return null
                val id = match.groupValues[1]
                parseNhacCuaTuiAudio(sourceUrl, id, get("https://www.nhaccuatui.com/song/$id"))
            }
            else -> null
        }
    }

    private fun resolveZing(sourceUrl: String, path: String, id: String): YoutubeDLResolvedAudio {
        val html = get("https://zingmp3.vn$path")
        val version = Regex("/zmp3-desktop/v([0-9]+(?:\\.[0-9]+)+)/static/js/")
            .find(html)?.groupValues?.get(1)
            ?: error("Zing MP3: unable to read the website API version")

        fun callApi(endpoint: String, songId: String): JSONObject {
            val url = zingMp3ApiUrl(endpoint, songId, version, (System.currentTimeMillis() / 1000.0).roundToLong())
            repeat(3) { attempt ->
                val response = JSONObject(get(url))
                val code = response.optInt("err", Int.MIN_VALUE)
                if (code == 0) return response.optJSONObject("data")
                    ?: error("Zing MP3: missing song information")
                // The public website retries after the server sets its anonymous request cookie.
                if (code == -201 && attempt < 2) {
                    retryDelay()
                } else {
                    val message = response.optString("msg").takeIf(String::isNotBlank) ?: "request failed"
                    error("Zing MP3: $message (code $code)")
                }
            }
            error("Zing MP3: unable to initialize an anonymous session")
        }

        val song = callApi("/api/v2/page/get/song", id)
        check(!song.optBoolean("isPrivate") && !song.optBoolean("preRelease")) {
            "Zing MP3: this song is private or not yet released"
        }
        val songId = song.optString("encodeId").takeIf(String::isNotBlank) ?: id
        val streams = callApi("/api/v2/song/get/streaming", songId)
        val formats = streams.keys().asSequence().mapNotNull { quality ->
            val bitrate = quality.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val url = publicAudioUrl(streams.optString(quality)) ?: return@mapNotNull null
            MusicAudioFormat(bitrate, url)
        }.toList()
        check(formats.isNotEmpty()) {
            if (streams.keys().asSequence().any { streams.optString(it) == "VIP" }) {
                "Zing MP3: no public audio stream; the available qualities require a VIP account"
            } else {
                "Zing MP3: no public audio stream is available for this song"
            }
        }
        return musicResolvedAudio(
            sourceUrl = sourceUrl,
            id = songId,
            title = song.optString("title").ifBlank { "Audio" },
            owner = song.optString("artistsNames"),
            thumbnail = song.optString("thumbnail").ifBlank { song.optString("thumbnailM") },
            durationSeconds = song.optLong("duration"),
            extractor = "ZingMp3",
            formats = formats,
        )
    }
}

internal fun zingMp3ApiUrl(endpoint: String, id: String, version: String, timestampSeconds: Long): String {
    val parameters = sortedMapOf("ctime" to timestampSeconds.toString(), "id" to id, "version" to version)
    fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    val signedValues = parameters.entries.joinToString("") { "${encode(it.key)}=${encode(it.value)}" }
    fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }
    val digest = MessageDigest.getInstance("SHA-256").digest(signedValues.toByteArray(Charsets.UTF_8)).hex()
    val mac = Mac.getInstance("HmacSHA512").apply {
        init(SecretKeySpec("acOrvUS15XRW2o9JksiK1KgQ6Vbds8ZW".toByteArray(Charsets.UTF_8), "HmacSHA512"))
    }
    parameters["apiKey"] = "X5BM3w8N7MKozC0B85o4KMlzLZKhV00y"
    parameters["sig"] = mac.doFinal("$endpoint$digest".toByteArray(Charsets.UTF_8)).hex()
    return "https://zingmp3.vn$endpoint?" + parameters.entries.joinToString("&") {
        "${encode(it.key)}=${encode(it.value)}"
    }
}

internal fun parseNhacCuaTuiAudio(sourceUrl: String, id: String, html: String): YoutubeDLResolvedAudio {
    val state = Regex(
        """<script\b[^>]*\bid\s*=\s*["']__NUXT_DATA__["'][^>]*>(.*?)</script\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).find(html)?.groupValues?.get(1) ?: error("NhacCuaTui: unable to read song data from this page")
    val pool = NuxtData(JSONArray(state))
    val root = pool.value(0) as? JSONObject ?: error("NhacCuaTui: invalid page data")
    val data = pool.field(root, "data") as? JSONObject ?: error("NhacCuaTui: missing page data")
    // Resolve only the requested song, never MP3 URLs from recommendations or user state.
    val song = pool.field(data, "dataDetail:$id") as? JSONObject
        ?: error("NhacCuaTui: song information is unavailable")
    check(pool.field(song, "__detailUnavailable") != true && pool.field(song, "key") == id) {
        "NhacCuaTui: this song is unavailable"
    }
    val statusPlay = (pool.field(song, "statusPlay") as? Number)?.toInt()
    val statusView = (pool.field(song, "statusView") as? Number)?.toInt()
    check(statusPlay == 1 && statusView !in setOf(2, 3, 5, 6, 7, 8)) {
        when {
            statusPlay == 2 || statusView == 5 -> "NhacCuaTui: this song requires a VIP account"
            statusView == 6 || statusView == 7 -> "NhacCuaTui: this song requires sign-in"
            else -> "NhacCuaTui: this song is not publicly available"
        }
    }
    val flags = pool.field(song, "flags") as? JSONArray
    check(flags == null || (0 until flags.length()).none { pool.value(flags.opt(it)) == "selfView" }) {
        "NhacCuaTui: this song is only available to its owner"
    }
    val streams = pool.field(song, "streamURL") as? JSONArray ?: JSONArray()
    val formats = (0 until streams.length()).mapNotNull { index ->
        val stream = pool.value(streams.opt(index)) as? JSONObject ?: return@mapNotNull null
        if (pool.field(stream, "onlyVIP") != false || (pool.field(stream, "status") as? Number)?.toInt() != 1) {
            return@mapNotNull null
        }
        val bitrate = pool.field(stream, "type")?.toString()?.toIntOrNull()?.takeIf { it > 0 }
            ?: return@mapNotNull null
        val url = publicAudioUrl(pool.field(stream, "stream") as? String) ?: return@mapNotNull null
        MusicAudioFormat(bitrate, url)
    }
    check(formats.isNotEmpty()) { "NhacCuaTui: no public audio stream is available for this song" }
    return musicResolvedAudio(
        sourceUrl = sourceUrl,
        id = id,
        title = (pool.field(song, "name") as? String).orEmpty().ifBlank { "Audio" },
        owner = pool.field(song, "artistName") as? String,
        thumbnail = pool.field(song, "image") as? String,
        durationSeconds = (pool.field(song, "duration") as? Number)?.toLong() ?: 0L,
        extractor = "NhacCuaTui",
        formats = formats,
    )
}

private class NuxtData(private val pool: JSONArray) {
    fun field(obj: JSONObject, key: String): Any? = value(obj.opt(key))

    fun value(reference: Any?): Any? {
        var index = (reference as? Number)?.toInt() ?: return null
        repeat(8) {
            if (index < 0 || index >= pool.length()) return null
            val value = pool.opt(index)
            if (value is JSONArray && value.opt(0) in setOf("ShallowReactive", "Reactive", "ShallowRef", "Ref")) {
                index = (value.opt(1) as? Number)?.toInt() ?: return null
            } else {
                return value.takeUnless { it == JSONObject.NULL }
            }
        }
        error("NhacCuaTui: invalid reactive page data")
    }
}

private data class MusicAudioFormat(val bitrate: Int, val url: String)

private fun publicAudioUrl(value: String?): String? {
    val url = value?.let { if (it.startsWith("//")) "https:$it" else it } ?: return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    return url.takeIf {
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }
}

private fun musicResolvedAudio(
    sourceUrl: String,
    id: String,
    title: String,
    owner: String?,
    thumbnail: String?,
    durationSeconds: Long,
    extractor: String,
    formats: List<MusicAudioFormat>,
): YoutubeDLResolvedAudio {
    val headers = JSONObject().put("Referer", sourceUrl).put("User-Agent", MUSIC_PAGE_USER_AGENT)
    val info = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("uploader", owner?.takeIf(String::isNotBlank))
        .put("thumbnail", thumbnail?.takeIf(String::isNotBlank))
        .put("duration", durationSeconds.coerceAtLeast(0))
        .put("webpage_url", sourceUrl)
        .put("extractor", extractor)
        .put("extractor_key", "Generic")
        .put("http_headers", headers)
        .put("formats", JSONArray(formats.distinctBy { it.bitrate }.sortedBy { it.bitrate }.map {
            JSONObject().put("format_id", it.bitrate.toString()).put("url", it.url)
                .put("ext", "mp3").put("vcodec", "none").put("acodec", "mp3").put("abr", it.bitrate)
                .put("http_headers", headers)
        }))
    return YoutubeDLResolvedAudio(
        id = id,
        sourceUrl = sourceUrl,
        title = title,
        ownerName = owner?.takeIf(String::isNotBlank),
        thumbnailUrl = thumbnail?.takeIf(String::isNotBlank),
        durationMs = durationSeconds.coerceAtLeast(0) * 1_000L,
        extractor = extractor,
        userAgent = MUSIC_PAGE_USER_AGENT,
        referer = sourceUrl,
        downloadInfoJson = info.toString(),
    )
}

private class MusicPageClient {
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)

    fun get(url: String): String {
        val uri = URI(url)
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", MUSIC_PAGE_USER_AGENT)
            connection.setRequestProperty("Accept-Encoding", "gzip")
            connection.setRequestProperty("Referer", "${uri.scheme}://${uri.host}/")
            cookies.get(uri, emptyMap()).forEach { (name, values) ->
                connection.setRequestProperty(name, values.joinToString("; "))
            }
            val status = connection.responseCode
            cookies.put(uri, connection.headerFields.filterKeys { it != null })
            check(status in 200..299) { "${uri.host}: music request failed (HTTP $status)" }
            val input = if (connection.contentEncoding.equals("gzip", ignoreCase = true)) {
                GZIPInputStream(connection.inputStream)
            } else {
                connection.inputStream
            }
            return input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

private const val MUSIC_PAGE_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
