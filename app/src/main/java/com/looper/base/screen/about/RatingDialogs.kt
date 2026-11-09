package com.looper.base.screen.about

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looper.base.R
import com.looper.base.screen.about.data.AboutIntent
import com.looper.base.screen.about.data.AboutUiState
import com.looper.base.screen.profile.AppDialog
import com.looper.base.screen.profile.DialogTexts
import com.looper.base.screen.profile.PrimaryButton
import com.looper.base.screen.profile.VerticalGap
import com.looper.base.ui.theme.Color000000
import com.looper.base.ui.theme.ColorFFEE32
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.ui.theme.sfProTextStyle
import com.looper.base.utils.clickable
import com.looper.base.utils.noRippleClickable

@Composable
internal fun RatingDialog(
    state: AboutUiState,
    onIntent: (AboutIntent) -> Unit,
) {
    AppDialog(
        onDismissRequest = {
            onIntent(AboutIntent.RatingDismiss)
        },
    ) {
        RatingHeader(
            imageRes = R.drawable.img_rating_feedback,
            title = stringResource(id = R.string.rating_title),
            message = stringResource(id = R.string.rating_message),
        )

        VerticalGap(height = 24.dp)

        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 24.dp),
        ) {
            (1..AboutUiState.MAX_RATING).forEach { star ->
                Image(
                    painter = painterResource(
                        id = if (star <= state.rating) {
                            R.drawable.ic_rating_star_filled
                        } else {
                            R.drawable.ic_rating_star_outline
                        }
                    ),
                    contentDescription = stringResource(id = R.string.rating_star, star),
                    modifier = Modifier
                        .size(size = 36.dp)
                        .noRippleClickable(
                            onClick = {
                                onIntent(AboutIntent.RatingChange(rating = star))
                            },
                        ),
                )
            }
        }

        if (state.isLowRating) {
            VerticalGap(height = 20.dp)

            HorizontalDivider(
                thickness = 1.dp,
                color = ColorFFFFFF.copy(alpha = 0.1f),
            )

            VerticalGap(height = 20.dp)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 16.dp,
                    alignment = Alignment.CenterHorizontally
                ),
                verticalArrangement = Arrangement.spacedBy(space = 16.dp),
            ) {
                state.reasons.forEach { reason ->
                    ReasonChip(
                        text = stringResource(id = reason.labelRes),
                        selected = reason in state.selectedReasons,
                        onClick = {
                            onIntent(AboutIntent.ReasonToggle(reason = reason))
                        },
                    )
                }
            }
        }

        if (state.showsFeedbackField) {
            VerticalGap(height = 20.dp)

            FeedbackField(
                text = state.feedback,
                onTextChange = { text ->
                    onIntent(AboutIntent.FeedbackChange(text = text))
                },
            )
        }

        VerticalGap(height = 32.dp)

        PrimaryButton(
            text = stringResource(id = R.string.rating_rate_us),
            enabled = state.rating > 0,
            onClick = {
                onIntent(AboutIntent.RateSubmit)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        VerticalGap(height = 16.dp)

        Text(
            text = stringResource(id = R.string.rating_maybe_later),
            style = sfProTextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.W500,
                color = ColorFFFFFF.copy(alpha = 0.5f),
            ),
            modifier = Modifier
                .clip(shape = RoundedCornerShape(size = 12.dp))
                .clickable(
                    rippleColor = ColorFFFFFF,
                    onClick = {
                        onIntent(AboutIntent.RatingDismiss)
                    },
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun RatingThanksDialog(
    onIntent: (AboutIntent) -> Unit,
) {
    AppDialog(
        onDismissRequest = {
            onIntent(AboutIntent.RatingDismiss)
        },
    ) {
        RatingHeader(
            imageRes = R.drawable.img_rating_thanks,
            title = stringResource(id = R.string.rating_thanks_title),
            message = stringResource(id = R.string.rating_thanks_message),
        )

        VerticalGap(height = 24.dp)

        PrimaryButton(
            text = stringResource(id = R.string.rating_thanks_ok),
            onClick = {
                onIntent(AboutIntent.RatingDismiss)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RatingHeader(
    @DrawableRes imageRes: Int,
    title: String,
    message: String,
) {
    Image(
        painter = painterResource(id = imageRes),
        contentDescription = null,
        modifier = Modifier.size(width = 80.dp, height = 76.2.dp),
    )

    VerticalGap(height = 8.dp)

    DialogTexts(
        title = title,
        message = message,
    )
}

@Composable
private fun ReasonChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = sfProTextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = if (selected) {
                Color000000
            } else {
                ColorFFFFFF
            },
        ),
        maxLines = 1,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 24.dp))
            .background(
                color = if (selected) {
                    ColorFFEE32
                } else {
                    ColorFFFFFF.copy(alpha = 0.1f)
                }
            )
            .clickable(
                rippleColor = ColorFFFFFF,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun FeedbackField(
    text: String,
    onTextChange: (String) -> Unit,
) {
    val shape = RoundedCornerShape(size = 24.dp)
    val style = sfProTextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        textStyle = style,
        cursorBrush = SolidColor(value = ColorFFEE32),
        modifier = Modifier
            .fillMaxWidth()
            .height(height = 100.dp)
            .clip(shape = shape)
            .background(color = ColorFFFFFF.copy(alpha = 0.05f))
            .border(
                width = 0.5.dp,
                color = ColorFFFFFF.copy(alpha = 0.05f),
                shape = shape,
            ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.padding(all = 12.dp),
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.rating_feedback_hint),
                        style = style.copy(color = ColorFFFFFF.copy(alpha = 0.5f)),
                    )
                }
                innerTextField()
            }
        },
    )
}
