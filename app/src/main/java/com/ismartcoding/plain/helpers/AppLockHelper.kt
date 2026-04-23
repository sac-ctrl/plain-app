package com.ismartcoding.plain.helpers

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/**
 * Helpers around the optional app-open lock (PIN + biometric).
 *
 * The lock is purely a UI gate inside MainActivity. It does NOT affect:
 *   - the HTTP server / web panel
 *   - the Cloudflare tunnel
 *   - any foreground or background service
 *   - boot / watchdog flows
 */
object AppLockHelper {

    /** True when the device has biometric hardware AND the user has enrolled at least one credential. */
    fun isBiometricAvailable(context: Context): Boolean {
        return try {
            val mgr = BiometricManager.from(context)
            mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Throwable) {
            false
        }
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeText: String,
        onSuccess: () -> Unit,
        onFallback: () -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFallback()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        try {
            prompt.authenticate(info)
        } catch (_: Throwable) {
            onFallback()
        }
    }
}
