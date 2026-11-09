package com.looper.base.screen.profile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looper.base.R
import com.looper.base.equalizer.autoeq.AutoEqEntry
import com.looper.base.equalizer.model.TenBandPreset
import com.looper.base.screen.profile.data.EqPresetText
import com.looper.base.screen.profile.data.EqSelection
import com.looper.base.screen.profile.data.EqualizerPage
import com.looper.base.screen.profile.data.EqualizerSheetEffect
import com.looper.base.screen.profile.data.EqualizerSheetIntent
import com.looper.base.screen.profile.data.EqualizerSheetState
import com.looper.base.ui.theme.ColorFFEE32
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.ui.theme.sfProTextStyle
import com.looper.base.utils.clickable
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun EqualizerSheet(
    onClose: () -> Unit,
    viewModel: EqualizerSheetViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(key1 = viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is EqualizerSheetEffect.ShowMessage -> Toast.makeText(
                    context,
                    effect.arg?.let { arg ->
                        context.getString(effect.resId, arg)
                    } ?: context.getString(effect.resId),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    BackHandler(
        enabled = state.page == EqualizerPage.Headphones,
        onBack = {
            viewModel.onIntent(EqualizerSheetIntent.CloseHeadphones)
        },
    )

    AnimatedContent(
        targetState = state.page,
        label = "equalizerPage",
    ) { page ->
        when (page) {
            EqualizerPage.Main -> EqualizerMainPage(
                state = state,
                onIntent = viewModel::onIntent,
                onClose = onClose,
            )

            EqualizerPage.Headphones -> HeadphonePage(
                state = state,
                onIntent = viewModel::onIntent,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun EqualizerMainPage(
    state: EqualizerSheetState,
    onIntent: (EqualizerSheetIntent) -> Unit,
    onClose: () -> Unit,
) {
    val selection = state.selection

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(state = rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        SheetHeader(
            title = stringResource(id = R.string.profile_eq_title),
            onBackClick = onClose,
            onDoneClick = onClose,
        )

        VerticalGap(height = 33.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = R.string.profile_eq_toggle),
                style = sfProTextStyle(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.W500,
                ),
                modifier = Modifier.weight(weight = 1f),
            )

            AppToggle(
                checked = state.enabled,
                onCheckedChange = { enabled ->
                    onIntent(EqualizerSheetIntent.ToggleEnabled(enabled = enabled))
                },
            )
        }

        VerticalGap(height = 16.dp)

        Text(
            text = stringResource(id = R.string.profile_eq_description),
            style = sfProTextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = ColorFFFFFF.copy(alpha = 0.5f),
            ),
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        VerticalGap(height = 24.dp)

        EqGraph(
            levels = state.tenBand.gainsDb,
            labels = state.frequenciesHz.map { frequency ->
                frequencyLabel(frequencyHz = frequency)
            },
            minLevel = state.minGainDb,
            maxLevel = state.maxGainDb,
            enabled = state.enabled,
            onBandChange = { index, gain ->
                onIntent(EqualizerSheetIntent.ChangeGain(index = index, gainDb = gain))
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        VerticalGap(height = 24.dp)

        Text(
            text = stringResource(id = R.string.profile_eq_preset),
            style = sfProTextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.W500,
                color = ColorFFFFFF.copy(alpha = 0.5f),
            ),
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        VerticalGap(height = 4.dp)

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            state.firstPreset?.let { preset ->
                PresetOption(
                    preset = preset,
                    selection = selection,
                    onIntent = onIntent,
                )
            }

            CheckOption(
                title = stringResource(id = R.string.profile_eq_custom),
                description = stringResource(id = R.string.profile_eq_custom_description),
                selected = selection == EqSelection.Custom,
                onClick = {
                    onIntent(EqualizerSheetIntent.SelectCustom)
                },
            )

            CheckOption(
                title = stringResource(id = R.string.profile_eq_headphone),
                description = (selection as? EqSelection.Headphone)?.name
                    ?: stringResource(id = R.string.profile_eq_headphone_description),
                selected = selection is EqSelection.Headphone,
                onClick = {
                    onIntent(EqualizerSheetIntent.OpenHeadphones)
                },
            )

            state.otherPresets.forEach { preset ->
                PresetOption(
                    preset = preset,
                    selection = selection,
                    onIntent = onIntent,
                )
            }
        }
    }
}

@Composable
private fun PresetOption(
    preset: TenBandPreset,
    selection: EqSelection,
    onIntent: (EqualizerSheetIntent) -> Unit,
) {
    val text = EqPresetText.of(presetId = preset.id)

    CheckOption(
        title = text?.let {
            stringResource(id = it.titleRes)
        } ?: preset.name,
        description = text?.let {
            stringResource(id = it.descriptionRes)
        }.orEmpty(),
        selected = selection == EqSelection.Preset(presetId = preset.id),
        onClick = {
            onIntent(EqualizerSheetIntent.SelectPreset(preset = preset))
        },
    )
}

@Composable
private fun HeadphonePage(
    state: EqualizerSheetState,
    onIntent: (EqualizerSheetIntent) -> Unit,
    onClose: () -> Unit,
) {
    val activeName = (state.selection as? EqSelection.Headphone)?.name

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        SheetHeader(
            title = stringResource(id = R.string.profile_eq_headphone_title),
            onBackClick = {
                onIntent(EqualizerSheetIntent.CloseHeadphones)
            },
            onDoneClick = onClose,
        )

        VerticalGap(height = 24.dp)

        SearchField(
            query = state.query,
            onQueryChange = { query ->
                onIntent(EqualizerSheetIntent.QueryChange(query = query))
            },
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        VerticalGap(height = 16.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(weight = 1f),
        ) {
            when {
                !state.canSearch -> HeadphoneMessage(
                    text = stringResource(id = R.string.profile_eq_headphone_prompt),
                )

                state.isSearching -> CircularProgressIndicator(
                    color = ColorFFEE32,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .align(alignment = Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .size(size = 28.dp),
                )

                state.searchFailed -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HeadphoneMessage(
                        text = stringResource(id = R.string.profile_eq_headphone_error),
                    )
                    PrimaryButton(
                        text = stringResource(id = R.string.profile_eq_headphone_retry),
                        onClick = {
                            onIntent(EqualizerSheetIntent.RetrySearch)
                        },
                    )
                }

                state.results.isEmpty() -> HeadphoneMessage(
                    text = stringResource(id = R.string.profile_eq_headphone_empty),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 14.dp),
                ) {
                    items(
                        items = state.results,
                        key = { entry ->
                            entry.path + entry.name
                        },
                    ) { entry ->
                        HeadphoneRow(
                            entry = entry,
                            selected = entry.name == activeName,
                            loading = entry == state.loadingProfile,
                            onClick = {
                                onIntent(EqualizerSheetIntent.SelectHeadphone(entry = entry))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size = 24.dp)
    val style = sfProTextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(value = ColorFFEE32),
        modifier = modifier
            .fillMaxWidth()
            .height(height = 48.dp)
            .clip(shape = shape)
            .background(color = ColorFFFFFF.copy(alpha = 0.05f))
            .border(
                width = 0.5.dp,
                color = ColorFFFFFF.copy(alpha = 0.1f),
                shape = shape,
            ),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_home_search),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(color = ColorFFFFFF.copy(alpha = 0.5f)),
                    modifier = Modifier.size(size = 20.dp),
                )

                HorizontalGap(width = 10.dp)

                Box(
                    modifier = Modifier.weight(weight = 1f),
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(id = R.string.profile_eq_headphone_search_hint),
                            style = style.copy(color = ColorFFFFFF.copy(alpha = 0.5f)),
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun HeadphoneMessage(text: String) {
    Text(
        text = text,
        style = sfProTextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = ColorFFFFFF.copy(alpha = 0.5f),
        ),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    )
}

@Composable
private fun HeadphoneRow(
    entry: AutoEqEntry,
    selected: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                rippleColor = ColorFFFFFF,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        Column(
            modifier = Modifier.weight(weight = 1f),
        ) {
            Text(
                text = entry.name,
                style = sfProTextStyle(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.W500,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(id = R.string.profile_eq_headphone_source, entry.source),
                style = sfProTextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = ColorFFFFFF.copy(alpha = 0.5f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (loading) {
            CircularProgressIndicator(
                color = ColorFFEE32,
                strokeWidth = 2.dp,
                modifier = Modifier.size(size = 20.dp),
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.ic_profile_check),
                contentDescription = null,
                modifier = Modifier.alpha(
                    alpha = if (selected) {
                        1f
                    } else {
                        0f
                    }
                ),
            )
        }
    }
}

@Composable
private fun frequencyLabel(frequencyHz: Int): String = if (frequencyHz >= 1_000) {
    val kilo = frequencyHz / 1_000f
    val text = if (kilo % 1f == 0f) {
        kilo.toInt().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", kilo)
    }
    stringResource(id = R.string.profile_eq_khz, text)
} else {
    stringResource(id = R.string.profile_eq_hz, frequencyHz.toString())
}

@Composable
private fun EqGraph(
    levels: List<Float>,
    labels: List<String>,
    minLevel: Float,
    maxLevel: Float,
    enabled: Boolean,
    onBandChange: (index: Int, level: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = sfProTextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = ColorFFFFFF.copy(alpha = 0.5f),
    )
    val currentLevels by rememberUpdatedState(newValue = levels)
    val currentOnBandChange by rememberUpdatedState(newValue = onBandChange)
    val range = (maxLevel - minLevel).takeIf {
        it > 0f
    } ?: 1f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height = 248.dp)
            .alpha(
                alpha = if (enabled) {
                    1f
                } else {
                    0.4f
                }
            )
            .pointerInput(minLevel, maxLevel) {
                val pointRadius = 8.dp.toPx()
                val top = pointRadius
                val bottom = 212.dp.toPx()
                var activeIndex = -1

                fun levelAt(y: Float): Float {
                    val fraction = 1f - ((y - top) / (bottom - top)).coerceIn(
                        minimumValue = 0f,
                        maximumValue = 1f
                    )
                    return minLevel + fraction * range
                }

                fun indexAt(x: Float): Int {
                    val count = currentLevels.size
                    if (count <= 1) {
                        return 0
                    }
                    val step = (size.width - pointRadius * 2f) / (count - 1)
                    return ((x - pointRadius) / step).roundToInt().coerceIn(
                        minimumValue = 0,
                        maximumValue = count - 1
                    )
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        activeIndex = indexAt(x = offset.x)
                        currentOnBandChange(activeIndex, levelAt(y = offset.y))
                    },
                    onDragEnd = {
                        activeIndex = -1
                    },
                    onDragCancel = {
                        activeIndex = -1
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (activeIndex >= 0) {
                            currentOnBandChange(activeIndex, levelAt(y = change.position.y))
                        }
                    },
                )
            },
    ) {
        val count = levels.size
        if (count == 0) {
            return@Canvas
        }
        val pointRadius = 8.dp.toPx()
        val top = pointRadius
        val bottom = 212.dp.toPx()
        val baseline = 220.dp.toPx()
        val step = if (count > 1) {
            (size.width - pointRadius * 2f) / (count - 1)
        } else {
            0f
        }
        val points = levels.mapIndexed { index, level ->
            val fraction = ((level - minLevel) / range).coerceIn(
                minimumValue = 0f,
                maximumValue = 1f
            )
            Offset(
                x = pointRadius + step * index,
                y = top + (1f - fraction) * (bottom - top),
            )
        }

        points.forEach { point ->
            drawLine(
                color = ColorFFFFFF.copy(alpha = 0.05f),
                start = Offset(x = point.x, y = 0f),
                end = Offset(x = point.x, y = baseline),
                strokeWidth = 2.dp.toPx(),
            )
        }

        val area = Path().apply {
            moveTo(x = points.first().x, y = baseline)
            points.forEach { point ->
                lineTo(x = point.x, y = point.y)
            }
            lineTo(x = points.last().x, y = baseline)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(
                    ColorFFEE32.copy(alpha = 0.3f),
                    ColorFFEE32.copy(alpha = 0f),
                ),
                startY = points.minOf {
                    it.y
                },
                endY = baseline,
            ),
        )

        val line = Path().apply {
            moveTo(x = points.first().x, y = points.first().y)
            points.drop(n = 1).forEach { point ->
                lineTo(x = point.x, y = point.y)
            }
        }
        drawPath(
            path = line,
            color = ColorFFEE32,
            style = Stroke(width = 2.dp.toPx()),
        )

        points.forEach { point ->
            drawCircle(
                color = ColorFFFFFF,
                radius = pointRadius,
                center = point,
            )
        }

        val labelTop = 232.dp.toPx()
        val gap = 6.dp.toPx()
        val placed = labels.take(n = count).mapIndexed { index, label ->
            val layout = textMeasurer.measure(text = label, style = labelStyle)
            val x = (points[index].x - layout.size.width / 2f).coerceIn(
                minimumValue = 0f,
                maximumValue = (size.width - layout.size.width).coerceAtLeast(minimumValue = 0f)
            )
            Triple(first = x, second = x + layout.size.width, third = layout)
        }
        val visible = mutableListOf<Triple<Float, Float, TextLayoutResult>>()
        placed.forEachIndexed { index, candidate ->
            val previous = visible.lastOrNull()
            val overlaps = previous != null && candidate.first < previous.second + gap
            when {
                !overlaps -> visible.add(element = candidate)
                index == placed.lastIndex -> {
                    visible.removeAt(index = visible.lastIndex)
                    visible.add(element = candidate)
                }
            }
        }
        visible.forEach { (x, _, layout) ->
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x = x, y = labelTop),
            )
        }
    }
}
