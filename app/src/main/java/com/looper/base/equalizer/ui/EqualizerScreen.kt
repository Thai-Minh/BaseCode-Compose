package com.looper.base.equalizer.ui

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looper.base.R
import com.looper.base.equalizer.model.EqConstants
import com.looper.base.equalizer.model.EqPreset
import com.looper.base.equalizer.ui.data.EqEffect
import com.looper.base.equalizer.ui.data.EqIntent
import com.looper.base.equalizer.ui.data.EqState
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember {
        SnackbarHostState()
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is EqEffect.ShowMessage -> snackbarHostState.showSnackbar(
                    message = context.getString(effect.resId)
                )
            }
        }
    }

    EqualizerContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun EqualizerContent(
    state: EqState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onIntent: (EqIntent) -> Unit,
) {
    Scaffold(
        topBar = {
            EqTopBar(
                enabled = state.settings.enabled,
                supported = state.supported,
                onBack = onBack,
                onToggle = {
                    onIntent(EqIntent.ToggleEnabled(enabled = it))
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { padding ->
        EqualizerPanel(
            state = state,
            onIntent = onIntent,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(state = rememberScrollState())
                .padding(bottom = 24.dp),
        )
    }
}

@Composable
internal fun EqualizerPanel(
    state: EqState,
    onIntent: (EqIntent) -> Unit,
    modifier: Modifier = Modifier,
    showEnableSwitch: Boolean = false,
) {
    Column(modifier = modifier) {
        if (showEnableSwitch) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(id = R.string.equalizer),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(weight = 1f),
                )
                Switch(
                    checked = state.settings.enabled,
                    enabled = state.supported,
                    onCheckedChange = {
                        onIntent(EqIntent.ToggleEnabled(enabled = it))
                    },
                )
            }
        }

        if (!state.supported) {
            UnsupportedCard(audioSessionId = state.audioSessionId)
            return@Column
        }

        if (!state.attached) {
            Text(
                text = stringResource(id = R.string.eq_waiting_player),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        PresetRow(
            presets = state.presets,
            selectedId = state.settings.presetId,
            isCustom = state.isCustom,
            onSelect = {
                onIntent(EqIntent.SelectPreset(preset = it))
            },
            onManage = {
                onIntent(EqIntent.OpenManagePreset(preset = it))
            },
        )

        BandSliders(
            freqsHz = state.bandFreqsHz,
            levelsMb = state.settings.bandLevelsMb,
            minLevelMb = state.minLevelMb,
            maxLevelMb = state.maxLevelMb,
            enabled = state.settings.enabled,
            onLevelChange = { index, level ->
                onIntent(
                    EqIntent.ChangeBand(
                        index = index,
                        levelMb = level
                    )
                )
            },
        )

        EffectSlider(
            label = stringResource(id = R.string.eq_bass_boost),
            value = state.settings.bassBoost,
            max = EqConstants.MAX_STRENGTH,
            enabled = state.settings.enabled,
            onValueChange = {
                onIntent(EqIntent.ChangeBassBoost(strength = it))
            },
        )

        EffectSlider(
            label = stringResource(id = R.string.eq_virtualizer),
            value = state.settings.virtualizer,
            max = EqConstants.MAX_STRENGTH,
            enabled = state.settings.enabled,
            onValueChange = {
                onIntent(EqIntent.ChangeVirtualizer(strength = it))
            },
        )

        EffectSlider(
            label = stringResource(id = R.string.eq_loudness),
            value = state.settings.loudnessGainMb,
            max = EqConstants.MAX_LOUDNESS_MB,
            enabled = state.settings.enabled,
            onValueChange = {
                onIntent(EqIntent.ChangeLoudness(gainMb = it))
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    onIntent(EqIntent.Reset)
                },
                modifier = Modifier.weight(weight = 1f),
            ) {
                Text(text = stringResource(id = R.string.eq_reset))
            }
            Button(
                onClick = {
                    onIntent(EqIntent.OpenSaveDialog)
                },
                modifier = Modifier.weight(weight = 1f),
            ) {
                Text(text = stringResource(id = R.string.eq_save_preset))
            }
        }
    }

    if (state.isSaveDialogVisible) {
        PresetNameDialog(
            title = stringResource(id = R.string.eq_save_preset),
            initialName = "",
            onDismiss = {
                onIntent(EqIntent.DismissSaveDialog)
            },
            onConfirm = {
                onIntent(EqIntent.SavePreset(name = it))
            },
        )
    }

    state.managedPreset?.let { preset ->
        ManagePresetDialog(
            preset = preset,
            onDismiss = {
                onIntent(EqIntent.DismissManagePreset)
            },
            onRename = {
                onIntent(EqIntent.RenamePreset(preset = preset, name = it))
            },
            onDelete = {
                onIntent(EqIntent.DeletePreset(preset = preset))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqTopBar(
    enabled: Boolean,
    supported: Boolean,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    TopAppBar(
        title = {
            Text(text = stringResource(id = R.string.equalizer))
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        },
        actions = {
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = supported,
                modifier = Modifier.padding(end = 12.dp),
            )
        },
    )
}

@Composable
private fun UnsupportedCard(audioSessionId: Int) {
    val context = LocalContext.current
    val canOpenSystem = remember {
        context.canOpenSystemEqualizer()
    }

    Card(modifier = Modifier.padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.eq_not_supported_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.eq_not_supported_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (canOpenSystem) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    context.openSystemEqualizer(audioSessionId = audioSessionId)
                }) {
                    Text(text = stringResource(id = R.string.eq_open_system))
                }
            }
        }
    }
}

@Composable
private fun PresetRow(
    presets: List<EqPreset>,
    selectedId: String?,
    isCustom: Boolean,
    onSelect: (EqPreset) -> Unit,
    onManage: (EqPreset) -> Unit,
) {
    Text(
        text = stringResource(id = R.string.eq_presets),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isCustom) {
            item(key = "__custom__") {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = {
                        Text(text = stringResource(id = R.string.eq_custom))
                    },
                )
            }
        }
        items(presets, key = {
            it.id
        }) { preset ->
            FilterChip(
                selected = preset.id == selectedId,
                onClick = {
                    onSelect(preset)
                },
                label = {
                    Text(text = preset.name)
                },
                trailingIcon = if (preset.isBuiltIn) {
                    null
                } else {
                    {
                        IconButton(
                            onClick = {
                                onManage(preset)
                            },
                            modifier = Modifier
                                .height(24.dp)
                                .width(24.dp),
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(id = R.string.eq_manage_preset)
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun BandSliders(
    freqsHz: List<Int>,
    levelsMb: List<Int>,
    minLevelMb: Int,
    maxLevelMb: Int,
    enabled: Boolean,
    onLevelChange: (index: Int, levelMb: Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 16.dp)
            .alpha(
                if (enabled) {
                    1f
                } else {
                    0.45f
                }
            ),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        freqsHz.forEachIndexed { index, freq ->
            val levelMb = levelsMb.getOrNull(index) ?: 0
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = formatDb(levelMb = levelMb),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
                VerticalSlider(
                    value = levelMb.toFloat(),
                    onValueChange = {
                        onLevelChange(index, it.roundToInt())
                    },
                    valueRange = minLevelMb.toFloat()..maxLevelMb.toFloat(),
                    enabled = enabled,
                    modifier = Modifier
                        .height(height = 220.dp)
                        .width(width = 40.dp),
                )
                Text(
                    text = formatFrequency(freq),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun EffectSlider(
    label: String,
    value: Int,
    max: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(weight = 1f))
            Text(
                text = "${(value * 100f / max).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = {
                onValueChange(it.roundToInt())
            },
            valueRange = 0f..max.toFloat(),
            enabled = enabled,
        )
    }
}

@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                rotationZ = 270f
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    )
                )
                layout(width = placeable.height, height = placeable.width) {
                    placeable.place(
                        x = -(placeable.width - placeable.height) / 2,
                        y = (placeable.width - placeable.height) / 2,
                    )
                }
            },
    )
}

@Composable
private fun PresetNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable {
        mutableStateOf(value = initialName)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                },
                label = {
                    Text(text = stringResource(id = R.string.eq_preset_name))
                },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(name)
            }) {
                Text(text = stringResource(id = R.string.eq_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.eq_cancel))
            }
        },
    )
}

@Composable
private fun ManagePresetDialog(
    preset: EqPreset,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var renaming by rememberSaveable(preset.id) {
        mutableStateOf(value = false)
    }

    if (renaming) {
        PresetNameDialog(
            title = stringResource(id = R.string.eq_rename),
            initialName = preset.name,
            onDismiss = onDismiss,
            onConfirm = onRename,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(preset.name)
        },
        text = {
            Text(text = stringResource(id = R.string.eq_manage_preset))
        },
        confirmButton = {
            TextButton(onClick = {
                renaming = true
            }) {
                Text(text = stringResource(id = R.string.eq_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(id = R.string.eq_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
    )
}

private fun formatDb(levelMb: Int): String {
    val rounded = (levelMb / 100f).roundToInt()
    return if (rounded > 0) {
        "+$rounded dB"
    } else {
        "$rounded dB"
    }
}

private fun formatFrequency(hz: Int): String =
    if (hz >= 1000) "${if (hz % 1000 == 0) (hz / 1000).toString() else "%.1f".format(hz / 1000f)}kHz" else "${hz}Hz"

private fun systemEqualizerIntent(context: Context, audioSessionId: Int) =
    Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        if (audioSessionId != EqConstants.AUDIO_SESSION_UNSET) {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
        }
    }

private fun Context.canOpenSystemEqualizer(): Boolean =
    systemEqualizerIntent(
        context = this,
        audioSessionId = EqConstants.AUDIO_SESSION_UNSET
    ).resolveActivity(packageManager) != null

private fun Context.openSystemEqualizer(audioSessionId: Int) {
    runCatching {
        startActivity(systemEqualizerIntent(context = this, audioSessionId = audioSessionId))
    }
}
