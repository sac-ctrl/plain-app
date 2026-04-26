package com.ismartcoding.plain.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.R
import com.ismartcoding.plain.helpers.DeviceAdminGuard
import com.ismartcoding.plain.ui.DeviceAdminUnlockActivity

/**
 * Device Admin receiver. Once activated by the user (Settings > Security >
 * Device admin apps), Android raises this app's process priority and prevents
 * casual uninstall — both of which contribute to keep-alive on aggressive
 * OEM ROMs (Xiaomi/Realme/Oppo/Vivo/Samsung).
 *
 * In addition, when the user attempts to deactivate Device Admin from system
 * Settings, [onDisableRequested] is fired *before* the system shows its
 * confirmation dialog. We use that hook to launch [DeviceAdminUnlockActivity]
 * which forces the user to enter the in-app PIN (and biometric if enabled)
 * before they can proceed.
 *
 * Because the OS owns the deactivation dialog, the user can still tap "OK"
 * after dismissing or failing the PIN prompt and the OS will call
 * [onDisabled]. To make the PIN gate effective we cooperate with
 * [DeviceAdminGuard]: the unlock activity records a "pin verified" timestamp
 * on success, and `onDisabled` checks it. If the timestamp is missing or
 * stale (no successful PIN within the last minute), we immediately fire
 * the system add-admin intent, re-activating the receiver.
 */
class PlainDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        LogCat.d("PlainDeviceAdminReceiver enabled")
        DeviceAdminGuard.clear(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        LogCat.d("PlainDeviceAdminReceiver disabled")
        val verified = DeviceAdminGuard.isRecentlyVerified(context)
        // Always consume the flag once we've checked it so a single PIN entry
        // can't keep authorising future deactivations.
        DeviceAdminGuard.clear(context)
        if (!verified) {
            LogCat.d("PlainDeviceAdminReceiver disabled WITHOUT PIN — forcing reactivation")
            DeviceAdminGuard.requestReactivation(context)
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Wipe any prior PIN approval — every deactivation attempt must pass a
        // fresh PIN check.
        DeviceAdminGuard.clear(context)
        try {
            val unlock = Intent(context, DeviceAdminUnlockActivity::class.java)
            unlock.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            context.startActivity(unlock)
        } catch (e: Throwable) {
            LogCat.e("Failed to launch DeviceAdminUnlockActivity: ${e.message}")
        }
        return context.getString(R.string.device_admin_disable_warning)
    }
}
