package com.hereliesaz.barcodencrypt.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffoldWithNavRail(
    modifier: Modifier = Modifier,
    onNavigateToManageKeys: () -> Unit,
    onNavigateToCompose: () -> Unit,
    onNavigateToSettings: () -> Unit,
    floatingActionButton: @Composable () -> Unit = {},
    screenContent: @Composable () -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = floatingActionButton
    ) { scaffoldPadding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            AppNavRail(
                onManageContactKeys = onNavigateToManageKeys,
                onComposeMessage = onNavigateToCompose,
                onSettings = onNavigateToSettings
            )
            Box(
                modifier = Modifier
                    .weight(1f)
            ) {
                screenContent()
            }
        }
    }
}
