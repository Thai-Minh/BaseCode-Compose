package com.looper.base.activities.lockscreen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.looper.base.R
import com.looper.base.service.AudioPlaybackConnection
import com.looper.base.ui.theme.Color171717
import com.looper.base.utils.clickableNoRipple
import com.looper.base.utils.coil.embeddedAudioArtworkRequest

@Composable
fun LockScreenPlayerScreen(
    playback: AudioPlaybackConnection,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val state by playback.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    val audioUri = state.audioUri?.takeIf { it != Uri.EMPTY }
    var embeddedArtworkFailed by remember(audioUri, state.artworkUri) { mutableStateOf(false) }
    val thumbnailModel = remember(context, audioUri, state.artworkUri, embeddedArtworkFailed) {
        if (audioUri != null && !embeddedArtworkFailed) {
            embeddedAudioArtworkRequest(context, audioUri.toString())
        } else {
            state.artworkUri?.takeIf { it != Uri.EMPTY }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color171717)
            .clickableNoRipple(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        val artworkSize = minOf(maxHeight * 0.28f, 190.dp)

        Box(
            Modifier.fillMaxSize()
        ) {
            AsyncImage(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp),
                model = thumbnailModel,
                contentDescription = null,
                contentScale = ContentScale.Crop
            )

            Column(
                Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                )
                AsyncImage(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()

                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        // 1/3 trên: chỉ thấy blur
                                        0f to Color.Transparent,
                                        0.1f to Color.Transparent,

                                        // Top của vùng giữa: blur -> nét
                                        0.3f to Color.Black,

                                        // Chính giữa: nét hoàn toàn
                                        0.7f to Color.Black,

                                        // Bottom vùng giữa: nét -> blur
                                        0.9f to Color.Transparent,

                                        // 1/3 dưới: chỉ thấy blur
                                        1f to Color.Transparent
                                    )
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        },
                    model = thumbnailModel,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth
                )
                Box(modifier = Modifier.weight(1f))
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.audio_now_playing),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .size(artworkSize)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_audio), null, Modifier.size(64.dp),
                    tint = colors.onSurfaceVariant
                )
                AsyncImage(
                    model = thumbnailModel,
                    onError = { embeddedArtworkFailed = true },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                state.title, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
            Text(
                state.artist, style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, enabled = state.isConnected && state.canSkip) {
                    Icon(
                        painterResource(R.drawable.ic_previous),
                        stringResource(R.string.audio_previous)
                    )
                }
                Surface(shape = CircleShape, color = colors.primary) {
                    IconButton(
                        onClick = onToggle,
                        enabled = state.isConnected,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                            stringResource(if (state.isPlaying) R.string.audio_pause else R.string.audio_play),
                            tint = colors.onPrimary
                        )
                    }
                }
                IconButton(onClick = onNext, enabled = state.isConnected && state.canSkip) {
                    Icon(painterResource(R.drawable.ic_next), stringResource(R.string.audio_next))
                }
            }
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onClose) { Text(stringResource(R.string.audio_lock_screen_dismiss)) }
        }
    }
}
