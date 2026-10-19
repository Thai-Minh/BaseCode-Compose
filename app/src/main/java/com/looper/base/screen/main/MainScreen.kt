package com.looper.base.screen.main

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.looper.base.R
import com.looper.base.repository.audio_local.data.DeviceAudio
import com.looper.base.service.AudioPlaybackState
import com.looper.base.widget.AudioHomeWidgetProvider
import com.looper.base.base.storage.audioLockScreenEnabled
import com.looper.base.base.storage.updateAudioLockScreenEnabled
import com.looper.base.utils.coil.embeddedAudioArtworkRequest
import com.looper.local_file_manager.data.FileManagerState
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel = koinViewModel(),
    navigateToDownload: () -> Unit
) {
    val context = LocalContext.current
    val widgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val tabState = if (hasAudioPermission) {
        viewModel.tabState.collectAsStateWithLifecycle(initialValue = FileManagerState.Loading).value
    } else {
        FileManagerState.Loading
    }
    val audioItems = if (hasAudioPermission) {
        viewModel.audioItems.collectAsStateWithLifecycle(initialValue = emptyList()).value
    } else {
        emptyList()
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) viewModel.refresh()
    }
    val lockScreenEnabled by audioLockScreenEnabled.collectAsStateWithLifecycle(false)
    var hasOverlayPermission by rememberSaveable { mutableStateOf(Settings.canDrawOverlays(context)) }
    var showLockScreenPermission by rememberSaveable { mutableStateOf(false) }
    var enableAfterPermission by rememberSaveable { mutableStateOf(false) }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        if (enableAfterPermission && hasOverlayPermission) updateAudioLockScreenEnabled(true)
        enableAfterPermission = false
    }
    val canPinWidget = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        widgetManager.isRequestPinAppWidgetSupported

    LaunchedEffect(Unit) {
        if (!permissionRequested &&
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionRequested = true
            permissionLauncher.launch(permission)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        hasAudioPermission =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (hasAudioPermission) viewModel.refresh()
        viewModel.reconnect()
    }

    if (showLockScreenPermission) {
        AlertDialog(
            onDismissRequest = { showLockScreenPermission = false },
            title = { Text(stringResource(R.string.audio_lock_screen_permission_title)) },
            text = { Text(stringResource(R.string.audio_lock_screen_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLockScreenPermission = false
                    enableAfterPermission = true
                    overlayPermissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
                    )
                }) { Text(stringResource(R.string.audio_lock_screen_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { showLockScreenPermission = false }) {
                    Text(stringResource(R.string.audio_lock_screen_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.audio_library),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasAudioPermission) {
                        TextButton(onClick = viewModel::refresh, enabled = tabState != FileManagerState.Loading) {
                            Text(stringResource(R.string.audio_refresh))
                        }
                    }
                }
                if (hasAudioPermission) {
                    Text(
                        pluralStringResource(R.plurals.audio_count, audioItems.size, audioItems.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.audio_widget_title),
                            style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.audio_widget_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (canPinWidget) {
                        TextButton(onClick = {
                            widgetManager.requestPinAppWidget(
                                ComponentName(context, AudioHomeWidgetProvider::class.java),
                                null,
                                null
                            )
                        }) {
                            Text(stringResource(R.string.audio_widget_add))
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.audio_lock_screen_title),
                            style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.audio_lock_screen_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        when {
                            lockScreenEnabled && hasOverlayPermission -> updateAudioLockScreenEnabled(false)
                            hasOverlayPermission -> updateAudioLockScreenEnabled(true)
                            else -> showLockScreenPermission = true
                        }
                    }) {
                        Text(stringResource(
                            if (lockScreenEnabled && hasOverlayPermission) R.string.audio_lock_screen_disable
                            else R.string.audio_lock_screen_enable
                        ))
                    }
                }

                TextButton(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    onClick = navigateToDownload
                ) {
                    Text("Navigate to download")
                }

                playback.error?.let { error ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(error), Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        if (!playback.isConnected) {
                            TextButton(onClick = viewModel::reconnect) {
                                Text(stringResource(R.string.audio_retry))
                            }
                        }
                    }
                }


                when {
                    !hasAudioPermission -> {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AudioArtwork(null, Modifier.size(88.dp))
                            Text(
                                stringResource(R.string.audio_permission_title),
                                Modifier.padding(top = 24.dp, bottom = 12.dp),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                stringResource(R.string.audio_permission_message),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    permissionRequested = true
                                    permissionLauncher.launch(permission)
                                },
                                modifier = Modifier.padding(top = 24.dp)
                            ) {
                                Text(stringResource(R.string.audio_grant_permission))
                            }
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        "package:${context.packageName}".toUri()
                                    )
                                )
                            }) {
                                Text(stringResource(R.string.open_settings))
                            }
                        }
                    }
                    tabState == FileManagerState.Loading -> Box(
                        Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    tabState == FileManagerState.Failed -> Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.audio_load_error))
                        TextButton(onClick = viewModel::refresh) {
                            Text(stringResource(R.string.audio_retry))
                        }
                    }
                    audioItems.isEmpty() -> Box(
                        Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.audio_empty), Modifier.padding(24.dp))
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(audioItems, key = { it.id }) { audio ->
                            AudioRow(
                                audio, audio.id.toString() == playback.mediaId, playback.isConnected
                            ) {
                                updateAudioLockScreenEnabled(true)

                                viewModel.play(audioItems, audio)
                            }
                        }
                    }
                }
            }
            if (playback.mediaId != null) {
                NowPlayingBar(playback, viewModel::previous, viewModel::togglePlayPause, viewModel::next)
            }
        }
    }
}

@Composable
private fun AudioRow(audio: DeviceAudio, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Row(
        Modifier.fillMaxWidth().background(background).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AudioArtwork(audio.artworkUri, Modifier.size(56.dp), audioUri = audio.uri)
        Column(Modifier.weight(1f)) {
            Text(audio.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium)
            Text(audio.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val seconds = audio.durationMs / 1000
        Text("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AudioArtwork(uri: Uri?, modifier: Modifier = Modifier, audioUri: Uri? = null) {
    val context = LocalContext.current
    val sourceUri = audioUri?.takeIf { it != Uri.EMPTY }
    var embeddedArtworkFailed by remember(sourceUri, uri) { mutableStateOf(false) }
    val model = remember(context, sourceUri, uri, embeddedArtworkFailed) {
        if (sourceUri != null && !embeddedArtworkFailed) {
            embeddedAudioArtworkRequest(context, sourceUri.toString())
        } else {
            uri?.takeIf { it != Uri.EMPTY }
        }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_audio), null, Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(model = model, contentDescription = null,
            onError = { embeddedArtworkFailed = true },
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun NowPlayingBar(
    state: AudioPlaybackState,
    previous: () -> Unit,
    toggle: () -> Unit,
    next: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        Column {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AudioArtwork(state.artworkUri, Modifier.size(48.dp), audioUri = state.audioUri)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.audio_now_playing), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall)
                }
                IconButton(onClick = previous, enabled = state.isConnected && state.canSkip) {
                    Icon(painterResource(R.drawable.ic_previous), stringResource(R.string.audio_previous))
                }
                IconButton(onClick = toggle, enabled = state.isConnected) {
                    Icon(painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        stringResource(if (state.isPlaying) R.string.audio_pause else R.string.audio_play))
                }
                IconButton(onClick = next, enabled = state.isConnected && state.canSkip) {
                    Icon(painterResource(R.drawable.ic_next), stringResource(R.string.audio_next))
                }
            }
        }
    }
}
