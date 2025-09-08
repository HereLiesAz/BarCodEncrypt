package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.util.AuthManager
import kotlinx.coroutines.launch

class SettingsViewModel(private val authManager: AuthManager) : ViewModel() {

    fun logout() {
        viewModelScope.launch {
            authManager.logout()
        }
    }

    fun setPassword(password: String) {
        authManager.setPassword(password)
    }
}
