package com.looper.base.utils.coil

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer

internal fun embeddedAudioArtworkRequest(
    context: Context,
    imagePath: String,
    lastModified: Long = 0L,
): ImageRequest {
    val cacheKey = "embedded-audio:$imagePath:$lastModified"
    return ImageRequest.Builder(context)
        .data(EmbeddedAudioArtwork(imagePath))
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .networkCachePolicy(CachePolicy.DISABLED)
        .fetcherFactory(EmbeddedAudioArtworkFetcher.Factory())
        .build()
}

private data class EmbeddedAudioArtwork(val path: String)

private class EmbeddedAudioArtworkFetcher(
    private val data: EmbeddedAudioArtwork,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(data.path)
        val retriever = MediaMetadataRetriever()
        try {
            when (uri.scheme) {
                "content" -> retriever.setDataSource(options.context, uri)
                "file" -> retriever.setDataSource(
                    uri.path ?: throw IOException("Invalid audio file URI")
                )
                null -> retriever.setDataSource(data.path)
                else -> throw IOException("Only local audio artwork is supported")
            }
            // Returning null would let Coil try a video or network fetcher instead.
            val artwork = retriever.embeddedPicture?.takeIf { it.isNotEmpty() }
                ?: throw IOException("No embedded audio artwork")
            SourceFetchResult(
                source = ImageSource(
                    source = Buffer().write(artwork),
                    fileSystem = options.fileSystem,
                ),
                mimeType = null,
                dataSource = DataSource.DISK,
            )
        } finally {
            retriever.release()
        }
    }

    class Factory : Fetcher.Factory<EmbeddedAudioArtwork> {
        override fun create(
            data: EmbeddedAudioArtwork,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = EmbeddedAudioArtworkFetcher(data, options)
    }
}
