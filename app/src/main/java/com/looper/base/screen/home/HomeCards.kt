package com.looper.base.screen.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.looper.base.R
import com.looper.base.screen.home.data.HomeIntent
import com.looper.base.screen.home.data.NowPlayingUi
import com.looper.base.screen.home.data.PlaylistUi
import com.looper.base.screen.home.data.RecentDownloadUi
import com.looper.base.screen.home.data.SocialApp
import com.looper.base.ui.theme.Color000000
import com.looper.base.ui.theme.Color02010A
import com.looper.base.ui.theme.Color111111
import com.looper.base.ui.theme.Color1E0178
import com.looper.base.ui.theme.Color9D8AC7
import com.looper.base.ui.theme.ColorFFEE32
import com.looper.base.ui.theme.ColorFFFAC0
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.ui.theme.sfProTextStyle
import com.looper.base.utils.clickable
import com.looper.base.utils.clickableBorderCircle
import com.looper.base.utils.conditional
import kotlinx.coroutines.launch
import java.util.Locale

private val CardShape = RoundedCornerShape(size = 50.dp)
private val CardColor = ColorFFFFFF.copy(alpha = 0.05f)
private val CardBorderColor = ColorFFFFFF.copy(alpha = 0.1f)
private val ArtworkPlaceholder = ColorPainter(color = ColorFFFFFF.copy(alpha = 0.1f))
private const val EmptyTrackText = "--/--"
private const val EmptyTimeText = "00:00"

private data class HeroImage(
    @DrawableRes val resId: Int,
    val size: DpSize,
)

private val HeroImages = listOf(
    HeroImage(
        resId = R.drawable.img_home_hero_1,
        size = DpSize(width = 123.333.dp, height = 185.dp)
    ),
    HeroImage(
        resId = R.drawable.img_home_hero_2,
        size = DpSize(width = 135.199.dp, height = 169.045.dp)
    ),
    HeroImage(resId = R.drawable.img_home_hero_3, size = DpSize(width = 126.dp, height = 189.dp)),
)

private val NowPlayingGlow = listOf(
    Shadow(
        radius = 30.dp,
        color = Color9D8AC7.copy(alpha = 0.3f),
        offset = DpOffset(x = (-15).dp, y = 0.dp)
    ),
    Shadow(
        radius = 30.dp,
        color = Color9D8AC7.copy(alpha = 0.3f),
        offset = DpOffset(x = 15.dp, y = 0.dp)
    ),
    Shadow(
        radius = 30.dp,
        color = Color9D8AC7.copy(alpha = 0.3f),
        offset = DpOffset(x = 0.dp, y = 15.dp)
    ),
    Shadow(
        radius = 60.dp,
        color = Color1E0178.copy(alpha = 0.5f),
        offset = DpOffset(x = 50.dp, y = 0.dp)
    ),
    Shadow(
        radius = 60.dp,
        color = Color1E0178.copy(alpha = 0.5f),
        offset = DpOffset(x = 0.dp, y = (-50).dp)
    ),
    Shadow(
        radius = 60.dp,
        color = Color1E0178.copy(alpha = 0.6f),
        offset = DpOffset(x = (-50).dp, y = 0.dp)
    ),
    Shadow(
        radius = 60.dp,
        color = Color1E0178.copy(alpha = 0.8f),
        offset = DpOffset(x = 0.dp, y = 50.dp)
    ),
)

private val PasteFieldFilledColor = ColorFFFFFF.copy(alpha = 0.1f)

private val SearchButtonGlow = listOf(
    Shadow(
        radius = 4.583.dp,
        color = ColorFFFAC0.copy(alpha = 0.8f),
        offset = DpOffset(x = 0.dp, y = 2.75.dp)
    ),
    Shadow(
        radius = 4.583.dp,
        color = ColorFFFAC0.copy(alpha = 0.8f),
        offset = DpOffset(x = 0.dp, y = (-2.75).dp)
    ),
)

private val PasteIconGlow = listOf(
    Shadow(
        radius = 6.dp,
        color = ColorFFEE32.copy(alpha = 0.1f),
        offset = DpOffset(x = 0.dp, y = (-6).dp)
    ),
    Shadow(
        radius = 6.dp,
        color = ColorFFEE32.copy(alpha = 0.1f),
        offset = DpOffset(x = 0.dp, y = 6.dp)
    ),
)

@Composable
internal fun HeroCard(
    link: String,
    onLinkChange: (String) -> Unit,
    onLinkPaste: (String) -> Unit,
    onLinkClear: () -> Unit,
    onLinkSubmit: () -> Unit,
    pasteFieldModifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = CardShape)
            .background(color = CardColor)
            .border(width = 0.5.dp, color = CardBorderColor, shape = CardShape)
            .padding(paddingValues = PaddingValues(top = 39.5.dp, bottom = 24.5.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val highlight = stringResource(id = R.string.home_hero_title_highlight)
        val title = stringResource(id = R.string.home_hero_title, highlight)
        val highlightStart = title.indexOf(string = highlight)

        Text(
            text = buildAnnotatedString {
                append(text = title)
                if (highlightStart >= 0) {
                    addStyle(
                        style = SpanStyle(color = ColorFFEE32),
                        start = highlightStart,
                        end = highlightStart + highlight.length,
                    )
                }
            },
            style = sfProTextStyle(
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.W600,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(height = 6.dp))

        Text(
            text = stringResource(id = R.string.home_hero_subtitle),
            style = sfProTextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = ColorFFFFFF.copy(alpha = 0.5f),
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(height = 30.dp))

        Row(
            modifier = Modifier.wrapContentWidth(
                align = Alignment.CenterHorizontally,
                unbounded = true,
            ),
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            HeroImages.forEach { image ->
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 160.dp)
                        .clip(shape = RoundedCornerShape(size = 24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = image.resId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.requiredSize(size = image.size),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(height = 30.dp))

        PasteLinkField(
            link = link,
            onLinkChange = onLinkChange,
            onLinkPaste = onLinkPaste,
            onLinkClear = onLinkClear,
            onLinkSubmit = onLinkSubmit,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .then(other = pasteFieldModifier),
        )
    }
}

@Composable
private fun PasteLinkField(
    link: String,
    onLinkChange: (String) -> Unit,
    onLinkPaste: (String) -> Unit,
    onLinkClear: () -> Unit,
    onLinkSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size = 32.dp)
    val hasLink = link.isNotEmpty()
    val clipboard = LocalClipboard.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val backgroundColor by animateColorAsState(
        targetValue = if (hasLink) {
            PasteFieldFilledColor
        } else {
            CardColor
        },
        label = "PasteFieldBackground",
    )
    val textStyle = sfProTextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.W500,
    )
    val submit = {
        focusManager.clearFocus()
        onLinkSubmit()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height = 56.dp)
            .clip(shape = shape)
            .background(color = backgroundColor)
            .border(width = 0.5.dp, color = CardBorderColor, shape = shape)
            .padding(
                start = 4.dp,
                end = if (hasLink) {
                    6.dp
                } else {
                    16.dp
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(size = 48.dp)
                .clip(shape = CircleShape)
                .background(color = Color111111)
                .innerShadows(shape = CircleShape, shadows = PasteIconGlow)
                .clickableBorderCircle {
                    scope.launch {
                        val pasted = clipboard.getClipEntry()
                            ?.clipData
                            ?.takeIf { clipData ->
                                clipData.itemCount > 0
                            }
                            ?.getItemAt(0)
                            ?.text
                            ?.toString()
                            ?.trim()
                        if (!pasted.isNullOrEmpty()) {
                            focusManager.clearFocus()
                            onLinkPaste(pasted)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_home_link),
                contentDescription = null,
            )
        }

        BasicTextField(
            value = link,
            onValueChange = onLinkChange,
            modifier = Modifier.weight(weight = 1f),
            textStyle = textStyle,
            singleLine = true,
            cursorBrush = SolidColor(value = ColorFFEE32),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    submit()
                },
            ),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (!hasLink) {
                        Text(
                            text = stringResource(id = R.string.home_paste_link_hint),
                            style = textStyle.copy(color = ColorFFFFFF.copy(alpha = 0.5f)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )

        if (hasLink) {
            Image(
                painter = painterResource(id = R.drawable.ic_home_clear),
                contentDescription = null,
                modifier = Modifier
                    .size(size = 24.dp)
                    .clickableBorderCircle {
                        onLinkClear()
                    },
            )

            Box(
                modifier = Modifier
                    .size(size = 44.dp)
                    .clip(shape = CircleShape)
                    .background(color = ColorFFEE32)
                    .innerShadows(shape = CircleShape, shadows = SearchButtonGlow)
                    .clickableBorderCircle(
                        rippleColor = Color000000,
                        onClick = {
                            submit()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_home_search),
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
internal fun NowPlayingCard(
    nowPlaying: NowPlayingUi?,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = CardShape),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(color = Color02010A)
                .innerShadows(shape = CardShape, shadows = NowPlayingGlow),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    paddingValues = PaddingValues(
                        start = 32.dp,
                        end = 32.dp,
                        top = 24.dp,
                        bottom = 20.dp
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(weight = 1f),
                    verticalArrangement = Arrangement.spacedBy(space = 2.dp),
                ) {
                    Text(
                        text = nowPlaying?.artist?.ifBlank { EmptyTrackText } ?: EmptyTrackText,
                        style = sfProTextStyle(
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = ColorFFFFFF.copy(alpha = 0.75f),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = nowPlaying?.title?.ifBlank { EmptyTrackText }?.capitalizeWords()
                            ?: EmptyTrackText,
                        style = sfProTextStyle(
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.W600,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(size = 44.dp)
                        .clip(shape = CircleShape)
                        .background(color = ColorFFFFFF.copy(alpha = 0.1f))
                        .clickableBorderCircle(
                            onClick = {
                                onIntent(HomeIntent.NowPlayingFavoriteClick)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(
                            id = if (nowPlaying?.isFavorite == true) {
                                R.drawable.ic_home_heart_filled
                            } else {
                                R.drawable.ic_home_heart_outline
                            }
                        ),
                        contentDescription = null,
                    )
                }
            }

            Spacer(modifier = Modifier.height(height = 22.dp))

            PlaybackProgress(progress = nowPlaying?.progress ?: 0f)

            Spacer(modifier = Modifier.height(height = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val timeStyle = sfProTextStyle(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                Text(
                    text = nowPlaying?.let { track ->
                        formatDuration(ms = track.positionMs)
                    } ?: EmptyTimeText,
                    style = timeStyle,
                )
                Text(
                    text = nowPlaying?.let { track ->
                        "-" + formatDuration(ms = track.remainingMs)
                    } ?: EmptyTimeText,
                    style = timeStyle,
                    textAlign = TextAlign.End,
                )
            }

            Spacer(modifier = Modifier.height(height = 16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
            ) {
                PlaybackButton(
                    iconRes = R.drawable.ic_home_skip_previous,
                    width = 56.dp,
                    onClick = {
                        onIntent(HomeIntent.PreviousClick)
                    },
                )
                PlaybackButton(
                    iconRes = if (nowPlaying?.isPlaying == true) {
                        R.drawable.ic_widget_pause
                    } else {
                        R.drawable.ic_home_play
                    },
                    width = 100.dp,
                    onClick = {
                        onIntent(HomeIntent.PlayPauseClick)
                    },
                )
                PlaybackButton(
                    iconRes = R.drawable.ic_home_skip_previous,
                    width = 56.dp,
                    mirrored = true,
                    onClick = {
                        onIntent(HomeIntent.NextClick)
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaybackProgress(
    progress: Float,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height = 10.dp),
    ) {
        val trackTop = 3.dp.toPx()
        val trackHeight = 4.dp.toPx()
        val trackRadius = CornerRadius(x = trackHeight / 2f, y = trackHeight / 2f)
        val thumbRadius = 5.dp.toPx()
        val thumbX = (size.width * progress).coerceIn(
            minimumValue = thumbRadius,
            maximumValue = size.width - thumbRadius,
        )
        val fillEnd = (thumbX + 2.dp.toPx()).coerceAtMost(maximumValue = size.width)

        drawRoundRect(
            color = ColorFFFFFF.copy(alpha = 0.05f),
            topLeft = Offset(x = 0f, y = trackTop),
            size = Size(width = size.width, height = trackHeight),
            cornerRadius = trackRadius,
        )
        drawCircle(
            color = ColorFFEE32,
            radius = thumbRadius,
            center = Offset(x = thumbX, y = size.height / 2f),
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(ColorFFEE32.copy(alpha = 0f), ColorFFEE32),
                startX = 0f,
                endX = fillEnd,
            ),
            topLeft = Offset(x = 0f, y = trackTop),
            size = Size(width = fillEnd, height = trackHeight),
            cornerRadius = trackRadius,
        )
    }
}

@Composable
private fun PlaybackButton(
    @DrawableRes iconRes: Int,
    width: Dp,
    onClick: () -> Unit,
    mirrored: Boolean = false,
) {
    val shape = RoundedCornerShape(size = 32.dp)

    Box(
        modifier = Modifier
            .size(width = width, height = 56.dp)
            .clip(shape = shape)
            .background(color = ColorFFFFFF.copy(alpha = 0.05f))
            .clickable(
                rippleColor = ColorFFFFFF,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.graphicsLayer {
                scaleX = if (mirrored) -1f else 1f
            },
        )
    }
}

@Composable
internal fun SocialAppsCard(
    apps: List<SocialApp>,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        modifier = modifier,
        title = stringResource(id = R.string.home_social_apps),
        onSeeAllClick = null,
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 24.dp),
        onClick = {
            onIntent(HomeIntent.SocialAppsCardClick)
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            apps.forEach { app ->
                Column(
                    modifier = Modifier.width(width = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(space = 10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(size = 64.dp)
                            .clip(shape = CircleShape)
                            .background(color = ColorFFFFFF.copy(alpha = 0.05f))
                            .clickableBorderCircle(
                                onClick = {
                                    onIntent(HomeIntent.SocialAppClick(app = app))
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = app.iconRes),
                            contentDescription = null,
                        )
                    }

                    Text(
                        text = stringResource(id = app.labelRes),
                        style = sfProTextStyle(
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = ColorFFFFFF.copy(alpha = 0.75f),
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FavoritePlaylistCard(
    playlists: List<PlaylistUi>,
    onIntent: (HomeIntent) -> Unit,
) {
    SectionCard(
        title = stringResource(id = R.string.home_favorite_playlist),
        onSeeAllClick = {
            onIntent(HomeIntent.PlaylistsSeeAllClick)
        },
        contentPadding = PaddingValues(top = 24.dp, bottom = 36.dp),
        headerPadding = PaddingValues(horizontal = 32.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        ) {
            items(
                items = playlists,
                key = { playlist ->
                    playlist.id
                },
            ) { playlist ->
                PlaylistItem(
                    playlist = playlist,
                    onIntent = onIntent,
                )
            }
        }
    }
}

@Composable
private fun PlaylistItem(
    playlist: PlaylistUi,
    onIntent: (HomeIntent) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size = 84.dp)
            .clip(shape = RoundedCornerShape(size = 24.dp))
            .clickable(
                rippleColor = ColorFFFFFF,
                onClick = {
                    onIntent(HomeIntent.PlaylistClick(id = playlist.id))
                },
            ),
    ) {
        AsyncImage(
            model = playlist.artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ArtworkPlaceholder,
            error = ArtworkPlaceholder,
            fallback = ArtworkPlaceholder,
            modifier = Modifier.matchParentSize(),
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(color = Color000000.copy(alpha = 0.1f)),
        )

        Box(
            modifier = Modifier
                .align(alignment = Alignment.TopEnd)
                .padding(all = 8.dp)
                .size(size = 32.dp)
                .clip(shape = CircleShape)
                .background(color = Color000000.copy(alpha = 0.2f))
                .clickableBorderCircle(
                    onClick = {
                        onIntent(HomeIntent.PlaylistFavoriteClick(id = playlist.id))
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(
                    id = if (playlist.isFavorite) {
                        R.drawable.ic_home_heart_filled_small
                    } else {
                        R.drawable.ic_home_heart_outline_small
                    }
                ),
                contentDescription = null,
            )
        }
    }
}

@Composable
internal fun RecentDownloadsCard(
    downloads: List<RecentDownloadUi>,
    onIntent: (HomeIntent) -> Unit,
) {
    SectionCard(
        title = stringResource(id = R.string.home_recent_downloads),
        onSeeAllClick = {
            onIntent(HomeIntent.RecentDownloadsSeeAllClick)
        },
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        headerPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(space = 10.dp),
        ) {
            downloads.forEach { download ->
                RecentDownloadItem(
                    download = download,
                    onIntent = onIntent,
                )
            }
        }
    }
}

@Composable
private fun RecentDownloadItem(
    download: RecentDownloadUi,
    onIntent: (HomeIntent) -> Unit,
) {
    val shape = RoundedCornerShape(size = 32.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height = 60.dp)
            .clip(shape = shape)
            .background(color = ColorFFFFFF.copy(alpha = 0.05f))
            .clickable(
                rippleColor = ColorFFFFFF,
                onClick = {
                    onIntent(HomeIntent.RecentDownloadClick(id = download.id))
                },
            )
            .padding(paddingValues = PaddingValues(start = 4.dp, end = 12.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        AsyncImage(
            model = download.artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ArtworkPlaceholder,
            error = ArtworkPlaceholder,
            fallback = ArtworkPlaceholder,
            modifier = Modifier
                .size(size = 52.dp)
                .clip(shape = CircleShape),
        )

        Column(
            modifier = Modifier.weight(weight = 1f),
        ) {
            Text(
                text = download.title,
                style = sfProTextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.W600,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = download.artist,
                style = sfProTextStyle(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = ColorFFFFFF.copy(alpha = 0.5f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Image(
            painter = painterResource(id = R.drawable.ic_home_more),
            contentDescription = null,
            modifier = Modifier
                .rotate(degrees = -90f)
                .clickableBorderCircle(
                    onClick = {
                        onIntent(HomeIntent.RecentDownloadMoreClick(id = download.id))
                    }
                ),
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    onSeeAllClick: (() -> Unit)?,
    contentPadding: PaddingValues,
    headerPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = CardShape)
            .background(color = CardColor)
            .conditional(
                condition = onClick != null,
                ifTrue = {
                    clickable(
                        rippleColor = ColorFFFFFF,
                        onClick = {
                            onClick?.invoke()
                        },
                    )
                },
            )
            .padding(paddingValues = contentPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues = headerPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = sfProTextStyle(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.W500,
                ),
            )
            Image(
                painter = painterResource(id = R.drawable.ic_home_chevron_right),
                contentDescription = null,
                modifier = Modifier.conditional(
                    condition = onSeeAllClick != null,
                    ifTrue = {
                        clickableBorderCircle(
                            onClick = {
                                onSeeAllClick?.invoke()
                            },
                        )
                    },
                ),
            )
        }

        Spacer(modifier = Modifier.height(height = 24.dp))

        content()
    }
}

private fun Modifier.innerShadows(
    shape: Shape,
    shadows: List<Shadow>,
): Modifier = shadows.fold(initial = this) { modifier, shadow ->
    modifier.innerShadow(shape = shape, shadow = shadow)
}

private fun String.capitalizeWords(): String = split(" ").joinToString(separator = " ") { word ->
    word.replaceFirstChar { char ->
        char.titlecase(locale = Locale.getDefault())
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1_000L
    return String.format(
        locale = Locale.US,
        format = "%d:%02d",
        totalSeconds / 60L,
        totalSeconds % 60L,
    )
}
