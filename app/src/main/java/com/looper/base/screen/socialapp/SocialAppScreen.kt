package com.looper.base.screen.socialapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looper.base.R
import com.looper.base.screen.home.data.SocialApp
import com.looper.base.screen.socialapp.data.SocialAppEffect
import com.looper.base.screen.socialapp.data.SocialAppIntent
import com.looper.base.screen.socialapp.data.SocialAppUiState
import com.looper.base.ui.theme.Color010300
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.ui.theme.sfProTextStyle
import com.looper.base.utils.clickableBorderCircle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SocialAppScreen(
    onBack: () -> Unit,
    viewModel: SocialAppViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(newValue = onBack)

    LaunchedEffect(key1 = viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SocialAppEffect.NavigateBack -> currentOnBack()
            }
        }
    }

    SocialAppContent(
        state = state,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun SocialAppContent(
    state: SocialAppUiState,
    onIntent: (SocialAppIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color010300)
            .statusBarsPadding(),
    ) {
        SocialAppHeader(
            onBackClick = {
                onIntent(SocialAppIntent.BackClick)
            },
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(count = 3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(weight = 1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
            verticalArrangement = Arrangement.spacedBy(space = 24.dp),
        ) {
            items(
                items = state.apps,
                key = { app ->
                    app.name
                },
            ) { app ->
                SocialAppItem(
                    app = app,
                    onClick = {
                        onIntent(SocialAppIntent.AppClick(app = app))
                    },
                )
            }
        }
    }
}

@Composable
private fun SocialAppHeader(
    onBackClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(alignment = Alignment.CenterStart)
                .size(size = 40.dp)
                .clip(shape = CircleShape)
                .background(color = ColorFFFFFF.copy(alpha = 0.1f))
                .clickableBorderCircle(
                    onClick = onBackClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_social_app_back),
                contentDescription = stringResource(id = R.string.social_app_back),
            )
        }

        Text(
            text = stringResource(id = R.string.social_app_title),
            style = sfProTextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.W600,
            ),
        )
    }
}

@Composable
private fun SocialAppItem(
    app: SocialApp,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(size = 64.dp)
                .clip(shape = CircleShape)
                .background(color = ColorFFFFFF.copy(alpha = 0.05f))
                .clickableBorderCircle(
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = app.iconRes),
                contentDescription = null,
            )
        }

        Text(
            text = stringResource(id = app.labelRes),
            style = sfProTextStyle(
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = ColorFFFFFF.copy(alpha = 0.75f),
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
private fun SocialAppContentPreview() {
    SocialAppContent(
        state = SocialAppUiState(),
        onIntent = {},
    )
}
