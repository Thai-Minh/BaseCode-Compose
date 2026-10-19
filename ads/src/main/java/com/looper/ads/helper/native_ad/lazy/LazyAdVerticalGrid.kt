package com.looper.ads.helper.native_ad.lazy

import android.app.Application
import android.content.Context
import android.view.LayoutInflater
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.looper.ads.R
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
import com.looper.ads.isSupportAdState
import com.zenith.adapter.ui.UnifiedNativeAdViewBinder
import kotlin.math.ceil

enum class NativeAdGridSpan {
    FullRow,
    SingleCell,
}

data class NativeAdGridType(
    val items: List<Any> = emptyList(),
    val adType: NativeAdColumnType,
    val adSpan: NativeAdGridSpan = NativeAdGridSpan.FullRow,
)

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

    val supportAds by isSupportAdState.collectAsStateWithLifecycle()
    if (!supportAds || !canScroll) {
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
        key = nativeType.adType.config.adType.name,
        factory = NativeAdViewModelFactory(
            app = app,
            type = nativeType.adType.config.adType,
            refreshAd = nativeType.adType.config.adType.removable
        )
    )

    val nativeAd by viewModel.nativeAd.collectAsStateWithLifecycle(initialValue = NativeAdState.Loading)

    LaunchedEffect(viewModel) {
        viewModel.forceRefreshAd()
    }

    NativeAdLifecycle(viewModel)

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
        val visibleRowCount =
            (size.height / app.dp2px(nativeType.adType.config.itemHeightDp)).coerceAtLeast(1)
        val nativeSpace = visibleRowCount + 2
        val adSpace = when {
            nativeType.adType.config.adInterval < 0 -> nativeType.adType.config.adInterval
            nativeType.adType.config.adInterval > nativeSpace -> nativeType.adType.config.adInterval
            else -> nativeSpace
        }

        mutableStateOf(
            value = addNativeItem(
                items = nativeType.items,
                columnCount = columnCount,
                startAdIndex = if (nativeType.adType.config.startAdIndex == NEXT_SCROLL_INDEX) {
                    nativeSpace
                } else {
                    nativeType.adType.config.startAdIndex
                },
                adInterval = adSpace,
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
                if (nativeAd !is NativeAdState.Error) {
                    item(
                        key = "native$index",
                        span = {
                            GridItemSpan(
                                when (nativeType.adSpan) {
                                    NativeAdGridSpan.FullRow -> columnCount
                                    NativeAdGridSpan.SingleCell -> 1
                                }
                            )
                        }
                    ) {
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
                            when (val nativeAdState = nativeAd) {
                                is NativeAdState.Success -> {
                                    val view = remember(
                                        index,
                                        nativeAdState,
                                        nativeType.adType.config.nativeLayoutId
                                    ) {
                                        val nativeView = LayoutInflater.from(app).inflate(
                                            nativeType.adType.config.nativeLayoutId,
                                            FrameLayout(app),
                                            false
                                        )
                                        val baseView = UnifiedNativeAdViewBinder.Builder(nativeView)
                                            .buildDefault(buttonCallToAction = R.id.native_call_to_action)

                                        nativeAdState.nativeAd.render(baseView)
                                    }

                                    NativeItem(
                                        modifier = nativeType.adType.config.modifier,
                                        view = view,
                                        space = NativeSpace.space()
                                    )
                                }

                                NativeAdState.Loading,
                                null -> {
                                    AndroidView(
                                        factory = { context ->
                                            LayoutInflater.from(context).inflate(
                                                nativeType.adType.config.shimmerLayoutId,
                                                FrameLayout(context),
                                                false
                                            )
                                        },
                                        modifier = nativeType.adType.config.modifier
                                    )
                                }

                                NativeAdState.Error -> Unit
                            }
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
    adInterval: Int,
    columnCount: Int = 6,
): List<Any> {
    if (items.isEmpty() || columnCount <= 0) return items

    val rows = items.chunked(columnCount)
    val normalizedStartIndex = startAdIndex.coerceAtLeast(0)
    val list = mutableListOf<Any>()

    if (normalizedStartIndex == 0) {
        list.add(AdItem)
    }

    rows.forEachIndexed { rowIndex, rowItems ->
        list.addAll(rowItems)

        val completedRowCount = rowIndex + 1
        val isFirstAdPosition = normalizedStartIndex > 0 &&
                completedRowCount == normalizedStartIndex
        val isRepeatedAdPosition = adInterval > 0 &&
                completedRowCount > normalizedStartIndex &&
                (completedRowCount - normalizedStartIndex) % adInterval == 0

        if (isFirstAdPosition || isRepeatedAdPosition) {
            list.add(AdItem)
        }
    }

    return list
}