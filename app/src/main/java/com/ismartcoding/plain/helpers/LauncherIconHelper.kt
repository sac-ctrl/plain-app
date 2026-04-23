package com.ismartcoding.plain.helpers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.ismartcoding.lib.logcat.LogCat

/**
 * Toggles the home-screen / app-drawer icon for PlainApp by
 * enabling or disabling the LauncherAlias activity-alias declared in AndroidManifest.xml.
 *
 * IMPORTANT: This only hides the *icon*. MainActivity, the HTTP server, the Cloudflare tunnel,
 * the watchdog and every background service keep working exactly the same. The web panel
 * stays reachable. The app can still be opened via:
 *   - the web panel "Open app on device" button
 *   - any deep-link / file intent that targets MainActivity
 *   - reopening from Settings > Apps > PlainApp
 */
object LauncherIconHelper {

    private const val ALIAS_CLASS = "com.ismartcoding.plain.LauncherAlias"

    fun isHidden(context: Context): Boolean {
        val pm = context.packageManager
        val component = ComponentName(context.packageName, ALIAS_CLASS)
        val state = pm.getComponentEnabledSetting(component)
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun setHidden(context: Context, hidden: Boolean) {
        val pm = context.packageManager
        val component = ComponentName(context.packageName, ALIAS_CLASS)
        val newState = if (hidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        try {
            pm.setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP)
        } catch (e: Exception) {
            LogCat.e("Failed to toggle launcher icon: ${e.message}")
        }
    }
}
