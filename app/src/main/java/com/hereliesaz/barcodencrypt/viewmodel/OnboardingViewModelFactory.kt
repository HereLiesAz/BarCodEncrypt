package com.hereliesaz.barcodencrypt.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hereliesaz.barcodencrypt.BarcodeApplication

class OnboardingViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            // Get the singleton instance from the Application class
            val authManager = (application as BarcodeApplication).authManager
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(authManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}