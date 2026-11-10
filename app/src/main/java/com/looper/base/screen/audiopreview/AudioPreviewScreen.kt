package com.looper.base.screen.audiopreview

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.ImageRequest
import com.looper.base.R
import com.looper.base.screen.audiopreview.data.AudioPreviewEffect
import com.looper.base.screen.audiopreview.data.AudioPreviewIntent
import com.looper.base.screen.audiopreview.data.AudioPreviewUiState
import com.looper.base.screen.audiopreview.data.LinkError
import com.looper.base.screen.audiopreview.data.PreviewDownloadState
import com.looper.base.screen.audiopreview.data.PreviewTrackUi
import com.looper.base.ui.theme.Color000000
import com.looper.base.ui.theme.Color010300
import com.looper.base.ui.theme.ColorFFEE32
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.ui.theme.sfProTextStyle
import com.looper.base.utils.clickable
import com.looper.base.utils.clickableBorderCircle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.Locale

private val ArtworkPlaceholder = ColorPainter(color = ColorFFFFFF.copy(alpha = 0.05f))
private val CardBorderColor = ColorFFFFFF.copy(alpha = 0.1f)
private val ButtonColor = ColorFFFFFF.copy(alpha = 0.05f)

@Composable
fun AudioPreviewScreen(
    sourceUrl: String,
    onBack: () -> Unit,
    viewModel: AudioPreviewViewModel = koinViewModel(
        parameters = {
            parametersOf(sourceUrl)
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(newValue = onBack)
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(key1 = viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                AudioPreviewEffect.NavigateBack -> currentOnBack()

                is AudioPreviewEffect.ShowLinkError -> Toast.makeText(
                    context,
                    effect.error.messageRes(),
                    Toast.LENGTH_SHORT,
                ).show()

                is AudioPreviewEffect.OpenUrl -> runCatching {
                    uriHandler.openUri(uri = effect.url)
                }

                AudioPreviewEffect.ShowDownloadFailed -> Toast.makeText(
                    context,
                    R.string.preview_download_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    AudioPreviewContent(
        state = state,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun AudioPreviewContent(
    state: AudioPreviewUiState,
    onIntent: (AudioPreviewIntent) -> Unit,
) {
    BackHandler(
        enabled = state.isPlaylistSheetVisible,
        onBack = {
            onIntent(AudioPreviewIntent.PlaylistSheetDismiss)
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color010300),
    ) {
        Crossfade(
            targetState = state.track,
            label = "AudioPreviewPhase",
        ) { track ->
            if (track == null) {
                ExtractingContent(progress = state.progress)
            } else {
                PreviewContent(
                    track = track,
                    state = state,
                    onIntent = onIntent,
                )
            }
        }

        AddToPlaylistSheet(
            visible = state.isPlaylistSheetVisible,
            playlists = state.playlists,
            onDismiss = {
                onIntent(AudioPreviewIntent.PlaylistSheetDismiss)
            },
            onCreateClick = {
                onIntent(AudioPreviewIntent.CreatePlaylistClick)
            },
            onToggle = { playlistId ->
                onIntent(AudioPreviewIntent.PlaylistToggle(playlistId = playlistId))
            },
        )

        state.createPlaylist?.let { form ->
            CreatePlaylistDialog(
                form = form,
                onNameChange = { name ->
                    onIntent(AudioPreviewIntent.CreatePlaylistNameChange(name = name))
                },
                onGenreSelect = { genre ->
                    onIntent(AudioPreviewIntent.CreatePlaylistGenreSelect(genre = genre))
                },
                onDismiss = {
                    onIntent(AudioPreviewIntent.CreatePlaylistDismiss)
                },
                onConfirm = {
                    onIntent(AudioPreviewIntent.CreatePlaylistConfirm)
                },
            )
        }
    }
}

@Composable
private fun ExtractingContent(
    progress: Int,
) {
    val context = LocalContext.current
    val orbRequest = remember(key1 = context) {
        ImageRequest.Builder(context = context)
            .data(data = R.raw.extracting_orb)
            .decoderFactory(
                factory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    AnimatedImageDecoder.Factory()
                } else {
                    GifDecoder.Factory()
                }
            )
            .build()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .offset(y = (-19).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AsyncImage(
            model = orbRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size = 180.dp),
        )

        Spacer(modifier = Modifier.height(height = 10.dp))

        Column(
            modifier = Modifier.width(width = 296.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            val label = stringResource(id = R.string.preview_extracting)
            val percent = stringResource(id = R.string.preview_extracting_percent, progress)
            Text(
                text = buildAnnotatedString {
                    append(text = label)
                    append(text = " ")
                    withStyle(style = SpanStyle(color = ColorFFEE32)) {
                        append(text = percent)
                    }
                },
                style = sfProTextStyle(
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.W700,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(id = R.string.preview_extracting_message),
                style = sfProTextStyle(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = ColorFFFFFF.copy(alpha = 0.5f),
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PreviewContent(
    track: PreviewTrackUi,
    state: AudioPreviewUiState,
    onIntent: (AudioPreviewIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        PreviewHeader(
            onBackClick = {
                onIntent(AudioPreviewIntent.BackClick)
            },
        )

        Column(
            modifier = Modifier
                .weight(weight = 1f)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArtworkCard(
                track = track,
                isFavorite = state.isFavorite,
                onFavoriteClick = {
                    onIntent(AudioPreviewIntent.FavoriteClick)
                },
                modifier = Modifier
                    .weight(weight = 1f)
                    .fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(height = 16.dp))

            ActionRow(
                isPlaying = state.isPlaying,
                download = state.download,
                onIntent = onIntent,
            )

            Spacer(modifier = Modifier.height(height = 16.dp))

            SourceRow(
                track = track,
                onClick = {
                    onIntent(AudioPreviewIntent.SourceClick)
                },
            )
        }
    }
}

@Composable
private fun PreviewHeader(
    onBackClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(alignment = Alignment.CenterStart)
                .size(size = 40.dp)
                .clip(shape = CircleShape)
                .background(color = ColorFFFFFF.copy(alpha = 0.1f))
                .clickableBorderCircle(
                    onClick = onBackClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_social_app_back),
                contentDescription = stringResource(id = R.string.social_app_back),
            )
        }

        Text(
            text = stringResource(id = R.string.preview_title),
            style = sfProTextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.W600,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun ArtworkCard(
    track: PreviewTrackUi,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 50.dp))
            .background(color = ButtonColor),
    ) {
        AsyncImage(
            model = track.artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ArtworkPlaceholder,
            error = ArtworkPlaceholder,
            fallback = ArtworkPlaceholder,
            modifier = Modifier.matchParentSize(),
        )

        Box(
            modifier = Modifier
                .align(alignment = Alignment.TopEnd)
                .padding(all = 24.dp)
                .size(size = 44.dp)
                .clip(shape = CircleShape)
                .background(color = Color000000.copy(alpha = 0.2f))
                .clickableBorderCircle(
                    onClick = onFavoriteClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(
                    id = if (isFavorite) {
                        R.drawable.ic_home_heart_filled
                    } else {
                        R.drawable.ic_home_heart_outline
                    }
                ),
                contentDescription = null,
            )
        }

        Column(
            modifier = Modifier
                .align(alignment = Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 40.dp, end = 40.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space = 2.dp),
        ) {
            if (track.artist.isNotBlank()) {
                Text(
                    text = track.artist,
                    style = sfProTextStyle(
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = ColorFFFFFF.copy(alpha = 0.75f),
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = track.title.capitalizeWords(),
                style = sfProTextStyle(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.W600,
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionRow(
    isPlaying: Boolean,
    download: PreviewDownloadState,
    onIntent: (AudioPreviewIntent) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        CircleAction(
            onClick = {
                onIntent(AudioPreviewIntent.AddToPlaylistClick)
            },
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_preview_add),
                contentDescription = null,
            )
        }

        Row(
            modifier = Modifier
                .height(height = 56.dp)
                .clip(shape = RoundedCornerShape(size = 32.dp))
                .background(color = ColorFFEE32)
                .clickable(
                    rippleColor = Color000000,
                    onClick = {
                        onIntent(AudioPreviewIntent.PlayClick)
                    },
                )
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        ) {
            Image(
                painter = painterResource(
                    id = if (isPlaying) {
                        R.drawable.ic_preview_pause
                    } else {
                        R.drawable.ic_preview_play
                    }
                ),
                contentDescription = null,
            )
            Text(
                text = stringResource(
                    id = if (isPlaying) {
                        R.string.preview_pause
                    } else {
                        R.string.preview_play
                    }
                ),
                style = sfProTextStyle(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.W500,
                    color = Color000000,
                ),
                maxLines = 1,
            )
        }

        CircleAction(
            enabled = download == PreviewDownloadState.IDLE,
            onClick = {
                onIntent(AudioPreviewIntent.DownloadClick)
            },
        ) {
            when (download) {
                PreviewDownloadState.IDLE -> Image(
                    painter = painterResource(id = R.drawable.ic_preview_download),
                    contentDescription = null,
                )

                PreviewDownloadState.DOWNLOADING -> {
                    val transition = rememberInfiniteTransition(label = "DownloadSpinner")
                    val rotation by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1_000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "DownloadSpinnerRotation",
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_preview_downloading),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer {
                            rotationZ = rotation
                        },
                    )
                }

                PreviewDownloadState.DONE -> Image(
                    painter = painterResource(id = R.drawable.ic_preview_downloaded),
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun CircleAction(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size = 56.dp)
            .clip(shape = CircleShape)
            .background(color = ButtonColor)
            .clickableBorderCircle(
                enable = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun SourceRow(
    track: PreviewTrackUi,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(size = 32.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height = 88.dp)
            .clip(shape = shape)
            .background(color = ButtonColor)
            .border(width = 0.5.dp, color = CardBorderColor, shape = shape)
            .clickable(
                rippleColor = ColorFFFFFF,
                onClick = onClick,
            )
            .padding(all = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        AsyncImage(
            model = track.artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ArtworkPlaceholder,
            error = ArtworkPlaceholder,
            fallback = ArtworkPlaceholder,
            modifier = Modifier
                .size(size = 56.dp)
                .clip(shape = RoundedCornerShape(size = 16.dp)),
        )

        Column(
            modifier = Modifier.weight(weight = 1f),
            verticalArrangement = Arrangement.spacedBy(space = 2.dp),
        ) {
            Text(
                text = stringResource(id = R.string.preview_source_from, track.platformName),
                style = sfProTextStyle(
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.W600,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.sourceLabel,
                style = sfProTextStyle(
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = ColorFFFFFF.copy(alpha = 0.75f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Image(
            painter = painterResource(id = R.drawable.ic_preview_open_source),
            contentDescription = null,
        )
    }
}

@StringRes
private fun LinkError.messageRes(): Int = when (this) {
    LinkError.PLAYLIST -> R.string.preview_error_playlist
    LinkError.DRM -> R.string.preview_error_drm
    LinkError.NO_STREAM -> R.string.preview_error_no_stream
    LinkError.FAILED -> R.string.preview_error_failed
}

private fun String.capitalizeWords(): String = split(" ").joinToString(separator = " ") { word ->
    word.replaceFirstChar { char ->
        char.titlecase(locale = Locale.getDefault())
    }
}
