package com.hereliesaz.barcodencrypt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hereliesaz.barcodencrypt.viewmodel.OnboardingViewModel

@Composable
fun OnboardingScreen(
    onboardingViewModel: OnboardingViewModel
) {
    var showPasswordDialog by remember { mutableStateOf(false) }

    if (showPasswordDialog) {
        PasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { password ->
                onboardingViewModel.onSetPasswordClicked(password)
                showPasswordDialog = false
            }
        )
    }

    val signInError by onboardingViewModel.signInError.collectAsState()
    val noCredentialsFound by onboardingViewModel.noCredentialsFound.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Barcodencrypt", color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onboardingViewModel.onSignInWithGoogleClicked() },
            enabled = !noCredentialsFound
        ) {
            Text(if (signInError) "Retry Sign-in" else "Sign in with Google")
        }

        if (noCredentialsFound) {
            Text(
                "No Google accounts found. Please set a password to continue.",
                modifier = Modifier.padding(top = 8.dp),
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { showPasswordDialog = true }) {
            Text("Enter Password")
        }
    }
}
