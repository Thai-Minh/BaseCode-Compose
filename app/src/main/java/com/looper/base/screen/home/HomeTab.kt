package com.looper.base.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looper.base.R
import com.looper.base.screen.home.data.HomeEffect
import com.looper.base.screen.home.data.HomeIntent
import com.looper.base.screen.home.data.HomeUiState
import com.looper.base.ui.theme.Color010300
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.ui.theme.sfProTextStyle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeTab(
    onNavigateSocialApps: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnNavigateSocialApps by rememberUpdatedState(newValue = onNavigateSocialApps)

    LaunchedEffect(key1 = viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                HomeEffect.NavigateToSocialApps -> currentOnNavigateSocialApps()
                is HomeEffect.ShowLinkError -> Unit
            }
        }
    }

    HomeContent(
        state = state,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(state = rememberScrollState())
            .navigationBarsPadding()
            .padding(paddingValues = PaddingValues(bottom = 24.dp)),
    ) {
        HomeTitle(
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(modifier = Modifier.height(height = 20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(space = 10.dp),
        ) {
            HeroCard(
                link = state.link,
                onLinkChange = { link ->
                    onIntent(HomeIntent.LinkChange(link = link))
                },
                onLinkClear = {
                    onIntent(HomeIntent.LinkClear)
                },
                onLinkSubmit = {
                    onIntent(HomeIntent.LinkSubmit)
                },
            )

            state.nowPlaying?.let { nowPlaying ->
                NowPlayingCard(
                    nowPlaying = nowPlaying,
                    onIntent = onIntent,
                )
            }

            if (state.socialApps.isNotEmpty()) {
                SocialAppsCard(
                    apps = state.socialApps,
                    onIntent = onIntent,
                )
            }

            if (state.playlists.isNotEmpty()) {
                FavoritePlaylistCard(
                    playlists = state.playlists,
                    onIntent = onIntent,
                )
            }

            if (state.recentDownloads.isNotEmpty()) {
                RecentDownloadsCard(
                    downloads = state.recentDownloads,
                    onIntent = onIntent,
                )
            }
        }
    }
}

@Composable
private fun HomeTitle(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = (-2).dp),
    ) {
        Text(
            text = stringResource(id = R.string.home_title_music),
            style = sfProTextStyle(
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.W600,
            ),
        )
        Text(
            text = stringResource(id = R.string.home_title_downloader),
            style = sfProTextStyle(
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.W600,
                color = ColorFFFFFF.copy(alpha = 0.5f),
            ),
        )
    }
}

@Preview(widthDp = 360, heightDp = 1500)
@Composable
private fun HomeContentPreview() {
    HomeContent(
        state = HomeUiState(),
        onIntent = {},
        modifier = Modifier.background(color = Color010300),
    )
}
