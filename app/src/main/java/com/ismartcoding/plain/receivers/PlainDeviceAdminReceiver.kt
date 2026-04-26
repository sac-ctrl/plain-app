package com.ismartcoding.plain.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.R
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
 */
class PlainDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        LogCat.d("PlainDeviceAdminReceiver enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        LogCat.d("PlainDeviceAdminReceiver disabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
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
