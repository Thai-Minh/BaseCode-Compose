package com.looper.base.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.looper.base.R
import com.looper.base.database.AppDatabase
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private data class StatsWidgetData(
    val listenedMs: Long,
    val topTitles: List<String>,
)

class StatsWidget : GlanceAppWidget(), KoinComponent {

    private companion object {
        const val WINDOW_MS = 7L * 24L * 60L * 60L * 1000L
        const val TOP_COUNT = 3
    }

    private val database: AppDatabase by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = runCatching {
            load()
        }.getOrDefault(defaultValue = StatsWidgetData(listenedMs = 0L, topTitles = emptyList()))
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        provideContent {
            GlanceTheme {
                StatsContent(context = context, data = data, launchIntent = launchIntent)
            }
        }
    }

    private suspend fun load(): StatsWidgetData {
        val now = System.currentTimeMillis()
        val from = now - WINDOW_MS
        val eventDao = database.playbackEventDao()
        val top = eventDao.observeTopMedia(from = from, to = now, limit = TOP_COUNT).first()
        val titles = database.mediaItemDao()
            .getByIds(
                ids = top.map {
                    it.mediaItemId
                }
            )
            .associate {
                it.id to it.title
            }
        return StatsWidgetData(
            listenedMs = eventDao.observeTotalListened(from = from, to = now).first(),
            topTitles = top.mapNotNull {
                titles[it.mediaItemId]
            },
        )
    }

    @Composable
    private fun StatsContent(context: Context, data: StatsWidgetData, launchIntent: Intent?) {
        val rootModifier = GlanceModifier
            .fillMaxSize()
            .background(colorProvider = GlanceTheme.colors.widgetBackground)
            .cornerRadius(radius = 16.dp)
            .padding(all = 12.dp)
        Column(
            modifier = launchIntent?.let {
                rootModifier.clickable(onClick = actionStartActivity(intent = it))
            } ?: rootModifier,
        ) {
            Text(
                text = context.getString(R.string.widget_stats_title),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            val totalMinutes = data.listenedMs / 60_000L
            Text(
                text = context.getString(
                    R.string.widget_listening_time,
                    totalMinutes / 60,
                    totalMinutes % 60
                ),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            if (data.topTitles.isEmpty()) {
                Text(
                    text = context.getString(R.string.widget_stats_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            } else {
                data.topTitles.forEachIndexed { index, title ->
                    Text(
                        text = "${index + 1}. $title",
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface),
                    )
                }
            }
        }
    }
}

class StatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatsWidget()
}
