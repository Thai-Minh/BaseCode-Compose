package com.looper.base.widget

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.looper.base.R
import java.io.File

object PlaybackWidgetKeys {
    val HAS_MEDIA = booleanPreferencesKey(name = "has_media")
    val TITLE = stringPreferencesKey(name = "title")
    val ARTIST = stringPreferencesKey(name = "artist")
    val IS_PLAYING = booleanPreferencesKey(name = "is_playing")
    val ARTWORK_PATH = stringPreferencesKey(name = "artwork_path")
    val ARTWORK_VERSION = longPreferencesKey(name = "artwork_version")
    val COMMAND = ActionParameters.Key<String>(name = "command")
}

class PlaybackWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        provideContent {
            val state = currentState<Preferences>()
            val artwork = state[PlaybackWidgetKeys.ARTWORK_PATH]
                ?.let(block = ::File)
                ?.takeIf {
                    it.exists()
                }
                ?.let {
                    BitmapFactory.decodeFile(it.absolutePath)
                }
            GlanceTheme {
                PlaybackContent(
                    context = context,
                    state = state,
                    artwork = artwork?.let(block = ::ImageProvider),
                    launchIntent = launchIntent,
                )
            }
        }
    }

    @Composable
    private fun PlaybackContent(
        context: Context,
        state: Preferences,
        artwork: ImageProvider?,
        launchIntent: Intent?,
    ) {
        val hasMedia = state[PlaybackWidgetKeys.HAS_MEDIA] == true
        val isPlaying = state[PlaybackWidgetKeys.IS_PLAYING] == true
        val rootModifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(8.dp)
        Row(
            modifier = launchIntent?.let {
                rootModifier.clickable(actionStartActivity(it))
            } ?: rootModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = artwork ?: ImageProvider(R.drawable.ic_widget_music_note),
                contentDescription = null,
                colorFilter = if (artwork == null) ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant) else null,
                modifier = GlanceModifier
                    .size(56.dp)
                    .cornerRadius(12.dp),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = state[PlaybackWidgetKeys.TITLE]?.takeIf { hasMedia }
                        ?: context.getString(R.string.widget_nothing_playing),
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                state[PlaybackWidgetKeys.ARTIST]?.takeIf { hasMedia && it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                    )
                }
            }
            ControlButton(
                icon = R.drawable.ic_widget_previous,
                description = context.getString(R.string.player_previous),
                command = PlaybackWidgetAction.PREVIOUS,
            )
            ControlButton(
                icon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                description = context.getString(if (isPlaying) R.string.player_pause else R.string.player_play),
                command = PlaybackWidgetAction.TOGGLE,
            )
            ControlButton(
                icon = R.drawable.ic_widget_next,
                description = context.getString(R.string.player_next),
                command = PlaybackWidgetAction.NEXT,
            )
        }
    }

    @Composable
    private fun ControlButton(icon: Int, description: String, command: String) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
            modifier = GlanceModifier
                .size(40.dp)
                .padding(8.dp)
                .clickable(
                    actionRunCallback<PlaybackWidgetAction>(
                        parameters = actionParametersOf(PlaybackWidgetKeys.COMMAND to command)
                    )
                ),
        )
    }
}

class PlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaybackWidget()
}
