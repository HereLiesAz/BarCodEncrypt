package com.hereliesaz.barcodencrypt.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hereliesaz.barcodencrypt.util.AuthManager

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val sharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(AuthManager(context.applicationContext, sharedPreferences)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
