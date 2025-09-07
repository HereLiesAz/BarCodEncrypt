package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * The ViewModel for the Hierophant ([com.hereliesaz.barcodencrypt.MainActivity]).
 * Its concerns have shifted from managing lists to managing permissions and states
 * for the main dashboard.
 */
class MainViewModel : ViewModel() {
    /**
     * A LiveData to hold the state of the accessibility service.
     */
    val serviceStatus = MutableLiveData<Boolean>()

    /**
     * A LiveData to hold the state of the notification permission. (Kept for potential future use).
     */
    val notificationPermissionStatus = MutableLiveData<Boolean>()

    /**
     * A LiveData to hold the state of the contacts read permission.
     */
    val contactsPermissionStatus = MutableLiveData<Boolean>()

    /**
     * A LiveData to hold the state of the draw over other apps permission.
     */
    val overlayPermissionStatus = MutableLiveData<Boolean>()
}