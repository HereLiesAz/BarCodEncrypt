package com.hereliesaz.barcodencrypt.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hereliesaz.barcodencrypt.R
import com.hereliesaz.aznavrail.AzNavRail

@Composable
fun Home(
    onManageContactKeys: () -> Unit,
    onComposeMessage: () -> Unit,
    onAbout: () -> Unit
) {
    val manageKeysText = stringResource(id = R.string.manage_contact_keys)
    val composeText = stringResource(id = R.string.compose_message)
    val aboutText = stringResource(id = R.string.about)

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
            id = "about",
            text = aboutText,
            onClick = onAbout
        )
    }
}
