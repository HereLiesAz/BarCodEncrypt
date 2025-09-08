package com.hereliesaz.barcodencrypt.services

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.hereliesaz.barcodencrypt.R
import com.hereliesaz.barcodencrypt.ui.AutofillScannerTrampolineActivity
import java.util.ArrayDeque
import android.app.assist.AssistStructure
import android.view.autofill.AutofillId

@RequiresApi(Build.VERSION_CODES.O)
class BarcodeAutofillService : AutofillService() {

    companion object {
        const val EXTRA_AUTOFILL_ID = "com.hereliesaz.barcodencrypt.EXTRA_AUTOFILL_ID"
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: android.os.CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.last().structure
        val passwordNodes = findPasswordNodes(structure)

        if (passwordNodes.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val passwordNode = passwordNodes.first()
        val autofillId = passwordNode.autofillId ?: return

        val remoteViews = RemoteViews(packageName, R.layout.autofill_suggestion).apply {
            setTextViewText(R.id.suggestion_text, "Fill with Barcode")
        }

        val intent = Intent(this, AutofillScannerTrampolineActivity::class.java).apply {
            putExtra(EXTRA_AUTOFILL_ID, autofillId)
        }

        val intentSender = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
        ).intentSender

        // Dataset is for authentication, no separate presentation needed for the dataset itself here.
        val dataset = Dataset.Builder()
            .setAuthentication(intentSender)
            .build()

        // The remoteViews for the suggestion is set on the FillResponse
        val fillResponse = FillResponse.Builder()
            .setPresentation(remoteViews) // Correctly set presentation on FillResponse.Builder
            .addDataset(dataset)
            .build()

        callback.onSuccess(fillResponse)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private fun findPasswordNodes(structure: AssistStructure): List<AssistStructure.ViewNode> {
        val passwordNodes = mutableListOf<AssistStructure.ViewNode>()
        val queue = ArrayDeque<AssistStructure.ViewNode>()

        for (i in 0 until structure.windowNodeCount) {
            queue.add(structure.getWindowNodeAt(i).rootViewNode)
        }

        while (queue.isNotEmpty()) {
            val node = queue.remove()
            if (isPasswordNode(node)) {
                passwordNodes.add(node)
            }
            for (i in 0 until node.childCount) {
                queue.add(node.getChildAt(i))
            }
        }
        return passwordNodes
    }

    private fun isPasswordNode(node: AssistStructure.ViewNode): Boolean {
        val hints = node.autofillHints
        if (hints != null) {
            for (hint in hints) {
                if (hint.contains("password", ignoreCase = true)) {
                    return true
                }
            }
        }

        val inputType = node.inputType
        if (inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
            return true
        }

        return false
    }
}
