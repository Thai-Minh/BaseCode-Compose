package com.looper.base.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looper.base.screen.home.HomeTab
import com.looper.base.screen.main.data.MainIntent
import com.looper.base.screen.main.data.MainTab
import com.looper.base.screen.main.data.MainUiState
import com.looper.base.screen.profile.ProfileTab
import com.looper.base.ui.theme.Color010300
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.utils.clickableBorderCircle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainScreen(
    onNavigateSocialApps: () -> Unit,
    onNavigateAbout: () -> Unit,
    viewModel: MainViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MainContent(
        state = state,
        onIntent = viewModel::onIntent,
        homeTab = {
            HomeTab(
                onNavigateSocialApps = onNavigateSocialApps,
            )
        },
        profileTab = {
            ProfileTab(
                onNavigateAbout = onNavigateAbout,
            )
        },
    )
}

@Composable
private fun MainContent(
    state: MainUiState,
    onIntent: (MainIntent) -> Unit,
    homeTab: @Composable () -> Unit,
    profileTab: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color010300)
            .statusBarsPadding(),
    ) {
        MainTabBar(
            tabs = state.tabs,
            selectedTab = state.selectedTab,
            onTabClick = { tab ->
                onIntent(MainIntent.SelectTab(tab = tab))
            },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(weight = 1f),
        ) {
            when (state.selectedTab) {
                MainTab.Home -> homeTab()
                MainTab.Profile -> profileTab()
                MainTab.Library,
                MainTab.History,
                MainTab.Search -> Unit
            }
        }
    }
}

@Composable
private fun MainTabBar(
    tabs: List<MainTab>,
    selectedTab: MainTab,
    onTabClick: (MainTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        tabs.forEach { tab ->
            Box(
                modifier = Modifier
                    .size(size = 48.dp)
                    .clickableBorderCircle(
                        onClick = {
                            onTabClick(tab)
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = tab.iconRes),
                    contentDescription = stringResource(id = tab.labelRes),
                    tint = if (tab == selectedTab) {
                        ColorFFFFFF
                    } else {
                        ColorFFFFFF.copy(alpha = 0.4f)
                    },
                    modifier = Modifier.size(size = 24.dp),
                )
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
private fun MainContentPreview() {
    MainContent(
        state = MainUiState(),
        onIntent = {},
        homeTab = {},
        profileTab = {},
    )
}
