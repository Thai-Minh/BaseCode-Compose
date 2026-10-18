package com.looper.base.screen.download_test

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontWeight.Companion.W400
import androidx.compose.ui.text.font.FontWeight.Companion.W500
import androidx.compose.ui.text.font.FontWeight.Companion.W600
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.looper.base.screen.download_test.data.CatcherResult
import com.looper.base.ui.theme.Color171717
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.ui.theme.Typography
import com.looper.base.utils.toTimeFormat
import com.looper.base.utils.toBrowserCatchItem
import org.koin.androidx.compose.koinViewModel

@Composable
fun DownloadTestScreen(
    viewModel: CatcherViewModel = koinViewModel(),
    onResult: (CatcherResult?) -> Unit = {}
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is CatcherEffect.Result -> currentOnResult(effect.result)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorFFFFFF)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        OutlinedTextField(
            value = uiState.url,
            onValueChange = { viewModel.onIntent(CatcherIntent.UrlChanged(it)) },
            label = { Text("Audio URL") },
            placeholder = { Text("https://...") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            enabled = !uiState.isBusy,
            isError = uiState.error != null && uiState.audio == null,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        Button(
            onClick = {
                focusManager.clearFocus()
                viewModel.onIntent(CatcherIntent.Catch)
            },
            enabled = uiState.canCatch,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text("Catcher")
        }
        if (uiState.phase == CatcherPhase.RESOLVING) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text("Reading link...")
            }
        }
        if (!uiState.isConnected) {
            Text(
                text = "No internet connection",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }
        uiState.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
        if (uiState.payload != null) {
            Content(
                modifier = Modifier.fillMaxWidth(),
                uiState = uiState,
                onIntent = viewModel::onIntent
            )
        }
    }
}

@Composable
private fun Content(
    modifier: Modifier = Modifier,
    uiState: CatcherUiState,
    onIntent: (CatcherIntent) -> Unit
) {
    val payload = uiState.payload ?: return
    val item = payload.item.toBrowserCatchItem()
    val durationMs = item.duration

    Column(
        modifier = modifier
    ) {
        VideoInfoGroup(
            modifier = Modifier
                .padding(all = 16.dp)
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(16.dp))
                .background(color = ColorFFFFFF)
                .padding(6.dp),
            thumbnailUrl = item.thumbnailUrl.orEmpty(),
            title = uiState.download?.displayName ?: uiState.outputName,
            subTitle = if (durationMs <= 0) null else durationMs.toTimeFormat()
        )

        Text(
            text = "MP3 audio",
            style = Typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        DownloadStateView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            uiState = uiState
        )

        if (uiState.phase == CatcherPhase.READY || uiState.phase == CatcherPhase.STARTING_DOWNLOAD) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
            ) {
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable(enabled = !uiState.isBusy, onClick = { onIntent(CatcherIntent.Dismiss) })
                        .padding(16.dp),
                    text = "Cancel",
                    style = Typography.titleMedium.copy(
                        color = Color171717,
                        fontSize = 14.sp,
                        fontWeight = W600
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.width(12.dp))

                Text(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable(
                            enabled = uiState.canDownload,
                            onClick = { onIntent(CatcherIntent.Download) }
                        )
                        .padding(16.dp),
                    text = if (uiState.phase == CatcherPhase.STARTING_DOWNLOAD) "Starting..." else "Download MP3",
                    style = Typography.titleMedium.copy(
                        color = Color171717,
                        fontSize = 14.sp,
                        fontWeight = W600
                    ),
                    textAlign = TextAlign.Center
                )
            }
        } else if (uiState.phase == CatcherPhase.DOWNLOADING ||
            uiState.phase == CatcherPhase.CANCELING
        ) {
            Text(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .clickable(
                        enabled = uiState.phase == CatcherPhase.DOWNLOADING,
                        onClick = { onIntent(CatcherIntent.CancelDownload) }
                    )
                    .padding(16.dp),
                text = if (uiState.phase == CatcherPhase.CANCELING) "Canceling..." else "Cancel",
                style = Typography.titleMedium.copy(
                    color = Color171717,
                    fontSize = 14.sp,
                    fontWeight = W600
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DownloadStateView(
    uiState: CatcherUiState,
    modifier: Modifier = Modifier
) {
    when (uiState.phase) {
        CatcherPhase.DOWNLOADING, CatcherPhase.CANCELING -> DownloadingState(
            modifier = modifier,
            progress = uiState.download?.progress ?: 0
        )

        CatcherPhase.COMPLETED -> {
            Column(modifier = modifier) {
                DownloadedState()
                Text(
                    text = uiState.download?.displayName ?: uiState.outputName,
                    style = Typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        else -> Unit
    }
}

@Composable
private fun DownloadingState(
    progress: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Downloading",
                style = Typography.titleMedium.copy(
                    color = Color171717,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1
            )

            Text(
                text = "${progress.coerceIn(0, 100)}%",
                style = Typography.titleMedium.copy(
                    color = Color171717,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1
            )
        }

        DownloadProgressBar(
            progress = progress,
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(7.dp)
        )
    }
}

@Composable
private fun DownloadProgressBar(
    progress: Int,
    modifier: Modifier = Modifier
) {
    val progressFraction = progress.coerceIn(0, 100) / 100f

    Box(
        modifier = modifier
            .clip(shape = CircleShape)
            .background(color = Color171717)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progressFraction)
                .clip(shape = CircleShape)
                .background(ColorFFFFFF)
        )
    }
}

@Composable
private fun DownloadedState(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Downloaded",
            style = Typography.titleMedium.copy(
                color = Color171717,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun VideoInfoGroup(
    modifier: Modifier = Modifier,
    thumbnailUrl: String,
    title: String,
    subTitle: String?
) {
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(thumbnailUrl)
            .build(),
    )

    val state = painter.state.collectAsStateWithLifecycle()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painter,
            contentDescription = "Video Thumbnail Failed",
            modifier = Modifier
                .height(56.dp)
                .aspectRatio(ratio = 80 / 56f)
                .clip(shape = RoundedCornerShape(8.dp))
                .background(color = Color171717),
            contentScale = if (state.value is AsyncImagePainter.State.Success) ContentScale.Crop else ContentScale.Inside
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            val extensionStartIndex = title.lastIndexOf('.')
            val hasExtension = extensionStartIndex > 0 && extensionStartIndex < title.lastIndex
            val displayName = if (hasExtension) title.substring(0, extensionStartIndex) else title
            val extension = if (hasExtension) title.substring(extensionStartIndex) else ""
            val titleStyle = Typography.titleMedium.copy(
                color = Color171717,
                fontWeight = W500,
                fontSize = 14.sp
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = displayName,
                    modifier = Modifier.weight(1f),
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (extension.isNotEmpty()) {
                    Text(
                        text = extension,
                        style = titleStyle,
                        maxLines = 1
                    )
                }
            }

            if (subTitle != null) {
                Text(
                    text = subTitle,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(),
                    style = Typography.titleMedium.copy(
                        color = Color171717,
                        fontSize = 12.sp,
                        fontWeight = W400
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
