package com.hereliesaz.barcodencrypt.viewmodel

import android.app.Application
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _cameraProviderLiveData = MutableLiveData<ProcessCameraProvider>()
    val cameraProviderLiveData: LiveData<ProcessCameraProvider> = _cameraProviderLiveData

    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    val previewUseCase: Preview = Preview.Builder().build()
    val imageAnalysisUseCase: ImageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setTargetResolution(android.util.Size(1280, 720))
        .build()

    init {
        cameraProviderFuture = ProcessCameraProvider.getInstance(application)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            _cameraProviderLiveData.postValue(provider)
        }, ContextCompat.getMainExecutor(application))
    }

    fun bindUseCases(cameraProvider: ProcessCameraProvider, lifecycleOwner: LifecycleOwner) {
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                previewUseCase,
                imageAnalysisUseCase
            )
        } catch (exc: Exception) {
            Log.e("CameraViewModel", "Use case binding failed", exc)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
    }
}

class CameraViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CameraViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
