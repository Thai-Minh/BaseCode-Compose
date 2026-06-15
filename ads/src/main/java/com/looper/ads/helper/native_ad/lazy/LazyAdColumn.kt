package com.looper.ads.helper.native_ad.lazy

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.looper.ads.R
import com.looper.ads.dp2Px
import com.looper.ads.helper.banner.list.NEXT_SCROLL_INDEX
import com.looper.ads.helper.banner.list.ORIGINAL_ITEM_HEIGHT_UNLIMITED
import com.looper.ads.helper.banner.list.updateView
import com.looper.ads.helper.lazy_list.LazyListIntervalContent
import com.looper.ads.helper.lazy_list.LazyListScopeImpl
import com.looper.ads.helper.native_ad.NativeAdState
import com.looper.ads.helper.native_ad.NativeAdType
import com.looper.ads.helper.native_ad.NativeAdViewModel
import com.looper.ads.helper.native_ad.NativeAdViewModelFactory
import com.looper.ads.helper.native_ad.ui.BoxSpace
import com.looper.ads.helper.native_ad.ui.NativeAdLifecycle
import com.looper.ads.helper.native_ad.ui.NativeSpace
import com.looper.ads.helper.native_ad.ui.buildDefault
import com.looper.ads.isSupportAds
import com.zenith.adapter.ui.UnifiedNativeAdViewBinder

enum class NativeAdColumnType(val config: NativeAdConfig) {
//    DeviceVideoColumnList(
//        config = NativeAdConfig(
//            adType = NativeAdType.NativeVideoDeviceColumnList,
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = 16.dp, vertical = 8.dp)
//                .height(140.dp)
//                .border(
//                    width = 1.dp,
//                    color = FFFFFFFF,
//                    shape = RoundedCornerShape(8.dp)
//                )
//                .clip(RoundedCornerShape(8.dp)),
//            startAdIndex = NEXT_SCROLL_INDEX,
//            itemHeightDp = 96,
//            shimmerLayoutId = R.layout.native_device_video_list_shimmer,
//            nativeLayoutId = R.layout.native_device_video_list
//        )
//    ),

}

data class NativeAdConfig(
    val adType: NativeAdType,
    val modifier: Modifier = Modifier,
    val space: NativeSpace = NativeSpace(),
    val startAdIndex: Int = 1,
    val itemHeightDp: Int = 142,
    @LayoutRes val shimmerLayoutId: Int,
    @LayoutRes val nativeLayoutId: Int,
    val isAnimateItemPlacement: Boolean = true
)

@Composable
fun LazyAdColumnWithCheckScroll(
    modifier: Modifier = Modifier,
    originalListSize: Int,
    state: LazyListState = rememberLazyListState(),
    config: NativeAdConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    lineAbove: @Composable ColumnScope.() -> Unit = {},
    lineBelow: @Composable ColumnScope.() -> Unit = {},
    headerLineColor: Color? = null,
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

    val heightItem = config.itemHeightDp

    val numberOfMax = remember(key1 = heightScreen, key2 = heightItem) {
        heightScreen / context.dp2Px(dp = heightItem)
    }

    val canScroll = remember(key1 = originalListSize, key2 = numberOfMax) {
        originalListSize >= numberOfMax // check ">=" because heightScreen include verticalPadding of List
    }

    val scrollFirst by remember {
        derivedStateOf {
            state.firstVisibleItemIndex == 1 && canScroll && config.startAdIndex == 0
        }
    }

    LaunchedEffect(scrollFirst) {
        if (scrollFirst) {
            state.scrollToItem(0)
        }
    }

    LazyAdColumn(
        modifier = modifier.onSizeChanged {
            if (heightScreen == 0)
                heightScreen = it.height
        },
        isShowAd = canScroll,
        config = config,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        lineAbove = lineAbove,
        lineBelow = lineBelow,
        headerLineColor = headerLineColor,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyAdColumn(
    modifier: Modifier = Modifier,
    isShowAd: Boolean = true,
    state: LazyListState = rememberLazyListState(),
    config: NativeAdConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    lineAbove: @Composable ColumnScope.() -> Unit = {},
    lineBelow: @Composable ColumnScope.() -> Unit = {},
    headerLineColor: Color? = null,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    if (!isSupportAds || !isShowAd) {
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
    val app = LocalContext.current.applicationContext as Application

    var size by remember { mutableStateOf(IntSize.Zero) }

    val itemHeightDp = config.itemHeightDp

    val nativeSpace = if (itemHeightDp == ORIGINAL_ITEM_HEIGHT_UNLIMITED) {
        Int.MAX_VALUE
    } else {
        size.height / app.dp2Px(config.itemHeightDp) + 2
    }

    val viewModel: NativeAdViewModel = viewModel(
        factory = NativeAdViewModelFactory(app, config.adType)
    )

    NativeAdLifecycle(viewModel)

    val nativeAd by viewModel.nativeAd.collectAsStateWithLifecycle(initialValue = NativeAdState.Loading)

    val context = LocalContext.current
    var adView by remember {
        mutableStateOf<View?>(null)
    }

    LaunchedEffect(nativeAd, headerLineColor) {
        adView = when (nativeAd) {
            is NativeAdState.Success -> {
                val view = LayoutInflater.from(context).inflate(
                    config.nativeLayoutId,
                    FrameLayout(context),
                    false
                )
                if (headerLineColor != null) {
                    view.findViewById<TextView>(R.id.ad_matrix_native_headline)
                        .setTextColor(headerLineColor.toArgb())
                }

                val baseView = UnifiedNativeAdViewBinder.Builder(view).buildDefault()

                (nativeAd as NativeAdState.Success).nativeAd.render(baseView)
            }

            is NativeAdState.Loading -> {
                LayoutInflater.from(context).inflate(
                    config.shimmerLayoutId,
                    FrameLayout(context),
                    false
                )
            }

            else -> null
        }
    }

    LazyColumn(
        modifier = modifier.onSizeChanged { size = it },
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
    ) {
        val listScope =
            LazyListScopeImpl(
                startIndex = if (config.startAdIndex == NEXT_SCROLL_INDEX) nativeSpace else config.startAdIndex,
                adSpace = nativeSpace
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
                        item(item.key) {
                            NativeWithLineItem(
                                modifier = config.modifier,
                                view = view,
                                space = config.space,
                                lineAbove = lineAbove,
                                lineBelow = lineBelow
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NativeWithLineItem(
    modifier: Modifier = Modifier,
    view: View,
    space: NativeSpace,
    lineAbove: @Composable ColumnScope.() -> Unit = {},
    lineBelow: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        lineAbove()

        NativeItem(
            modifier = modifier,
            view = view,
            space = space
        )

        lineBelow()
    }
}

@Composable
fun NativeItem(
    modifier: Modifier,
    view: View,
    space: NativeSpace,
) {
    BoxSpace(space = space) {
        AndroidView(
            factory = {
                FrameLayout(it)
            },
            modifier = modifier,
            update = { container ->
                container.updateView(view)
            }
        )
    }
}
