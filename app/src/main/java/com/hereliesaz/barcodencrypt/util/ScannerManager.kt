package com.hereliesaz.barcodencrypt.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The Central Nerve Ganglion.
 * A singleton object that decouples the request for a barcode scan from the Activity
 * that must perform it. This prevents the spontaneous growth of parasitic structures
 * like the ScannerTrampolineActivity.
 */
object ScannerManager {

    // A SharedFlow is used to broadcast scan requests to any listening collector.
    private val _requests = MutableSharedFlow<Unit>()
    val requests = _requests.asSharedFlow()

    // A simple callback to deliver the result back to the waiting process.
    private var resultCallback: ((String?) -> Unit)? = null

    /**
     * A component (like an OverlayService) calls this to initiate a scan.
     * It posts to the flow and provides a callback to receive the result.
     *
     * @param onResult The function to execute when the scanner returns a result.
     */
    suspend fun requestScan(onResult: (String?) -> Unit) {
        resultCallback = onResult
        _requests.emit(Unit)
    }

    /**
     * The Activity (the primary somatic structure) calls this to deliver the scan result.
     * It invokes the stored callback.
     *
     * @param result The scanned barcode value, or null if cancelled.
     */
    fun onScanResult(result: String?) {
        resultCallback?.invoke(result)
        resultCallback = null // Clear the callback to prevent stale references.
    }
}
