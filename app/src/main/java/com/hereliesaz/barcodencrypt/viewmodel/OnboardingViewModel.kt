package com.hereliesaz.barcodencrypt.viewmodel

import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.hereliesaz.barcodencrypt.util.AuthManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(private val authManager: AuthManager) : ViewModel() {

    private val _signInRequest = MutableSharedFlow<GetCredentialRequest>()
    val signInRequest: SharedFlow<GetCredentialRequest> = _signInRequest.asSharedFlow()

    private val _signInResult = MutableSharedFlow<GoogleIdTokenCredential?>()
    val signInResult: SharedFlow<GoogleIdTokenCredential?> = _signInResult.asSharedFlow()

    private val _signInError = MutableStateFlow(false)
    val signInError: StateFlow<Boolean> = _signInError.asStateFlow()

    private val _noCredentialsFound = MutableStateFlow(false)
    val noCredentialsFound: StateFlow<Boolean> = _noCredentialsFound.asStateFlow()

    fun onSignInWithGoogleClicked() {
        _signInError.value = false
        _noCredentialsFound.value = false
        viewModelScope.launch {
            _signInRequest.emit(authManager.getGoogleSignInRequest())
        }
    }

    fun onSignInError() {
        _signInError.value = true
    }

    fun onNoCredentialsFound() {
        _noCredentialsFound.value = true
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
}
