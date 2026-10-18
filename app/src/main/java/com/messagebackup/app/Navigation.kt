package com.messagebackup.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messagebackup.app.ui.main.MainScreen
import com.messagebackup.app.ui.main.MainViewModel

/**
 * Top-level application navigation entry point.
 */
@Composable
fun MainNavigation(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    MainScreen(
        viewModel = viewModel,
        modifier = modifier
    )
}
