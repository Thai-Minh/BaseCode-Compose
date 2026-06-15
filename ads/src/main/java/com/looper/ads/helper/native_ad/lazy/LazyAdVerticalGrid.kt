package com.looper.ads.helper.native_ad.lazy

import android.app.Application
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.looper.ads.conditional
import com.looper.ads.helper.banner.list.Adaptive
import com.looper.ads.helper.banner.list.Fixed
import com.looper.ads.helper.banner.list.GridType
import com.looper.ads.helper.banner.list.NEXT_SCROLL_INDEX
import com.looper.ads.helper.banner.list.getColumnCount
import com.looper.ads.helper.banner.list.toGridCells
import com.looper.ads.helper.native_ad.NativeAdState
import com.looper.ads.helper.native_ad.NativeAdViewModel
import com.looper.ads.helper.native_ad.NativeAdViewModelFactory
import com.looper.ads.helper.native_ad.ui.NativeAdLifecycle
import com.looper.ads.helper.native_ad.ui.NativeSpace
import com.looper.ads.helper.native_ad.ui.buildDefault
import com.looper.ads.isSupportAds
import com.zenith.adapter.ui.UnifiedNativeAdViewBinder
import kotlin.math.ceil

data class NativeAdGridType(val items: List<Any> = emptyList(), val adType: NativeAdColumnType)

object AdItem

@Composable
fun LazyAdVerticalGridCheckScroll(
    modifier: Modifier = Modifier,
    gridType: GridType,
    nativeType: NativeAdGridType,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    onKey: (Any) -> Any = {},
    onSingleItemBuilder: LazyGridScope.() -> Unit = {},
    onItemBuilder: @Composable (item: Any) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var size by remember {
        mutableStateOf(IntSize.Zero)
    }

    val columnCount = remember(size, gridType, density, contentPadding, horizontalArrangement) {
        if (gridType is Adaptive) {
            getColumnCount(
                size = size,
                gridType = gridType,
                density = density,
                contentPadding = contentPadding,
                horizontalArrangement = horizontalArrangement
            )
        } else {
            (gridType as Fixed).count
        }
    }

    val spanRowCount = remember(key1 = size) {
        size.height / context.dp2px(nativeType.adType.config.itemHeightDp)
    }

    val originalRowCount = remember(key1 = nativeType.items.size, key2 = columnCount) {
        val count = if (columnCount == 0) {
            1
        } else {
            columnCount
        }

        ceil(nativeType.items.size.toFloat() / count).toInt()
    }

    val canScroll = remember(key1 = originalRowCount, key2 = spanRowCount) {
        originalRowCount >= spanRowCount
    }

    LazyAdVerticalGrid(
        modifier = modifier.onSizeChanged {
            if (size == IntSize.Zero)
                size = it
        },
        canScroll = canScroll,
        type = gridType,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        onKey = onKey,
        onSingleItemBuilder = onSingleItemBuilder,
        onItemBuilder = onItemBuilder,
        nativeType = nativeType
    )
}

@Composable
fun LazyAdVerticalGrid(
    type: GridType,
    nativeType: NativeAdGridType,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    canScroll: Boolean = false,
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    onKey: (Any) -> Any = {},
    onSingleItemBuilder: LazyGridScope.() -> Unit = {},
    onItemBuilder: @Composable (item: Any) -> Unit = {}
) {

    if (!isSupportAds || !canScroll) {
        return LazyVerticalGrid(
            columns = type.toGridCells(),
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            content = {
                onSingleItemBuilder()

                items(items = nativeType.items, key = { onKey(it) }) {
                    onItemBuilder(it)
                }
            }
        )
    }

    val app = LocalContext.current.applicationContext as Application

    val viewModel: NativeAdViewModel = viewModel(
        factory = NativeAdViewModelFactory(app, nativeType.adType.config.adType)
    )

    NativeAdLifecycle(viewModel)

    val nativeAd by viewModel.nativeAd.collectAsStateWithLifecycle(initialValue = NativeAdState.Loading)

    var adView by remember {
        mutableStateOf<View?>(null)
    }

    LaunchedEffect(nativeAd) {
        adView = when (nativeAd) {
            is NativeAdState.Success -> {
                val view = LayoutInflater.from(app).inflate(
                    nativeType.adType.config.nativeLayoutId,
                    FrameLayout(app),
                    false
                )
                val baseView = UnifiedNativeAdViewBinder.Builder(view).buildDefault()

                (nativeAd as NativeAdState.Success).nativeAd.render(baseView)
            }

            is NativeAdState.Loading -> LayoutInflater.from(app).inflate(
                nativeType.adType.config.shimmerLayoutId,
                FrameLayout(app),
                false
            )

            else -> null
        }
    }

    var size by remember {
        mutableStateOf(IntSize.Zero)
    }

    val columnCount = remember(size) {
        if (type is Adaptive) {
            app.getColumnCount(
                size = size,
                minDp = type.minSize,
                contentPadding = contentPadding
            )
        } else {
            (type as Fixed).count
        }
    }

    val items by remember(columnCount, size, nativeType) {
        val spanRowCount = size.height / app.dp2px(nativeType.adType.config.itemHeightDp)
        mutableStateOf(
            value = addNativeItem(
                items = nativeType.items,
                spanRowCount = spanRowCount,
                columnCount = columnCount,
                startAdIndex = if (nativeType.adType.config.startAdIndex == NEXT_SCROLL_INDEX) spanRowCount else nativeType.adType.config.startAdIndex
            )
        )
    }

    LazyVerticalGrid(
        columns = type.toGridCells(),
        modifier = modifier.onSizeChanged { size = it },
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
    ) {
        onSingleItemBuilder()

        items.forEachIndexed { index, item ->
            if (item is AdItem) {
                if (adView != null) {
                    item(key = "native${index}", span = { GridItemSpan(columnCount) }) {
                        Box(
                            modifier = Modifier
                                .conditional(
                                    condition = nativeType.adType.config.isAnimateItemPlacement,
                                    ifTrue = {
                                        animateItem()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            NativeItem(
                                modifier = nativeType.adType.config.modifier,
                                view = adView!!,
                                space = NativeSpace.space()
                            )
                        }
                    }
                }
            } else {
                item(key = onKey(item)) {
                    onItemBuilder(item)
                }
            }
        }
    }
}


fun Context.getColumnCount(size: IntSize, minDp: Int, contentPadding: PaddingValues): Int {
    val columnWidthPixel = dp2px(minDp)
    val horizontalPadding = contentPadding.calculateStartPadding(LayoutDirection.Ltr) +
            contentPadding.calculateEndPadding(LayoutDirection.Ltr)

    val screenWidthPixel = size.width - dp2px(horizontalPadding.value.toInt())

    return (screenWidthPixel / columnWidthPixel + 0.5).toInt()
}

private fun Context.dp2px(dp: Int): Int {
    return (resources.displayMetrics.density * dp + 0.5f).toInt()
}

private fun addNativeItem(
    items: List<Any>,
    startAdIndex: Int,
    spanRowCount: Int = 6,
    columnCount: Int = 6
): List<Any> {
    val realIndex = startAdIndex - 1
    val list = mutableListOf<Any>()

    if (items.isNotEmpty() && columnCount > 0) {
        val chucks = items.chunked(columnCount)

        if (items.size < columnCount) {
            list.addAll(chucks[0])
            list.add(AdItem)
        } else {
            chucks.forEachIndexed { index, chunkItems ->
                list.addAll(chunkItems)

                if (index == realIndex || (index - realIndex) % (spanRowCount + 1) == 0) {
                    list.add(AdItem)
                }
            }
        }
    }

    return list
}