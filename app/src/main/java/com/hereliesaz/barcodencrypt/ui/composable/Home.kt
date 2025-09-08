package com.hereliesaz.barcodencrypt.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hereliesaz.barcodencrypt.R
import com.hereliesaz.aznavrail.AzNavRail

@Composable
fun Home(
    onManageContactKeys: () -> Unit,
    onComposeMessage: () -> Unit
) {
    val manageKeysText = stringResource(id = R.string.manage_contact_keys)
    val composeText = stringResource(id = R.string.compose_message)

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
    }
}
