package com.hereliesaz.barcodencrypt.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.hereliesaz.barcodencrypt.util.AuthManager

class MainViewModel(private val authManager: AuthManager) : ViewModel() {
    val serviceStatus = MutableLiveData<Boolean>()
    val notificationPermissionStatus = MutableLiveData<Boolean>()
    val contactsPermissionStatus = MutableLiveData<Boolean>()
    val overlayPermissionStatus = MutableLiveData<Boolean>()
    val isLoggedIn = MutableLiveData<Boolean>()
    val passwordCorrect = MutableLiveData<Boolean>()

    fun checkLoginStatus() {
        Log.d("MainViewModel", "checkLoginStatus")
        isLoggedIn.value = authManager.isLoggedIn()
    }

    fun checkPassword(password: String) {
        passwordCorrect.value = authManager.checkPassword(password)
    }
}