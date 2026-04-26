package com.ismartcoding.plain.helpers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.R
import com.ismartcoding.plain.receivers.PlainDeviceAdminReceiver
import java.io.File

/**
 * Tracks whether the user has just passed the in-app PIN check that protects
 * Device Admin deactivation.
 *
 * Background: Android's `Settings > Security > Device admin apps` deactivation
 * dialog is fully owned by the OS. Returning a string from
 * [android.app.admin.DeviceAdminReceiver.onDisableRequested] only changes the
 * warning text; tapping the system "OK" still calls
 * [android.app.admin.DeviceAdminReceiver.onDisabled] regardless of what we
 * launched on top of it. So even if the user enters the wrong PIN (or never
 * touches the PIN screen), the OS still strips us of admin if they tap OK.
 *
 * To turn that into a real PIN gate we persist a "last verified" timestamp
 * when the user passes the PIN, and consult it inside `onDisabled`. If the
 * timestamp is missing or stale, we immediately fire
 * [DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN] so the user is bounced back
 * to the activate-admin screen — making any unauthorised deactivation a
 * one-click no-op.
 */
object DeviceAdminGuard {
    private const val FLAG_FILE = ".device_admin_pin_verified_at"
    private const val VALID_WINDOW_MS = 60_000L

    private fun flagFile(ctx: Context): File = File(ctx.filesDir, FLAG_FILE)

    fun markVerified(ctx: Context) {
        runCatching {
            flagFile(ctx).writeText(System.currentTimeMillis().toString())
        }
    }

    fun clear(ctx: Context) {
        runCatching { flagFile(ctx).delete() }
    }

    fun isRecentlyVerified(ctx: Context): Boolean {
        val f = flagFile(ctx)
        if (!f.exists()) return false
        val ts = runCatching { f.readText().trim().toLong() }.getOrNull() ?: return false
        return System.currentTimeMillis() - ts in 0..VALID_WINDOW_MS
    }

    /**
     * Force re-activation by launching the system add-admin screen. Used when
     * the OS has already disabled us without a valid PIN verification.
     */
    fun requestReactivation(ctx: Context) {
        try {
            val cn = ComponentName(ctx, PlainDeviceAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    ctx.getString(R.string.device_admin_unauthorized_deactivate),
                )
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            ctx.startActivity(intent)
        } catch (t: Throwable) {
            LogCat.e("DeviceAdminGuard requestReactivation failed: ${t.message}")
        }
    }

    fun isAdminActive(ctx: Context = MainApp.instance): Boolean {
        return try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                ?: return false
            val cn = ComponentName(ctx, PlainDeviceAdminReceiver::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.isAdminActive(cn)
            } else {
                dpm.isAdminActive(cn)
            }
        } catch (_: Throwable) {
            false
        }
    }
}
