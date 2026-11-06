package com.looper.base.screen.profile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.looper.base.R
import com.looper.base.screen.profile.data.MiniPlayerUi
import com.looper.base.screen.profile.data.ProfileEffect
import com.looper.base.screen.profile.data.ProfileIntent
import com.looper.base.screen.profile.data.ProfileSetting
import com.looper.base.screen.profile.data.ProfileSheet
import com.looper.base.screen.profile.data.ProfileUiState
import com.looper.base.ui.theme.Color000000
import com.looper.base.ui.theme.Color010300
import com.looper.base.ui.theme.Color0E100D
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.ui.theme.sfProTextStyle
import com.looper.base.utils.clickableBorderCircle
import org.koin.compose.viewmodel.koinViewModel

private val ArtworkPlaceholder = ColorPainter(color = ColorFFFFFF.copy(alpha = 0.1f))

@Composable
fun ProfileTab(
    onNavigateAbout: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnNavigateAbout by rememberUpdatedState(newValue = onNavigateAbout)
    val context = LocalContext.current

    LaunchedEffect(key1 = viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ProfileEffect.NavigateToAbout -> currentOnNavigateAbout()
                is ProfileEffect.ShowMessage -> Toast.makeText(
                    context,
                    effect.resId,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    ProfileContent(
        state = state,
        onIntent = viewModel::onIntent,
        equalizerSheet = { onClose ->
            EqualizerSheet(
                onClose = onClose,
            )
        },
    )
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onIntent: (ProfileIntent) -> Unit,
    equalizerSheet: @Composable (onClose: () -> Unit) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = rememberScrollState())
                .navigationBarsPadding()
                .padding(
                    bottom = if (state.miniPlayer != null) {
                        112.dp
                    } else {
                        24.dp
                    }
                ),
        ) {
            ProfileTitle(
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            VerticalGap(height = 24.dp)

            StatsRow(
                downloadedCount = state.downloadedCount,
                hours = state.listeningHours,
                minutes = state.listeningMinutes,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            VerticalGap(height = 16.dp)

            SettingsCard(
                settings = state.settings,
                onSettingClick = { setting ->
                    onIntent(ProfileIntent.SettingClick(setting = setting))
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        state.miniPlayer?.let { miniPlayer ->
            MiniPlayer(
                miniPlayer = miniPlayer,
                onPlayPauseClick = {
                    onIntent(ProfileIntent.MiniPlayerPlayPauseClick)
                },
                onNextClick = {
                    onIntent(ProfileIntent.MiniPlayerNextClick)
                },
                modifier = Modifier.align(alignment = Alignment.BottomCenter),
            )
        }

        ProfileSheetHost(
            sheet = state.sheet,
            onDismiss = {
                onIntent(ProfileIntent.DismissSheet)
            },
        ) { sheet ->
            val onClose = {
                onIntent(ProfileIntent.DismissSheet)
            }
            when (sheet) {
                ProfileSheet.Equalizer -> equalizerSheet(onClose)

                ProfileSheet.AudioQuality -> AudioQualitySheet(
                    options = state.audioQualityOptions,
                    selected = state.audioQuality,
                    onSelect = { quality ->
                        onIntent(ProfileIntent.SelectAudioQuality(quality = quality))
                    },
                    onClose = onClose,
                )

                ProfileSheet.ClearCache -> ClearCacheSheet(
                    storage = state.storage,
                    isClearing = state.isClearingCache,
                    onClearClick = {
                        onIntent(ProfileIntent.ClearCacheClick)
                    },
                    onClose = onClose,
                )
            }
        }
    }

    if (state.isClearCacheDialogVisible) {
        ClearCacheDialog(
            cacheBytes = state.storage?.cacheBytes ?: 0L,
            onDismiss = {
                onIntent(ProfileIntent.DismissClearCacheDialog)
            },
            onConfirm = {
                onIntent(ProfileIntent.ConfirmClearCache)
            },
        )
    }
}

@Composable
private fun ProfileSheetHost(
    sheet: ProfileSheet?,
    onDismiss: () -> Unit,
    content: @Composable (ProfileSheet) -> Unit,
) {
    var lastSheet by remember {
        mutableStateOf(value = sheet)
    }
    if (sheet != null) {
        lastSheet = sheet
    }

    BackHandler(
        enabled = sheet != null,
        onBack = onDismiss,
    )

    AnimatedVisibility(
        visible = sheet != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color000000.copy(alpha = 0.8f)),
        )
    }

    AnimatedVisibility(
        visible = sheet != null,
        enter = slideInVertically(
            initialOffsetY = { height ->
                height
            },
        ),
        exit = slideOutVertically(
            targetOffsetY = { height ->
                height
            },
        ),
    ) {
        val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp)
                .clip(shape = shape)
                .background(color = Color0E100D)
                .topRoundedBorder(
                    width = 1.dp,
                    color = ColorFFFFFF.copy(alpha = 0.15f),
                    cornerRadius = 32.dp,
                    drawSides = false,
                ),
        ) {
            lastSheet?.let { current ->
                content(current)
            }
        }
    }
}

@Composable
private fun ProfileTitle(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = (-2).dp),
    ) {
        Text(
            text = stringResource(id = R.string.profile_title_my),
            style = sfProTextStyle(
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.W600,
            ),
        )
        Text(
            text = stringResource(id = R.string.profile_title_music),
            style = sfProTextStyle(
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.W600,
                color = ColorFFFFFF.copy(alpha = 0.5f),
            ),
        )
    }
}

@Composable
private fun StatsRow(
    downloadedCount: Int,
    hours: Long,
    minutes: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        StatCard(
            label = stringResource(id = R.string.profile_downloaded),
            modifier = Modifier.weight(weight = 1f),
        ) {
            Text(
                text = downloadedCount.toString(),
                style = statValueStyle(),
                maxLines = 1,
            )
        }

        StatCard(
            label = stringResource(id = R.string.profile_listening_time),
            modifier = Modifier.weight(weight = 1f),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = hours.toString(),
                    style = statValueStyle(),
                    maxLines = 1,
                )
                StatUnit(text = stringResource(id = R.string.profile_unit_hour))
                HorizontalGap(width = 6.dp)
                Text(
                    text = minutes.toString(),
                    style = statValueStyle(),
                    maxLines = 1,
                )
                StatUnit(text = stringResource(id = R.string.profile_unit_minute))
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .height(height = 122.dp)
            .clip(shape = RoundedCornerShape(size = 32.dp))
            .background(color = ColorFFFFFF.copy(alpha = 0.05f))
            .padding(start = 16.dp, end = 16.dp, top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.height(height = 36.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            value()
        }

        VerticalGap(height = 16.dp)

        Text(
            text = label,
            style = sfProTextStyle(
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.W500,
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatUnit(text: String) {
    Text(
        text = text,
        style = sfProTextStyle(
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = ColorFFFFFF.copy(alpha = 0.6f),
        ),
        modifier = Modifier.padding(start = 1.dp, bottom = 2.dp),
    )
}

private fun statValueStyle() = sfProTextStyle(
    fontSize = 28.sp,
    lineHeight = 36.sp,
    fontWeight = FontWeight.W600,
)

@Composable
private fun SettingsCard(
    settings: List<ProfileSetting>,
    onSettingClick: (ProfileSetting) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 50.dp))
            .background(color = ColorFFFFFF.copy(alpha = 0.05f))
            .padding(all = 20.dp),
    ) {
        Text(
            text = stringResource(id = R.string.profile_settings),
            style = sfProTextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.W500,
            ),
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        VerticalGap(height = 24.dp)

        Column(
            verticalArrangement = Arrangement.spacedBy(space = 10.dp),
        ) {
            settings.forEach { setting ->
                SettingRow(
                    iconRes = setting.iconRes,
                    label = stringResource(id = setting.labelRes),
                    height = 64.dp,
                    horizontalPadding = 7.5.dp,
                    textStartPadding = 12.dp,
                    onClick = {
                        onSettingClick(setting)
                    },
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    miniPlayer: MiniPlayerUi,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = 42.dp, topEnd = 42.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = shape)
            .background(color = Color0E100D)
            .topRoundedBorder(
                width = 0.5.dp,
                color = ColorFFFFFF.copy(alpha = 0.1f),
                cornerRadius = 42.dp,
                drawSides = true,
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = miniPlayer.artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ArtworkPlaceholder,
            error = ArtworkPlaceholder,
            fallback = ArtworkPlaceholder,
            modifier = Modifier
                .size(size = 52.dp)
                .clip(shape = CircleShape)
                .background(color = ColorFFFFFF),
        )

        HorizontalGap(width = 12.dp)

        Column(
            modifier = Modifier.weight(weight = 1f),
        ) {
            Text(
                text = miniPlayer.title,
                style = sfProTextStyle(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.W600,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = miniPlayer.artist,
                style = sfProTextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = ColorFFFFFF.copy(alpha = 0.5f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        HorizontalGap(width = 12.dp)

        MiniPlayerButton(
            iconRes = if (miniPlayer.isPlaying) {
                R.drawable.ic_widget_pause
            } else {
                R.drawable.ic_home_play
            },
            contentDescription = stringResource(
                id = if (miniPlayer.isPlaying) {
                    R.string.player_pause
                } else {
                    R.string.player_play
                }
            ),
            onClick = onPlayPauseClick,
        )

        HorizontalGap(width = 12.dp)

        MiniPlayerButton(
            iconRes = R.drawable.ic_home_skip_previous,
            contentDescription = stringResource(id = R.string.player_next),
            mirrored = true,
            enabled = miniPlayer.hasNext,
            onClick = onNextClick,
        )
    }
}

@Composable
private fun MiniPlayerButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    mirrored: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(size = 40.dp)
            .clip(shape = CircleShape)
            .background(color = ColorFFFFFF.copy(alpha = 0.05f))
            .clickableBorderCircle(
                enable = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(size = 20.dp)
                .alpha(
                    alpha = if (enabled) {
                        1f
                    } else {
                        0.4f
                    }
                )
                .graphicsLayer {
                    scaleX = if (mirrored) {
                        -1f
                    } else {
                        1f
                    }
                },
        )
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
private fun ProfileContentPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color010300),
    ) {
        ProfileContent(
            state = ProfileUiState(
                downloadedCount = 50,
                listeningMs = (18L * 60L + 42L) * 60_000L,
                miniPlayer = MiniPlayerUi(
                    title = "sip of your name",
                    artist = "kanegi",
                    artwork = null,
                    isPlaying = false,
                    hasNext = true,
                ),
            ),
            onIntent = {},
            equalizerSheet = {},
        )
    }
}
