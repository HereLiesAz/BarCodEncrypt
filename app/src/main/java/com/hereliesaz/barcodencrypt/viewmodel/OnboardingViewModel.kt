package com.hereliesaz.barcodencrypt.viewmodel

import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.playservices.auth.GoogleIdTokenCredential
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.util.AuthManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(private val authManager: AuthManager) : ViewModel() {

    private val _signInRequest = MutableSharedFlow<GetCredentialRequest>()
    val signInRequest = _signInRequest.asSharedFlow()

    private val _signInResult = MutableSharedFlow<GoogleIdTokenCredential?>()
    val signInResult = _signInResult.asSharedFlow()

    fun onSignInWithGoogleClicked() {
        viewModelScope.launch {
            _signInRequest.emit(authManager.getGoogleSignInRequest())
        }
    }

    fun handleSignInResult(result: GetCredentialResponse) {
        viewModelScope.launch {
            val credential = authManager.handleSignInResult(result)
            _signInResult.emit(credential)
        }
    }

    fun onSetPasswordClicked(password: String) {
        authManager.setPassword(password)
    }

    fun onGoogleSignInSuccess() {
        authManager.setGoogleSignInSuccess()
    }
}
