package com.looper.base.screen.profile

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looper.base.R
import com.looper.base.player.model.AudioQuality
import com.looper.base.screen.profile.data.AudioQualityOption
import com.looper.base.screen.profile.data.StorageUi
import com.looper.base.ui.theme.ColorDF6C02
import com.looper.base.ui.theme.ColorFFEE32
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.ui.theme.sfProTextStyle

@Composable
internal fun AudioQualitySheet(
    options: List<AudioQualityOption>,
    selected: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(state = rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        SheetHeader(
            title = stringResource(id = R.string.profile_quality_title),
            onBackClick = onClose,
            onDoneClick = onClose,
        )

        VerticalGap(height = 20.dp)

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { option ->
                CheckOption(
                    title = stringResource(id = option.titleRes),
                    description = stringResource(id = option.descriptionRes),
                    selected = option.quality == selected,
                    onClick = {
                        onSelect(option.quality)
                    },
                )
            }
        }
    }
}

@Composable
internal fun ClearCacheSheet(
    storage: StorageUi?,
    isClearing: Boolean,
    onClearClick: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val format = { bytes: Long ->
        Formatter.formatShortFileSize(context, bytes)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        SheetHeader(
            title = stringResource(id = R.string.profile_cache_title),
            onBackClick = onClose,
            onDoneClick = onClose,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(weight = 1f)
                .verticalScroll(state = rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 32.dp),
        ) {
            Text(
                text = stringResource(id = R.string.profile_cache_storage),
                style = sfProTextStyle(
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.W600,
                ),
            )

            VerticalGap(height = 20.dp)

            Text(
                text = stringResource(
                    id = R.string.profile_cache_free,
                    format(storage?.freeBytes ?: 0L)
                ),
                style = cacheCaptionStyle(),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )

            VerticalGap(height = 10.dp)

            StorageBar(
                storage = storage,
            )

            VerticalGap(height = 10.dp)

            Text(
                text = stringResource(
                    id = R.string.profile_cache_used,
                    format(storage?.usedBytes ?: 0L)
                ),
                style = cacheCaptionStyle(),
            )

            VerticalGap(height = 24.dp)

            Column(
                verticalArrangement = Arrangement.spacedBy(space = 16.dp),
            ) {
                StorageLegend(
                    color = ColorFFEE32,
                    text = stringResource(
                        id = R.string.profile_cache_other_apps,
                        format(storage?.otherAppsBytes ?: 0L)
                    ),
                )
                StorageLegend(
                    color = ColorDF6C02,
                    text = stringResource(
                        id = R.string.profile_cache_downloaded,
                        format(storage?.downloadsBytes ?: 0L)
                    ),
                )
                StorageLegend(
                    color = ColorFFFFFF.copy(alpha = 0.6f),
                    text = stringResource(
                        id = R.string.profile_cache_player,
                        format(storage?.cacheBytes ?: 0L)
                    ),
                )
                StorageLegend(
                    color = ColorFFFFFF.copy(alpha = 0.15f),
                    text = stringResource(
                        id = R.string.profile_cache_free_legend,
                        format(storage?.freeBytes ?: 0L)
                    ),
                )
            }

            VerticalGap(height = 32.dp)

            Text(
                text = stringResource(id = R.string.profile_cache_description),
                style = cacheCaptionStyle(),
            )

            VerticalGap(height = 24.dp)
        }

        PrimaryButton(
            text = stringResource(id = R.string.profile_cache_clear),
            enabled = !isClearing && storage != null,
            fontWeight = FontWeight.W600,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            onClick = onClearClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        )
    }
}

private fun cacheCaptionStyle() = sfProTextStyle(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = ColorFFFFFF.copy(alpha = 0.5f),
)

@Composable
private fun StorageBar(
    storage: StorageUi?,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height = 10.dp)
            .clip(shape = RoundedCornerShape(size = 32.dp))
            .background(color = ColorFFFFFF.copy(alpha = 0.15f)),
    ) {
        if (storage == null) {
            return@Canvas
        }
        var start = 0f
        listOf(
            storage.otherAppsBytes to ColorFFEE32,
            storage.downloadsBytes to ColorDF6C02,
            storage.cacheBytes to ColorFFFFFF.copy(alpha = 0.6f),
        ).forEach { (bytes, color) ->
            val width = size.width * storage.fractionOf(bytes = bytes)
            drawRect(
                color = color,
                topLeft = Offset(x = start, y = 0f),
                size = Size(width = width, height = size.height),
            )
            start += width
        }
    }
}

@Composable
private fun StorageLegend(
    color: Color,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(size = 12.dp)
                .clip(shape = CircleShape)
                .background(color = color),
        )
        Text(
            text = text,
            style = sfProTextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        )
    }
}

private val DialogButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)

@Composable
internal fun ClearCacheDialog(
    cacheBytes: Long,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current

    AppDialog(
        onDismissRequest = onDismiss,
    ) {
        DialogTexts(
            title = stringResource(id = R.string.profile_cache_dialog_title),
            message = stringResource(
                id = R.string.profile_cache_dialog_message,
                Formatter.formatShortFileSize(context, cacheBytes)
            ),
        )

        VerticalGap(height = 24.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PrimaryButton(
                text = stringResource(id = R.string.profile_cache_dialog_cancel),
                containerColor = ColorFFFFFF.copy(alpha = 0.1f),
                contentColor = ColorFFFFFF,
                contentPadding = DialogButtonPadding,
                onClick = onDismiss,
                modifier = Modifier.weight(weight = 1f),
            )
            PrimaryButton(
                text = stringResource(id = R.string.profile_cache_clear),
                contentPadding = DialogButtonPadding,
                onClick = onConfirm,
                modifier = Modifier.weight(weight = 1f),
            )
        }
    }
}
