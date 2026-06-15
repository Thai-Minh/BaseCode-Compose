package com.looper.ads.helper.lazy_list

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import kotlin.math.min

private const val ITEM_KEY = "item_key_"

internal class LazyListScopeImpl(
    private val startIndex: Int = 1,
    private val adSpace: Int = 6
) : LazyListScope {

    private val _items = mutableListOf<LazyListIntervalContent>()
    val items: List<LazyListIntervalContent> = _items

    private var totalSize = 0

    private var nativeSize = 0

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit
    ) {
        if (totalSize + count <= startIndex) {
            _items.add(
                LazyListIntervalContent.Multi(
                    count = count,
                    key = key,
                    contentType = contentType,
                    content = itemContent
                )
            )

            addNativeItem()
        } else {
            val offset = if (totalSize > startIndex)
                (totalSize - startIndex) % adSpace
            else
                startIndex - totalSize

            val startIndex = if (offset > 0) {
                val itemCount = if (totalSize > startIndex)
                    adSpace - offset
                else
                    offset

                _items.add(
                    LazyListIntervalContent.Multi(
                        count = min(itemCount, count),
                        key = key,
                        contentType = contentType,
                        content = itemContent
                    )
                )

                itemCount
            } else 0

            for (i in startIndex until count step adSpace) {
                addNativeItem()

                _items.add(
                    LazyListIntervalContent.Multi(
                        count = min(adSpace, count - i),
                        key = if (key != null) { index ->
                            key(i + index)
                        } else null,
                        contentType = { index ->
                            contentType(i + index)
                        },
                        content = { index ->
                            itemContent(i + index)
                        }
                    )
                )
            }
        }

        totalSize += count
    }

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        if (totalSize >= startIndex && (totalSize - startIndex) % adSpace == 0) {
            addNativeItem()
        }

        _items.add(
            LazyListIntervalContent.Single(
                key = key,
                contentType = contentType,
                content = content
            )
        )

        totalSize++
    }

    override fun stickyHeader(
        key: Any?,
        contentType: Any?,
        content: @Composable (LazyItemScope.(Int) -> Unit)
    ) {
        _items.add(
            LazyListIntervalContent.Header(
                key = key,
                contentType = contentType,
                content = content
            )
        )
    }

    private fun addNativeItem() {
        nativeSize++
        _items.add(LazyListIntervalContent.Native("$ITEM_KEY$nativeSize"))
    }
}

internal sealed class LazyListIntervalContent {
    class Single(
        val key: Any? = null,
        val contentType: Any? = null,
        val content: @Composable LazyItemScope.() -> Unit
    ) : LazyListIntervalContent()

    class Multi(
        val count: Int,
        val key: ((index: Int) -> Any)?,
        val contentType: (index: Int) -> Any?,
        val content: @Composable LazyItemScope.(index: Int) -> Unit
    ) : LazyListIntervalContent()

    class Header(
        val key: Any? = null,
        val contentType: Any? = null,
        val content: @Composable LazyItemScope.(Int) -> Unit
    ) : LazyListIntervalContent()

    class Native(
        val key: String
    ) : LazyListIntervalContent()
}