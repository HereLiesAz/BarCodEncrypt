package com.hereliesaz.barcodencrypt.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import android.widget.Toast
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
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
                } catch (e: NoCredentialException) {
                    Log.e("OnboardingActivity", "No credentials found.", e)
                    onboardingViewModel.onNoCredentialsFound()
                    runOnUiThread {
                        Toast.makeText(this@OnboardingActivity, "No Google accounts found. Please set a password.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: GetCredentialException) {
                    Log.e("OnboardingActivity", "GetCredentialException", e)
                    onboardingViewModel.onSignInError()
                    runOnUiThread {
                        Toast.makeText(this@OnboardingActivity, "Sign-in failed. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            onboardingViewModel.signInResult.collect { credential: GoogleIdTokenCredential? -> // Specified type
                if (credential != null) {
                    val intent = Intent(this@OnboardingActivity, MainActivity::class.java)
                    intent.putExtra("FROM_ONBOARDING", true)
                    startActivity(intent)
                    finish()
                } else {
                    // TODO: Handle failed sign in
                }
            }
        }
    }
}
