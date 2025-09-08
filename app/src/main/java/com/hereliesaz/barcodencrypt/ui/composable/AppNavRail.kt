package com.hereliesaz.barcodencrypt.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hereliesaz.barcodencrypt.R
import com.hereliesaz.aznavrail.AzNavRail

@Composable
fun AppNavRail(
    onManageContactKeys: () -> Unit,
    onComposeMessage: () -> Unit,
    onSettings: () -> Unit // Added settings callback
) {
    val manageKeysText = stringResource(id = R.string.manage_contact_keys)
    val composeText = stringResource(id = R.string.compose_message)
    val settingsText = stringResource(id = R.string.settings) // Added settings text resource

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
            id = "settings", // Added settings item
            text = settingsText,
            onClick = onSettings
        )
    }
}
