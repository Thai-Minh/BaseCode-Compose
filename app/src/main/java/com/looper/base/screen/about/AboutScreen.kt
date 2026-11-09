package com.looper.base.screen.about

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looper.base.R
import com.looper.base.screen.about.data.AboutEffect
import com.looper.base.screen.about.data.AboutIntent
import com.looper.base.screen.about.data.AboutUiState
import com.looper.base.screen.about.data.RatingStage
import com.looper.base.screen.profile.SettingRow
import com.looper.base.screen.profile.VerticalGap
import com.looper.base.ui.theme.Color010300
import com.looper.base.ui.theme.ColorFFEE32
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.ui.theme.sfProTextStyle
import com.looper.base.utils.clickableBorderCircle
import com.looper.base.utils.openGooglePlay
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateLanguage: () -> Unit,
    viewModel: AboutViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(newValue = onBack)
    val currentOnNavigateLanguage by rememberUpdatedState(newValue = onNavigateLanguage)
    val context = LocalContext.current

    LaunchedEffect(key1 = viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                AboutEffect.NavigateBack -> currentOnBack()
                AboutEffect.NavigateToLanguage -> currentOnNavigateLanguage()
                AboutEffect.ShareApp -> context.shareApp()
                AboutEffect.OpenPlayStore -> openGooglePlay(context = context)
                is AboutEffect.OpenUrl -> context.openUrl(url = context.getString(effect.urlRes))
            }
        }
    }

    AboutContent(
        state = state,
        onIntent = viewModel::onIntent,
    )

    when (state.ratingStage) {
        RatingStage.Rate -> RatingDialog(
            state = state,
            onIntent = viewModel::onIntent,
        )

        RatingStage.Thanks -> RatingThanksDialog(
            onIntent = viewModel::onIntent,
        )

        null -> Unit
    }
}

@Composable
private fun AboutContent(
    state: AboutUiState,
    onIntent: (AboutIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color010300)
            .statusBarsPadding()
            .verticalScroll(state = rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp)
                .size(size = 40.dp)
                .clip(shape = CircleShape)
                .background(color = ColorFFFFFF.copy(alpha = 0.1f))
                .clickableBorderCircle(
                    onClick = {
                        onIntent(AboutIntent.BackClick)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_social_app_back),
                contentDescription = stringResource(id = R.string.social_app_back),
            )
        }

        VerticalGap(height = 16.dp)

        AppInfo(
            versionName = state.versionName,
            modifier = Modifier.fillMaxWidth(),
        )

        VerticalGap(height = 36.dp)

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            state.items.forEach { item ->
                SettingRow(
                    iconRes = item.iconRes,
                    label = stringResource(id = item.labelRes),
                    height = 72.dp,
                    horizontalPadding = 15.5.dp,
                    textStartPadding = 16.dp,
                    onClick = {
                        onIntent(AboutIntent.ItemClick(item = item))
                    },
                )
            }
        }
    }
}

@Composable
private fun AppInfo(
    versionName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(size = 80.dp)
                .clip(shape = RoundedCornerShape(size = 24.dp))
                .background(color = ColorFFFFFF.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_about_app_logo),
                contentDescription = null,
            )
        }

        VerticalGap(height = 16.dp)

        Text(
            text = stringResource(id = R.string.about_app_name),
            style = sfProTextStyle(
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.W600,
            ),
            textAlign = TextAlign.Center,
        )

        VerticalGap(height = 6.dp)

        Text(
            text = stringResource(id = R.string.about_tagline),
            style = sfProTextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = ColorFFFFFF.copy(alpha = 0.5f),
            ),
            textAlign = TextAlign.Center,
        )

        VerticalGap(height = 16.dp)

        Text(
            text = stringResource(id = R.string.about_version, versionName),
            style = sfProTextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = ColorFFEE32,
            ),
            modifier = Modifier
                .clip(shape = RoundedCornerShape(size = 24.dp))
                .background(color = ColorFFEE32.copy(alpha = 0.1f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

private fun Context.shareApp() {
    val link = "https://play.google.com/store/apps/details?id=$packageName"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            getString(R.string.about_share_message, getString(R.string.about_app_name), link)
        )
    }
    runCatching {
        startActivity(Intent.createChooser(intent, getString(R.string.about_share_app)))
    }
}

private fun Context.openUrl(url: String) {
    if (url.isBlank()) {
        return
    }
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
private fun AboutContentPreview() {
    AboutContent(
        state = AboutUiState(versionName = "1.0.0"),
        onIntent = {},
    )
}
