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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Barcodencrypt")
        Spacer(modifier = Modifier.height(32.dp))
//        Button(onClick = { onboardingViewModel.onSignInWithGoogleClicked() }) {
//            Text("Sign in with Google")
//        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { showPasswordDialog = true }) {
            Text("Set a password")
        }
    }
}
