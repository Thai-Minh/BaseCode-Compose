package com.looper.ads.helper.banner.list

import android.content.Context
import android.util.DisplayMetrics
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.looper.ads.helper.banner.data.BannerState
import com.looper.ads.conditional
import com.looper.ads.createAdaptiveBannerShimmerLayout
import com.looper.ads.isSupportAds
import kotlin.math.ceil

sealed class GridType
data class Fixed(val count: Int) : GridType()
data class Adaptive(val minSize: Int) : GridType()

fun GridType.toGridCells(): GridCells {
    return when (this) {
        is Adaptive -> GridCells.Adaptive(this.minSize.dp)
        is Fixed -> GridCells.Fixed(this.count)
    }
}

data class BannerAdGridType(
    val config: BannerConfig,
    val originalItems: List<Any> = emptyList()
)

object AdItem

@Composable
fun LazyInlineVerticalGridCheckScroll(
    modifier: Modifier = Modifier,
    gridType: GridType,
    adGridType: BannerAdGridType,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    borderConfig: BorderConfig = BorderConfig(),
    viewAbove: @Composable ColumnScope.() -> Unit = {},
    viewBelow: @Composable ColumnScope.() -> Unit = {},
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
        size.height / context.dp2px(adGridType.config.originalItemHeightDp)
    }

    val originalRowCount = remember(key1 = adGridType.originalItems.size, key2 = columnCount) {
        val count = if (columnCount == 0) {
            1
        } else {
            columnCount
        }

        ceil(adGridType.originalItems.size.toFloat() / count).toInt()
    }

    val canScroll = remember(key1 = originalRowCount, key2 = spanRowCount) {
        originalRowCount >= spanRowCount // check ">=" because heightScreen include verticalPadding of List
    }

    LazyInlineVerticalGrid(
        modifier = modifier.onSizeChanged {
            if (size == IntSize.Zero)
                size = it
        },
        canScroll = canScroll,
        gridType = gridType,
        adGridType = adGridType,
        state = state,
        contentPadding = contentPadding,
        borderConfig = borderConfig,
        viewAbove = viewAbove,
        viewBelow = viewBelow,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        onKey = onKey,
        onItemBuilder = onItemBuilder,
        onSingleItemBuilder = onSingleItemBuilder
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyInlineVerticalGrid(
    modifier: Modifier = Modifier,
    canScroll: Boolean = false,
    gridType: GridType,
    adGridType: BannerAdGridType,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    borderConfig: BorderConfig = BorderConfig(),
    viewAbove: @Composable ColumnScope.() -> Unit = {},
    viewBelow: @Composable ColumnScope.() -> Unit = {},
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
            columns = gridType.toGridCells(),
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

                items(
                    items = adGridType.originalItems,
                    key = {
                        onKey(it)
                    }
                ) {
                    Box(
                        modifier = Modifier.conditional(
                            condition = adGridType.config.isAnimateItemPlacement,
                            ifTrue = { animateItem() }
                        )
                    ) {
                        onItemBuilder(it)
                    }
                }
            }
        )
    }

    val context = LocalContext.current
    val density = LocalDensity.current

    var size by remember {
        mutableStateOf(IntSize.Zero)
    }

    var adView by remember { mutableStateOf<View?>(null) }

    val bannerPadding = adGridType.config.bannerType.bannerPadding

    val viewModel: InlineBannerViewModel = viewModel<InlineBannerViewModel>(
        key = adGridType.config.bannerType.name,
        factory = InlineBannerFactory(
            bannerType = adGridType.config.bannerType
        )
    ).apply {
        init(context = context, paddingList = contentPadding)
    }

    LaunchedEffect(key1 = Unit) {
        viewModel.loadBannerAd(context = context, paddingList = contentPadding)
    }

    val adResult by viewModel.bannerAdResult.collectAsStateWithLifecycle(null)

    val columnCount = remember(size) {
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

    val spanRowCount by remember(key1 = size) {
        val value = size.height / context.dp2px(adGridType.config.originalItemHeightDp)
        mutableIntStateOf(value)
    }

    var items by remember(key1 = adGridType.originalItems.size) {
        mutableStateOf(adGridType.originalItems)
    }

    LaunchedEffect(adResult) {
        adView = when (adResult) {
            is BannerState.Loading -> context.createAdaptiveBannerShimmerLayout()

            is BannerState.Success -> (adResult as BannerState.Success).view

            else -> null
        }
    }

    LaunchedEffect(key1 = columnCount, key2 = adGridType, key3 = spanRowCount) {
        items = addBannerItem(
            originalItems = adGridType.originalItems,
            spanRowCount = spanRowCount,
            columnCount = columnCount,
            startAdIndex = if (adGridType.config.startIndex == NEXT_SCROLL_INDEX) spanRowCount else adGridType.config.startIndex
        )
    }

    LazyVerticalGrid(
        columns = gridType.toGridCells(),
        modifier = modifier.onSizeChanged {
            if (size != it)
                size = it
        },
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
                    item(key = "inline_banner_${index}", span = { GridItemSpan(columnCount) }) {
                        Box(
                            modifier = Modifier
                                .conditional(
                                    condition = adGridType.config.isAnimateItemPlacement,
                                    ifTrue = {
                                        animateItem()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            BannerItem(
                                view = adView!!,
                                borderConfig = borderConfig,
                                bannerPadding = bannerPadding,
                                viewAbove = viewAbove,
                                viewBelow = viewBelow,
                            )
                        }
                    }
                }
            } else {
                item(key = onKey(item)) {
                    Box(
                        modifier = Modifier.conditional(
                            condition = adGridType.config.isAnimateItemPlacement,
                            ifTrue = { animateItem() }
                        )
                    ) {
                        onItemBuilder(item)
                    }
                }
            }
        }
    }
}

fun Context.px2Dp(px: Int): Float {
    return px / (resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
}

fun getColumnCount(
    size: IntSize,
    gridType: GridType,
    density: Density,
    contentPadding: PaddingValues,
    horizontalArrangement: Arrangement.Horizontal
): Int {
    return with(gridType.toGridCells()) {
        with(density) {
            val horizontalPadding =
                contentPadding.calculateStartPadding(LayoutDirection.Ltr) + contentPadding.calculateEndPadding(
                    LayoutDirection.Ltr
                )
            val crossAxisCellSizes = calculateCrossAxisCellSizes(
                size.width - horizontalPadding.roundToPx(),
                horizontalArrangement.spacing.roundToPx()
            )

            crossAxisCellSizes.size
        }
    }
}

private fun Context.dp2px(dp: Int): Int {
    return (resources.displayMetrics.density * dp + 0.5f).toInt()
}

@Composable
private fun BannerItem(
    modifier: Modifier = Modifier,
    view: View,
    borderConfig: BorderConfig,
    bannerPadding: PaddingValues,
    viewAbove: @Composable ColumnScope.() -> Unit = {},
    viewBelow: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier = modifier) {

        viewAbove()

        AndroidView(
            factory = {
                FrameLayout(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues = bannerPadding)
                .border(
                    width = borderConfig.strokeWidth,
                    color = borderConfig.strokeColor,
                    shape = borderConfig.shape
                )
                .clip(borderConfig.shape),
            update = { container ->
                container.updateView(view)
            }
        )

        viewBelow()
    }
}

private fun addBannerItem(
    originalItems: List<Any>,
    startAdIndex: Int,
    spanRowCount: Int = 6,
    columnCount: Int = 6
): List<Any> {
    val realIndex = startAdIndex - 1
    val list = mutableListOf<Any>()

    if (originalItems.isNotEmpty() && columnCount > 0) {
        val chucks = originalItems.chunked(columnCount)

        if (originalItems.size < columnCount) {
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