package com.looper.base.screen.audiopreview

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.looper.base.R
import com.looper.base.library.model.PlaylistGenre
import com.looper.base.screen.audiopreview.data.CreatePlaylistUi
import com.looper.base.screen.audiopreview.data.PlaylistOptionUi
import com.looper.base.screen.profile.AppDialog
import com.looper.base.screen.profile.DialogTexts
import com.looper.base.screen.profile.PrimaryButton
import com.looper.base.screen.profile.VerticalGap
import com.looper.base.screen.profile.topRoundedBorder
import com.looper.base.ui.theme.Color000000
import com.looper.base.ui.theme.Color0E100D
import com.looper.base.ui.theme.Color2A2A2A
import com.looper.base.ui.theme.ColorFFEE32
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.ui.theme.sfProTextStyle
import com.looper.base.utils.clickable
import com.looper.base.utils.clickableBorderCircle

private val RowColor = ColorFFFFFF.copy(alpha = 0.05f)
private val RowBorderColor = ColorFFFFFF.copy(alpha = 0.1f)
private val IconBadgeColor = ColorFFFFFF.copy(alpha = 0.06f)
private val CollageCardColor = ColorFFFFFF.copy(alpha = 0.08f)
private val CollagePlaceholder = ColorPainter(color = CollageCardColor)

@Composable
internal fun AddToPlaylistSheet(
    visible: Boolean,
    playlists: List<PlaylistOptionUi>,
    onDismiss: () -> Unit,
    onCreateClick: () -> Unit,
    onToggle: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color000000.copy(alpha = 0.8f))
                .pointerInput(key1 = Unit) {
                    detectTapGestures {
                        onDismiss()
                    }
                },
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { height ->
                    height
                },
            ),
            exit = slideOutVertically(
                targetOffsetY = { height ->
                    height
                },
            ),
        ) {
            val shape = RoundedCornerShape(topStart = 42.dp, topEnd = 42.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape = shape)
                    .background(color = Color0E100D)
                    .topRoundedBorder(
                        width = 0.5.dp,
                        color = RowBorderColor,
                        cornerRadius = 42.dp,
                        drawSides = true,
                    )
                    .pointerInput(key1 = Unit) {
                        detectTapGestures()
                    }
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 23.5.dp, end = 23.5.dp, top = 23.5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .align(alignment = Alignment.CenterStart)
                            .size(size = 40.dp)
                            .clip(shape = CircleShape)
                            .background(color = ColorFFFFFF.copy(alpha = 0.1f))
                            .clickableBorderCircle(
                                onClick = onDismiss,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_social_app_back),
                            contentDescription = stringResource(id = R.string.social_app_back),
                        )
                    }

                    Text(
                        text = stringResource(id = R.string.playlist_add_title),
                        style = sfProTextStyle(
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.W600,
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }

                VerticalGap(height = 24.dp)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(space = 12.dp),
                ) {
                    item(key = "create") {
                        CreatePlaylistRow(onClick = onCreateClick)
                    }
                    items(
                        items = playlists,
                        key = { playlist ->
                            playlist.id
                        },
                    ) { playlist ->
                        PlaylistOptionRow(
                            playlist = playlist,
                            onClick = {
                                onToggle(playlist.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistRow(
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(size = 50.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height = 64.dp)
            .clip(shape = shape)
            .background(color = RowColor)
            .border(width = 0.5.dp, color = RowBorderColor, shape = shape)
            .clickable(
                rippleColor = ColorFFFFFF,
                onClick = onClick,
            )
            .padding(start = 7.5.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(size = 48.dp)
                .clip(shape = CircleShape)
                .background(color = IconBadgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_playlist_create),
                contentDescription = null,
            )
        }

        Text(
            text = stringResource(id = R.string.playlist_create_new),
            style = sfProTextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.W600,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun PlaylistOptionRow(
    playlist: PlaylistOptionUi,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(size = 36.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height = 64.dp)
            .clip(shape = shape)
            .background(color = RowColor)
            .border(width = 0.5.dp, color = RowBorderColor, shape = shape)
            .clickable(
                rippleColor = ColorFFFFFF,
                onClick = onClick,
            )
            .padding(start = 7.5.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        PlaylistCollage(
            cover = playlist.cover,
            thumbnails = playlist.thumbnails,
        )

        Column(
            modifier = Modifier.weight(weight = 1f),
        ) {
            Text(
                text = playlist.name,
                style = sfProTextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.W600,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    id = R.plurals.playlist_item_count,
                    count = playlist.itemCount,
                    playlist.itemCount,
                ),
                style = sfProTextStyle(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = ColorFFFFFF.copy(alpha = 0.5f),
                ),
                maxLines = 1,
            )
        }

        Image(
            painter = painterResource(
                id = if (playlist.isSelected) {
                    R.drawable.ic_playlist_radio_on
                } else {
                    R.drawable.ic_playlist_radio_off
                }
            ),
            contentDescription = null,
        )
    }
}

@Composable
private fun PlaylistCollage(
    cover: Any?,
    thumbnails: List<String>,
) {
    Box(
        modifier = Modifier
            .size(size = 48.dp)
            .clip(shape = CircleShape)
            .background(color = IconBadgeColor),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = CollagePlaceholder,
                error = CollagePlaceholder,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            CollageCard(
                image = thumbnails.getOrNull(index = 1),
                size = 21.5.dp,
                cornerRadius = 3.7.dp,
                rotation = -9.59f,
                modifier = Modifier.offset(x = (-9.4).dp, y = 2.3.dp),
            )
            CollageCard(
                image = thumbnails.getOrNull(index = 2),
                size = 21.5.dp,
                cornerRadius = 3.7.dp,
                rotation = 9.59f,
                modifier = Modifier.offset(x = 9.4.dp, y = 2.dp),
            )
            CollageCard(
                image = thumbnails.getOrNull(index = 0),
                size = 27.7.dp,
                cornerRadius = 4.9.dp,
                rotation = 0f,
                modifier = Modifier.offset(y = (-0.75).dp),
            )
        }
    }
}

@Composable
private fun CollageCard(
    image: String?,
    size: Dp,
    cornerRadius: Dp,
    rotation: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .rotate(degrees = rotation)
            .size(size = size)
            .clip(shape = RoundedCornerShape(size = cornerRadius))
            .background(color = CollageCardColor),
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = CollagePlaceholder,
                error = CollagePlaceholder,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
internal fun CreatePlaylistDialog(
    form: CreatePlaylistUi,
    onNameChange: (String) -> Unit,
    onGenreSelect: (PlaylistGenre) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismiss,
    ) {
        DialogTexts(
            title = stringResource(id = R.string.playlist_create_new),
            message = stringResource(id = R.string.playlist_create_message),
        )

        VerticalGap(height = 24.dp)

        PlaylistNameField(
            name = form.name,
            onNameChange = onNameChange,
        )

        VerticalGap(height = 16.dp)

        GenreField(
            genre = form.genre,
            onGenreSelect = onGenreSelect,
        )

        VerticalGap(height = 24.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PrimaryButton(
                text = stringResource(id = R.string.playlist_create_cancel),
                containerColor = ColorFFFFFF.copy(alpha = 0.1f),
                contentColor = ColorFFFFFF,
                onClick = onDismiss,
                modifier = Modifier.weight(weight = 1f),
            )
            PrimaryButton(
                text = stringResource(id = R.string.playlist_create_confirm),
                enabled = form.canCreate,
                onClick = onConfirm,
                modifier = Modifier.weight(weight = 1f),
            )
        }
    }
}

@Composable
private fun PlaylistNameField(
    name: String,
    onNameChange: (String) -> Unit,
) {
    val textStyle = sfProTextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.W500,
    )

    BasicTextField(
        value = name,
        onValueChange = onNameChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(value = ColorFFEE32),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            FieldContainer {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (name.isEmpty()) {
                        Text(
                            text = stringResource(id = R.string.playlist_create_name_hint),
                            style = textStyle.copy(color = ColorFFFFFF.copy(alpha = 0.5f)),
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
private fun GenreField(
    genre: PlaylistGenre?,
    onGenreSelect: (PlaylistGenre) -> Unit,
) {
    var expanded by remember {
        mutableStateOf(value = false)
    }

    Box {
        FieldContainer(
            onClick = {
                expanded = true
            },
        ) {
            Text(
                text = stringResource(id = genre?.labelRes() ?: R.string.playlist_create_genre_hint),
                style = sfProTextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.W500,
                    color = if (genre == null) {
                        ColorFFFFFF.copy(alpha = 0.5f)
                    } else {
                        ColorFFFFFF
                    },
                ),
                maxLines = 1,
                modifier = Modifier.weight(weight = 1f),
            )
            Image(
                painter = painterResource(id = R.drawable.ic_playlist_dropdown),
                contentDescription = null,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            },
            shape = RoundedCornerShape(size = 16.dp),
            containerColor = Color2A2A2A,
        ) {
            PlaylistGenre.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(id = option.labelRes()),
                            style = sfProTextStyle(
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = if (option == genre) {
                                    FontWeight.W600
                                } else {
                                    FontWeight.W400
                                },
                                color = if (option == genre) {
                                    ColorFFEE32
                                } else {
                                    ColorFFFFFF
                                },
                            ),
                        )
                    },
                    onClick = {
                        expanded = false
                        onGenreSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun FieldContainer(
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(size = 32.dp)
    val base = Modifier
        .fillMaxWidth()
        .height(height = 56.dp)
        .clip(shape = shape)
        .background(color = RowColor)
        .border(width = 0.5.dp, color = RowBorderColor, shape = shape)

    Row(
        modifier = if (onClick != null) {
            base.clickable(
                rippleColor = ColorFFFFFF,
                onClick = onClick,
            )
        } else {
            base
        }.padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        content()
    }
}

@StringRes
private fun PlaylistGenre.labelRes(): Int = when (this) {
    PlaylistGenre.POP -> R.string.playlist_genre_pop
    PlaylistGenre.ROCK -> R.string.playlist_genre_rock
    PlaylistGenre.HIP_HOP -> R.string.playlist_genre_hip_hop
    PlaylistGenre.ELECTRONIC -> R.string.playlist_genre_electronic
    PlaylistGenre.K_POP -> R.string.playlist_genre_k_pop
    PlaylistGenre.LOFI -> R.string.playlist_genre_lofi
    PlaylistGenre.CLASSICAL -> R.string.playlist_genre_classical
    PlaylistGenre.INDIE -> R.string.playlist_genre_indie
    PlaylistGenre.RNB -> R.string.playlist_genre_rnb
}
