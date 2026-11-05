/**
*Created by: ThaiND
*/

package com.looper.base.iap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException

interface SubscriptionConfigSource {
    /** Await the existing shared fetch, then read activated values even if activation returned false. */
    suspend fun readActivatedPayload(): String

    suspend fun readLastKnownGood(): String?

    suspend fun saveLastKnownGood(payload: String)
}

enum class CatalogOrigin { Activated, LastKnownGood, Default }

data class SessionCatalog(
    val selection: SubscriptionSelection,
    val origin: CatalogOrigin,
)

/** Freezes one validated selling catalog. Does not invent a default from unverified Play IDs. */
class SessionCatalogLoader(
    private val source: SubscriptionConfigSource,
    private val defaultPayload: String,
    private val onFallback: (String) -> Unit,
    private val parse: (String) -> SubscriptionSelection = { json ->
        SubscriptionCatalogParser.parse(json = json)
    },
    private val remoteTimeoutMillis: Long = 5_000L,
) {
    private val mutex = Mutex()
    private var frozen: SessionCatalog? = null

    suspend fun load(): SessionCatalog = mutex.withLock {
        val current = frozen

        if (current != null) {
            return@withLock current
        }

        val activated = readRemote()
        val remoteSelection = activated?.let { payload -> validated(payload = payload) }

        if (remoteSelection != null) {
            try {
                source.saveLastKnownGood(payload = activated)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                onFallback("Could not persist validated IAP catalog")
            }

            return@withLock freeze(
                selection = remoteSelection,
                origin = CatalogOrigin.Activated,
            )
        }

        onFallback("Activated IAP catalog unavailable or invalid; checking last-known-good")
        val cached = try {
            source.readLastKnownGood()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            onFallback("Could not read last-known-good IAP catalog")
            null
        }
        val cachedSelection = cached?.let { payload -> validated(payload = payload) }

        if (cachedSelection != null) {
            return@withLock freeze(
                selection = cachedSelection,
                origin = CatalogOrigin.LastKnownGood,
            )
        }

        onFallback("No validated cached IAP catalog; using explicitly supplied default")

        freeze(
            selection = parse(defaultPayload),
            origin = CatalogOrigin.Default,
        )
    }

    private suspend fun readRemote(): String? {
        return try {
            withTimeoutOrNull(timeMillis = remoteTimeoutMillis) {
                source.readActivatedPayload()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            onFallback("Shared remote IAP fetch failed")
            null
        }
    }

    private fun validated(payload: String): SubscriptionSelection? {
        return try {
            parse(payload)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IllegalArgumentException) {
            null
        } catch (exception: JSONException) {
            null
        }
    }

    private fun freeze(selection: SubscriptionSelection, origin: CatalogOrigin): SessionCatalog {
        val snapshot = SessionCatalog(
            selection = selection,
            origin = origin,
        )
        frozen = snapshot

        return snapshot
    }
}
