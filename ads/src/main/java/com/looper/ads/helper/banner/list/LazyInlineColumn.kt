package com.looper.ads.helper.banner.list

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.contains
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.looper.ads.helper.banner.data.BannerAdType
import com.looper.ads.helper.banner.data.BannerState
import com.looper.ads.conditional
import com.looper.ads.createAdaptiveBannerShimmerLayout
import com.looper.ads.dp2Px
import com.looper.ads.isSupportAds
import com.looper.ads.helper.lazy_list.LazyListIntervalContent
import com.looper.ads.helper.lazy_list.LazyListScopeImpl
import com.looper.ads.theme.FFD9D9D9

const val ORIGINAL_ITEM_HEIGHT_UNLIMITED = -1
const val NEXT_SCROLL_INDEX = -111

data class BannerConfig(
    val bannerType: BannerAdType,
    val originalItemHeightDp: Int = 142,
    val startIndex: Int = 2,
    val isAnimateItemPlacement: Boolean = true
)

data class SingleBannerConfig(
    val bannerType: BannerAdType,
    val startIndex: Int = 2,
    val isAnimateItemPlacement: Boolean = true
)

data class BorderConfig(
    val strokeWidth: Dp = 1.dp,
    val shape: Shape = RoundedCornerShape(size = 8.dp),
    val strokeColor: Color = Color.Transparent
)

@Composable
fun LazyInlineColumnCheckScroll(
    modifier: Modifier = Modifier,
    config: BannerConfig,
    originalListSize: Int,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bannerBackground: Color = Color.Transparent,
    borderConfig: BorderConfig = BorderConfig(),
    viewAbove: @Composable ColumnScope.() -> Unit = {},
    viewBelow: @Composable ColumnScope.() -> Unit = {},
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    val context = LocalContext.current

    var heightScreen by remember {
        mutableIntStateOf(0)
    }

    val heightItem = config.originalItemHeightDp

    val numberOfMax = remember(key1 = heightScreen, key2 = heightItem) {
        heightScreen / context.dp2Px(dp = heightItem)
    }

    val canScroll = remember(key1 = originalListSize, key2 = numberOfMax) {
        originalListSize >= numberOfMax // check ">=" because heightScreen include verticalPadding of List
    }

    LazyInlineColumn(
        modifier = modifier.onSizeChanged {
            if (heightScreen == 0)
                heightScreen = it.height
        },
        config = config,
        canScroll = canScroll,
        state = state,
        contentPadding = contentPadding,
        bannerBackground = bannerBackground,
        borderConfig = borderConfig,
        viewAbove = viewAbove,
        viewBelow = viewBelow,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        content = content
    )
}


@Composable
fun LazySingleInlineColumn(
    modifier: Modifier = Modifier,
    config: SingleBannerConfig,
    canScroll: Boolean = true,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bannerBackground: Color = Color.Transparent,
    borderConfig: BorderConfig = BorderConfig(),
    viewAbove: @Composable ColumnScope.() -> Unit = {},
    viewBelow: @Composable ColumnScope.() -> Unit = {},
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    LazyInlineColumn(
        modifier = modifier,
        config = BannerConfig(
            bannerType = config.bannerType,
            originalItemHeightDp = ORIGINAL_ITEM_HEIGHT_UNLIMITED,
            startIndex = config.startIndex,
            isAnimateItemPlacement = config.isAnimateItemPlacement
        ),
        canScroll = canScroll,
        state = state,
        contentPadding = contentPadding,
        bannerBackground = bannerBackground,
        borderConfig = borderConfig,
        viewAbove = viewAbove,
        viewBelow = viewBelow,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        content = content
    )
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyInlineColumn(
    modifier: Modifier = Modifier,
    config: BannerConfig,
    canScroll: Boolean = true,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bannerBackground: Color = Color.Transparent,
    borderConfig: BorderConfig = BorderConfig(),
    viewAbove: @Composable ColumnScope.() -> Unit = { BaseLine() },
    viewBelow: @Composable ColumnScope.() -> Unit = { BaseLine() },
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {

    if (!isSupportAds || !canScroll) {
        return LazyColumn(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            content = content
        )
    }

    val context = LocalContext.current

    val bannerPadding = config.bannerType.bannerPadding

    var adView by remember { mutableStateOf<View?>(null) }

    val viewModel: InlineBannerViewModel = viewModel<InlineBannerViewModel>(
        key = config.bannerType.name,
        factory = InlineBannerFactory(bannerType = config.bannerType)
    ).apply {
        init(context = context, paddingList = contentPadding)
    }

    LaunchedEffect(key1 = Unit) {
        viewModel.loadBannerAd(context = context, paddingList = contentPadding)
    }

    val adResult by viewModel.bannerAdResult.collectAsStateWithLifecycle(null)

    LaunchedEffect(adResult) {
        adView = when (adResult) {
            is BannerState.Loading -> context.createAdaptiveBannerShimmerLayout()

            is BannerState.Success -> (adResult as BannerState.Success).view

            else -> null
        }
    }

    var heightList by remember {
        mutableIntStateOf(0)
    }

    val originalItemHeightDp = config.originalItemHeightDp

    val bannerSpace = if (originalItemHeightDp == ORIGINAL_ITEM_HEIGHT_UNLIMITED) {
        Int.MAX_VALUE
    } else {
        heightList / context.dp2Px(config.originalItemHeightDp) + 2
    }

    LazyColumn(
        modifier = modifier.onSizeChanged {
            if (heightList == 0) {
                heightList = it.height
            }
        },
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled
    ) {
        val listScope = LazyListScopeImpl(
            startIndex = if (config.startIndex == NEXT_SCROLL_INDEX) bannerSpace else config.startIndex,
            adSpace = bannerSpace
        ).apply(content)

        for (item in listScope.items) {
            when (item) {
                is LazyListIntervalContent.Single -> {
                    item(item.key, item.contentType, item.content)
                }

                is LazyListIntervalContent.Multi -> {
                    items(item.count, item.key, item.contentType, item.content)
                }

                is LazyListIntervalContent.Header -> {
                    stickyHeader(item.key, item.contentType, item.content)
                }

                is LazyListIntervalContent.Native -> {
                    val view = adView
                    if (view != null) {
                        item("${view.hashCode()} - ${item.key}") {
                            Box(
                                modifier = Modifier
                                    .conditional(
                                        condition = config.isAnimateItemPlacement,
                                        ifTrue = {
                                            animateItem()
                                        }
                                    )
                                    .padding(paddingValues = bannerPadding)
                                    .background(color = bannerBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                BannerItem(
                                    view = view,
                                    borderConfig = borderConfig,
                                    viewAbove = viewAbove,
                                    viewBelow = viewBelow,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerItem(
    view: View,
    borderConfig: BorderConfig,
    viewAbove: @Composable ColumnScope.() -> Unit = {},
    viewBelow: @Composable ColumnScope.() -> Unit = {},
) {

    Column {

        viewAbove()

        AndroidView(
            factory = {
                FrameLayout(it)
            },
            modifier = Modifier
                .fillMaxWidth()
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

fun ViewGroup.updateView(child: View) {
    if (!this.contains(child)) {
        if (child.parent is ViewGroup)
            (child.parent as ViewGroup).removeAllViews()

        removeAllViews()
        addView(child)
    }
}

@Composable
fun BaseLine() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(height = 1.dp)
            .background(color = FFD9D9D9)
    )
}