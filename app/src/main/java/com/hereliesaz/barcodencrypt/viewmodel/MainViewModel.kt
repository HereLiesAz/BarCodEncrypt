package com.hereliesaz.barcodencrypt.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.hereliesaz.barcodencrypt.util.AuthManager
import com.hereliesaz.barcodencrypt.util.LogConfig
import kotlinx.coroutines.launch

class MainViewModel(private val authManager: AuthManager) : ViewModel() {
    private val TAG = "MainViewModel"

    val serviceStatus = MutableLiveData<Boolean>()
    val notificationPermissionStatus = MutableLiveData<Boolean>()
    val contactsPermissionStatus = MutableLiveData<Boolean>()
    val overlayPermissionStatus = MutableLiveData<Boolean>()
    val isLoggedIn = MutableLiveData<Boolean?>(null) // null represents the initial loading state
    val passwordCorrect = MutableLiveData<Boolean>()

    init {
        if (LogConfig.LIFECYCLE_VIEWMODEL) Log.d(TAG, "init: MainViewModel created.")
        viewModelScope.launch {
            authManager.user.collect { firebaseUser: FirebaseUser? ->
                val loggedIn = firebaseUser != null || authManager.hasLocalPassword()
                if (LogConfig.AUTH_FLOW) Log.d(TAG, "Auth state collected. User: ${firebaseUser?.uid}. Has local pass: ${authManager.hasLocalPassword()}. Setting isLoggedIn to: $loggedIn")
                if (isLoggedIn.value != loggedIn) {
                    isLoggedIn.value = loggedIn
                }
            }
        }
    }

    fun checkPassword(password: String) {
        passwordCorrect.value = authManager.checkPassword(password)
    }

    override fun onCleared() {
        super.onCleared()
        if (LogConfig.LIFECYCLE_VIEWMODEL) Log.d(TAG, "onCleared: MainViewModel destroyed.")
    }
}