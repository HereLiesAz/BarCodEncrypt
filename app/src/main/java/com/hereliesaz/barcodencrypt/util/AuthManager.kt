package com.hereliesaz.barcodencrypt.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.hereliesaz.barcodencrypt.R
import kotlinx.coroutines.tasks.await
import java.nio.charset.Charset
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AuthManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {

    private val credentialManager = CredentialManager.create(context)
    private val auth: FirebaseAuth = Firebase.auth
    private val webClientId by lazy {
        context.getString(R.string.web_client_id)
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun getSecretKey(): SecretKey {
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: generateSecretKey()
    }

    private fun generateSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    fun isLoggedIn(): Boolean {
        return sharedPreferences.contains(ENCRYPTED_PASSWORD_KEY) || (auth.currentUser != null)
    }

    fun getGoogleSignInRequest(): GetCredentialRequest {
        val nonce = generateNonce()
        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setNonce(nonce)
            .build()
        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    suspend fun handleSignInResult(result: GetCredentialResponse): GoogleIdTokenCredential? {
        val credential = result.credential
        if (credential is GoogleIdTokenCredential) {
            val googleIdToken = credential.idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            return try {
                auth.signInWithCredential(firebaseCredential).await()
                credential
            } catch (e: Exception) {
                Log.e("AuthManager", "Firebase sign-in failed", e)
                null
            }
        }
        return null
    }

    private fun generateNonce(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.toHexString()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun ByteArray.toHexString() = asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }


    fun setPassword(password: String) {
        val cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryptedPassword = cipher.doFinal(password.toByteArray(Charset.defaultCharset()))
        sharedPreferences.edit()
            .putString(ENCRYPTED_PASSWORD_KEY, Base64.encodeToString(encryptedPassword, Base64.DEFAULT))
            .putString(IV_KEY, Base64.encodeToString(iv, Base64.DEFAULT))
            .apply()
    }

    fun checkPassword(password: String): Boolean {
        val encryptedPasswordString = sharedPreferences.getString(ENCRYPTED_PASSWORD_KEY, null)
        val ivString = sharedPreferences.getString(IV_KEY, null)
        if (encryptedPasswordString != null && ivString != null) {
            try {
                val encryptedPassword = Base64.decode(encryptedPasswordString, Base64.DEFAULT)
                val iv = Base64.decode(ivString, Base64.DEFAULT)
                val cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE)
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
                val decryptedPassword = cipher.doFinal(encryptedPassword)
                return password == String(decryptedPassword, Charset.defaultCharset())
            } catch (e: Exception) {
                // decryption failed
                return false
            }
        }
        return false
    }

    suspend fun logout() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Log error
        }
        auth.signOut()
        sharedPreferences.edit()
            .remove(ENCRYPTED_PASSWORD_KEY)
            .remove(IV_KEY)
            .apply()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "password_key"
        private const val ENCRYPTED_PASSWORD_KEY = "encrypted_password"
        private const val IV_KEY = "iv"
        const val IS_GOOGLE_SIGNED_IN_KEY = "is_google_signed_in"
    }
}