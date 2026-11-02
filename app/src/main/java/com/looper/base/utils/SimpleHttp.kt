package com.looper.base.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class HttpTextResponse(
    val code: Int,
    val body: String?,
    val etag: String?,
)

object SimpleHttp {

    private const val TIMEOUT_MS = 15_000
    private const val PROBE_TIMEOUT_MS = 5_000

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpTextResponse = withContext(Dispatchers.IO) {
        val connection = open(url = url, headers = headers)
        try {
            read(connection = connection)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun isReachable(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = open(url = url, headers = emptyMap()).apply {
                requestMethod = "HEAD"
                connectTimeout = PROBE_TIMEOUT_MS
                readTimeout = PROBE_TIMEOUT_MS
            }
            try {
                connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(defaultValue = false)
    }

    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): HttpTextResponse = withContext(Dispatchers.IO) {
        val connection = open(url = url, headers = headers)
        try {
            val body = form.entries.joinToString(separator = "&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=utf-8"
            )
            connection.outputStream.use {
                it.write(body.toByteArray(charset = Charsets.UTF_8))
            }
            read(connection = connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, headers: Map<String, String>): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            headers.forEach { (name, value) ->
                setRequestProperty(name, value)
            }
        }

    private fun read(connection: HttpURLConnection): HttpTextResponse {
        val code = connection.responseCode
        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val body = stream?.bufferedReader(charset = Charsets.UTF_8)?.use {
            it.readText()
        }
        return HttpTextResponse(
            code = code,
            body = body,
            etag = connection.getHeaderField("ETag"),
        )
    }
}
