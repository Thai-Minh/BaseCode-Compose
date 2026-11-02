package com.looper.base.equalizer.autoeq

import android.content.Context
import android.net.Uri
import com.looper.base.equalizer.model.EqEngine
import com.looper.base.equalizer.model.TenBandEqConstants
import com.looper.base.equalizer.model.TenBandEqSettings
import com.looper.base.utils.SimpleHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection

data class AutoEqEntry(
    val name: String,
    val path: String,
    val source: String,
)

class AutoEqRepository(
    private val context: Context,
) {

    private companion object {
        const val RESULTS_URL =
            "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results"
        const val INDEX_FILE = "INDEX.md"
        const val ETAG_FILE = "INDEX.etag"
        const val DIRECTORY = "autoeq"

        val ENTRY_REGEX = Regex("""^- \[(.+)]\(\./(.+)\) by (.+)$""")
        val PREAMP_REGEX = Regex("""Preamp:\s*(-?[\d.]+)\s*dB""")
        val FILTER_REGEX = Regex("""Fc\s+(\d+)\s*Hz\s+Gain\s+(-?[\d.]+)\s*dB""")
    }

    private val mutex = Mutex()
    private var cachedEntries: List<AutoEqEntry>? = null

    suspend fun entries(forceRefresh: Boolean = false): List<AutoEqEntry> = mutex.withLock {
        if (!forceRefresh) {
            cachedEntries?.let {
                return@withLock it
            }
        }
        val directory = File(context.filesDir, DIRECTORY).apply {
            mkdirs()
        }
        val indexFile = File(directory, INDEX_FILE)
        val etagFile = File(directory, ETAG_FILE)
        val etag = etagFile.takeIf {
            it.exists()
        }?.readText()?.takeIf {
            indexFile.exists()
        }
        val response = runCatching {
            SimpleHttp.get(
                url = "$RESULTS_URL/$INDEX_FILE",
                headers = etag?.let {
                    mapOf("If-None-Match" to it)
                }.orEmpty(),
            )
        }.getOrNull()
        withContext(Dispatchers.IO) {
            if (response?.code == HttpURLConnection.HTTP_OK && response.body != null) {
                indexFile.writeText(text = response.body)
                response.etag?.let {
                    etagFile.writeText(text = it)
                }
            }
        }
        if (!indexFile.exists()) {
            throw IOException("AutoEq index unavailable")
        }
        val parsed = withContext(Dispatchers.Default) {
            indexFile.readLines().mapNotNull { line ->
                ENTRY_REGEX.matchEntire(input = line.trim())?.destructured?.let { (name, path, source) ->
                    AutoEqEntry(name = name, path = path, source = source)
                }
            }
        }
        cachedEntries = parsed
        parsed
    }

    suspend fun search(query: String, limit: Int = 50): List<AutoEqEntry> {
        val tokens = query.trim().lowercase().split(Regex(pattern = "\\s+")).filter {
            it.isNotEmpty()
        }
        if (tokens.isEmpty()) {
            return emptyList()
        }
        return entries().asSequence()
            .filter { entry ->
                val name = entry.name.lowercase()
                tokens.all {
                    it in name
                }
            }
            .take(n = limit)
            .toList()
    }

    suspend fun profile(entry: AutoEqEntry): TenBandEqSettings {
        val url = "$RESULTS_URL/${entry.path}/${Uri.encode("${entry.name} FixedBandEQ.txt")}"
        val response = SimpleHttp.get(url = url)
        val body = response.body?.takeIf {
            response.code == HttpURLConnection.HTTP_OK
        } ?: throw IOException("AutoEq profile unavailable (${response.code})")
        return parse(text = body, profileName = entry.name)
    }

    private fun parse(text: String, profileName: String): TenBandEqSettings {
        val preamp =
            PREAMP_REGEX.find(input = text)?.groupValues?.get(index = 1)?.toFloatOrNull() ?: 0f
        val gains = FILTER_REGEX.findAll(input = text)
            .map {
                it.groupValues[1].toInt() to it.groupValues[2].toFloat()
            }.toMap()
        if (gains.isEmpty()) {
            throw IOException("AutoEq profile has no filters")
        }
        return TenBandEqSettings(
            engine = EqEngine.BUILT_IN,
            gainsDb = TenBandEqConstants.FREQUENCIES_HZ.map {
                gains[it] ?: 0f
            },
            preampDb = preamp,
            profileName = profileName,
        ).normalized()
    }
}
