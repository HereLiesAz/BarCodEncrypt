package com.hereliesaz.barcodencrypt.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.hereliesaz.barcodencrypt.MainActivity
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.viewmodel.OnboardingViewModel
import com.hereliesaz.barcodencrypt.viewmodel.OnboardingViewModelFactory
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {
    private val onboardingViewModel: OnboardingViewModel by viewModels {
        OnboardingViewModelFactory(applicationContext)
    }

    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BarcodencryptTheme {
                OnboardingScreen(onboardingViewModel)
            }
        }

        lifecycleScope.launch {
            onboardingViewModel.signInRequest.collect { request ->
                try {
                    val result = credentialManager.getCredential(this@OnboardingActivity, request)
                    onboardingViewModel.handleSignInResult(result)
                } catch (e: GetCredentialException) {
                    Log.e("OnboardingActivity", "GetCredentialException", e)
                }
            }
        }

        lifecycleScope.launch {
            onboardingViewModel.signInResult.collect { credential ->
                if (credential != null) {
                    onboardingViewModel.onGoogleSignInSuccess()
                    startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
                    finish()
                } else {
                    // TODO: Handle failed sign in
                }
            }
        }
    }
}
