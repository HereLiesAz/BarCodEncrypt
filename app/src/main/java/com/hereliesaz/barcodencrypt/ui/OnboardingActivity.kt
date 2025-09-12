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
import com.hereliesaz.barcodencrypt.util.LogConfig
import com.hereliesaz.barcodencrypt.viewmodel.OnboardingViewModel
import com.hereliesaz.barcodencrypt.viewmodel.OnboardingViewModelFactory
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {
    private val TAG = "OnboardingActivity"

    private val onboardingViewModel: OnboardingViewModel by viewModels {
        OnboardingViewModelFactory(application)
    }

    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (LogConfig.LIFECYCLE_ONBOARDING_ACTIVITY) Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContent {
            BarcodencryptTheme {
                OnboardingScreen(onboardingViewModel)
            }
        }

        lifecycleScope.launch {
            onboardingViewModel.signInRequest.collect { request ->
                try {
                    if (LogConfig.AUTH_FLOW) Log.d(TAG, "signInRequest collected. Calling credentialManager.getCredential...")
                    val result = credentialManager.getCredential(this@OnboardingActivity, request)
                    if (LogConfig.AUTH_FLOW) Log.d(TAG, "credentialManager.getCredential SUCCEEDED.")
                    onboardingViewModel.handleSignInResult(result)
                } catch (e: NoCredentialException) {
                    if (LogConfig.AUTH_FLOW) Log.e(TAG, "credentialManager.getCredential FAILED: No credentials found.", e)
                    onboardingViewModel.onNoCredentialsFound()
                    runOnUiThread {
                        Toast.makeText(this@OnboardingActivity, "No Google accounts found. Please set a password.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: GetCredentialException) {
                    if (LogConfig.AUTH_FLOW) Log.e(TAG, "credentialManager.getCredential FAILED: GetCredentialException.", e)
                    onboardingViewModel.onSignInError()
                    runOnUiThread {
                        Toast.makeText(this@OnboardingActivity, "Sign-in failed. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            onboardingViewModel.signInResult.collect { credential: GoogleIdTokenCredential? ->
                if (credential != null) {
                    if (LogConfig.AUTH_FLOW) Log.d(TAG, "signInResult collected: SUCCESS. Starting MainActivity.")
                    val intent = Intent(this@OnboardingActivity, MainActivity::class.java)
                    intent.putExtra("FROM_ONBOARDING", true)
                    startActivity(intent)
                    finish()
                } else {
                    if (LogConfig.AUTH_FLOW) Log.w(TAG, "signInResult collected: FAILED (credential was null).")
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (LogConfig.LIFECYCLE_ONBOARDING_ACTIVITY) Log.d(TAG, "onStart")
    }

    override fun onResume() {
        super.onResume()
        if (LogConfig.LIFECYCLE_ONBOARDING_ACTIVITY) Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        if (LogConfig.LIFECYCLE_ONBOARDING_ACTIVITY) Log.d(TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
        if (LogConfig.LIFECYCLE_ONBOARDING_ACTIVITY) Log.d(TAG, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (LogConfig.LIFECYCLE_ONBOARDING_ACTIVITY) Log.d(TAG, "onDestroy")
    }
}