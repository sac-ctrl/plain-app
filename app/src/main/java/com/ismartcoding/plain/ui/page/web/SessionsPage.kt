package com.ismartcoding.plain.ui.page.web

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ismartcoding.plain.R
import com.ismartcoding.plain.ui.base.NoDataColumn
import com.ismartcoding.plain.ui.base.PScaffold
import com.ismartcoding.plain.ui.base.PTopAppBar
import com.ismartcoding.plain.ui.base.pullrefresh.PullToRefresh
import com.ismartcoding.plain.ui.base.pullrefresh.RefreshContentState
import com.ismartcoding.plain.ui.base.pullrefresh.setRefreshState
import com.ismartcoding.plain.ui.base.pullrefresh.rememberRefreshLayoutState
import com.ismartcoding.plain.ui.models.SessionsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionsPage(
    navController: NavHostController,
    sessionsVM: SessionsViewModel = viewModel(),
) {
    val refreshState =
        rememberRefreshLayoutState {
            setRefreshState(RefreshContentState.Finished)
        }

    PScaffold(
        topBar = {
            PTopAppBar(
                navController = navController,
                title = stringResource(id = R.string.sessions),
            )
        },
        content = { paddingValues ->
            PullToRefresh(modifier = Modifier.padding(top = paddingValues.calculateTopPadding()), refreshLayoutState = refreshState) {
                NoDataColumn()
            }
        },
    )
}
