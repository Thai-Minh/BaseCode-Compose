package com.looper.base.player.sleeptimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looper.base.R
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerScreen(
    onBack: () -> Unit,
    viewModel: SleepTimerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember {
        SnackbarHostState()
    }

    SleepTimerEffects(viewModel = viewModel, snackbarHostState = snackbarHostState)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.sleep_timer))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { padding ->
        SleepTimerContent(
            state = state,
            onIntent = viewModel::onIntent,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = padding),
            contentPadding = PaddingValues(all = 16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    onDismiss: () -> Unit,
    viewModel: SleepTimerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember {
        SnackbarHostState()
    }

    SleepTimerEffects(viewModel = viewModel, snackbarHostState = snackbarHostState)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = stringResource(id = R.string.sleep_timer),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SleepTimerContent(
            state = state,
            onIntent = viewModel::onIntent,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        )
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun SleepTimerEffects(
    viewModel: SleepTimerViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    LaunchedEffect(key1 = viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SleepTimerEffect.ShowMessage ->
                    snackbarHostState.showSnackbar(message = context.getString(effect.resId))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerContent(
    state: SleepTimerUiState,
    onIntent: (SleepTimerIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(all = 16.dp),
) {
    Column(
        modifier = modifier.padding(paddingValues = contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.isActive) {
            ActiveTimer(state = state, onIntent = onIntent)
        } else {
            Text(
                text = stringResource(id = R.string.sleep_timer_pick_duration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.presetMinutes.forEach { minutes ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            onIntent(SleepTimerIntent.Start(minutes = minutes))
                        },
                        label = {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.sleep_timer_minutes,
                                    minutes,
                                    minutes,
                                )
                            )
                        },
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { onIntent(SleepTimerIntent.StartEndOfTrack) },
                    label = {
                        Text(text = stringResource(id = R.string.sleep_timer_end_of_track))
                    },
                )
            }
        }
        if (!state.isConnected) {
            Text(
                text = stringResource(id = R.string.sleep_timer_not_connected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ActiveTimer(
    state: SleepTimerUiState,
    onIntent: (SleepTimerIntent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        if (state.isCountingDown) {
            Text(
                text = formatRemaining(ms = state.remainingMs),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(id = R.string.sleep_timer_remaining),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(id = R.string.sleep_timer_ends_after_track),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                onIntent(SleepTimerIntent.Extend(minutes = 5))
            }) {
                Text(text = stringResource(id = R.string.sleep_timer_extend_5))
            }
            Button(onClick = {
                onIntent(SleepTimerIntent.Cancel)
            }) {
                Text(text = stringResource(id = R.string.sleep_timer_turn_off))
            }
        }
    }
}

private fun formatRemaining(ms: Long): String {
    val totalSeconds = (ms + 999) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
