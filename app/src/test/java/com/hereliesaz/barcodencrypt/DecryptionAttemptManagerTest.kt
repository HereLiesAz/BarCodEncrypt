package com.hereliesaz.barcodencrypt

import android.content.Context
import android.content.SharedPreferences
import com.hereliesaz.barcodencrypt.util.DecryptionAttemptManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class DecryptionAttemptManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockPrefs: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var decryptionAttemptManager: DecryptionAttemptManager

    @Before
    fun setup() {
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockPrefs)
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putInt(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.remove(any())).thenReturn(mockEditor)
        decryptionAttemptManager = DecryptionAttemptManager(mockContext)
    }

    @Test
    fun `getRemainingAttempts returns max attempts for new ciphertext`() {
        val ciphertext = "test_ciphertext"
        val maxAttempts = 5
        whenever(mockPrefs.getInt(any(), eq(maxAttempts))).thenReturn(maxAttempts)

        val remaining = decryptionAttemptManager.getRemainingAttempts(ciphertext, maxAttempts)

        assert(remaining == maxAttempts)
    }

    @Test
    fun `getRemainingAttempts returns unlimited for max attempts 0`() {
        val ciphertext = "test_ciphertext"
        val remaining = decryptionAttemptManager.getRemainingAttempts(ciphertext, 0)
        assert(remaining == Int.MAX_VALUE)
    }

    @Test
    fun `recordFailedAttempt decrements attempts`() {
        val ciphertext = "test_ciphertext"
        val maxAttempts = 5
        whenever(mockPrefs.getInt(any(), eq(maxAttempts))).thenReturn(maxAttempts)

        decryptionAttemptManager.recordFailedAttempt(ciphertext, maxAttempts)

        verify(mockEditor).putInt(any(), eq(4))
    }

    @Test
    fun `recordFailedAttempt does nothing for unlimited attempts`() {
        val ciphertext = "test_ciphertext"
        decryptionAttemptManager.recordFailedAttempt(ciphertext, 0)
        verify(mockEditor, org.mockito.kotlin.never()).putInt(any(), any())
    }

    @Test
    fun `resetAttempts removes key from prefs`() {
        val ciphertext = "test_ciphertext"
        decryptionAttemptManager.resetAttempts(ciphertext)
        verify(mockEditor).remove(any())
    }
}
