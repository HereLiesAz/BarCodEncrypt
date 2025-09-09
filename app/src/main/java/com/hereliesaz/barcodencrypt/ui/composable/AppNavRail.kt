package com.hereliesaz.barcodencrypt.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hereliesaz.barcodencrypt.R
import com.hereliesaz.aznavrail.AzNavRail

@Composable
fun AppNavRail(
    onManageContactKeys: () -> Unit,
    onComposeMessage: () -> Unit,
    onSettings: () -> Unit,
    onNavigateToTryMe: () -> Unit,
) {
    val manageKeysText = stringResource(id = R.string.manage_contact_keys)
    val composeText = stringResource(id = R.string.compose_message)
    val settingsText = stringResource(id = R.string.settings)
    val tryMeText = stringResource(id = R.string.try_me)

    AzNavRail {
        azRailItem(
            id = "manage_keys",
            text = manageKeysText,
            onClick = onManageContactKeys
        )
        azRailItem(
            id = "compose",
            text = composeText,
            onClick = onComposeMessage
        )
        azRailItem(
            id = "settings",
            text = settingsText,
            onClick = onSettings
        )
        azRailItem(
            id = "try_me",
            text = tryMeText,
            onClick = onNavigateToTryMe
        )
    }
}
