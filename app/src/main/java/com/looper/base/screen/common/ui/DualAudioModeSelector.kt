package com.looper.base.screen.common.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.looper.base.R
import com.looper.base.screen.common.data.DualAudioMode

/**
 * The output video's sound: the full-frame slot's, the window's or both (see [DualAudioMode]).
 * Shared by the reaction and video combiner screens.
 */
@Composable
fun DualAudioModeSelector(
    selected: DualAudioMode,
    enabled: Boolean,
    onSelect: (DualAudioMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segmentColors = SegmentedButtonDefaults.colors(
        activeContainerColor = Color.White,
        activeContentColor = Color.Black,
        activeBorderColor = Color.White,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = Color.White,
        inactiveBorderColor = Color.White.copy(alpha = 0.5f),
        disabledActiveContainerColor = Color.White.copy(alpha = 0.4f),
        disabledActiveContentColor = Color.Black,
        disabledActiveBorderColor = Color.White.copy(alpha = 0.4f),
        disabledInactiveContainerColor = Color.Transparent,
        disabledInactiveContentColor = Color.White.copy(alpha = 0.4f),
        disabledInactiveBorderColor = Color.White.copy(alpha = 0.2f),
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        val modes = DualAudioMode.entries
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                enabled = enabled,
                colors = segmentColors,
                label = { Text(text = stringResource(mode.labelRes()), maxLines = 1) },
            )
        }
    }
}

@StringRes
private fun DualAudioMode.labelRes(): Int = when (this) {
    DualAudioMode.FullScreen -> R.string.dual_audio_fullscreen
    DualAudioMode.Window -> R.string.dual_audio_window
    DualAudioMode.Both -> R.string.dual_audio_both
}
