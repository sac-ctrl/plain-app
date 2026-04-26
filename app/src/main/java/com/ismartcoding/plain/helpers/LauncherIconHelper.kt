package com.ismartcoding.plain.helpers

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.ismartcoding.lib.logcat.LogCat

object LauncherIconHelper {

    private const val ALIAS_CLASS = "com.ismartcoding.plain.LauncherAlias"

    private val KNOWN_LAUNCHER_PACKAGES = listOf(
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.mi.android.globallauncher",
        "com.realme.launcher",
        "com.oppo.launcher",
        "com.bbk.launcher2",
        "com.vivo.launcher",
        "com.huawei.android.launcher",
        "com.honor.launcher",
        "com.transsion.hilauncher",
        "com.transsion.XOSLauncher",
        "net.oneplus.launcher",
        "com.oneplus.launcher",
        "com.asus.launcher",
        "com.motorola.launcher3",
        "com.motorola.personalize",
        "com.lge.launcher2",
        "com.lge.launcher3",
        "com.tcl.launcher",
        "com.teslacoilsw.launcher",
        "ch.deletescape.lawnchair.plah",
        "com.actionlauncher.playstore",
        "com.microsoft.launcher",
    )

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
        try {
            forceLauncherRefresh(context)
        } catch (e: Exception) {
            LogCat.e("Failed to refresh launcher: ${e.message}")
        }
    }

    /**
     * Many OEM home-screen apps (Samsung One UI, MIUI, ColorOS, ColorOS-derived, FuntouchOS,
     * EMUI, MagicUI, OneUI, etc.) keep a cached copy of every app icon in their drawer
     * even after the launcher activity has been disabled in PackageManager. The result is a
     * "ghost" icon that still appears, but tapping it opens system "App info" instead of the
     * app, because Android no longer has a real launcher target.
     *
     * Killing the launcher's background process forces it to reload its app list on next
     * launch and the ghost icon disappears. Requires [android.Manifest.permission.KILL_BACKGROUND_PROCESSES]
     * (a normal permission, granted at install time).
     */
    private fun forceLauncherRefresh(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val resolved = mutableSetOf<String>()
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolvers = context.packageManager.queryIntentActivities(home, 0)
            for (r in resolvers) {
                resolved.add(r.activityInfo.packageName)
            }
        } catch (_: Throwable) {
        }
        val toKill = (resolved + KNOWN_LAUNCHER_PACKAGES).distinct()
        for (pkg in toKill) {
            if (pkg == context.packageName) continue
            try {
                am.killBackgroundProcesses(pkg)
            } catch (_: Throwable) {
            }
        }
    }
}
