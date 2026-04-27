package com.ismartcoding.plain.helpers

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.R

object LauncherIconHelper {

    private const val DEFAULT_ALIAS = "com.ismartcoding.plain.LauncherAlias"

    /**
     * Built-in launcher themes. Each entry corresponds to an <activity-alias> in
     * AndroidManifest.xml. Only one alias may be enabled at a time.
     *
     * To add a new theme:
     *   1. Add an <activity-alias> in AndroidManifest.xml with android:enabled="false",
     *      its own android:icon (mipmap) and android:label (string).
     *   2. Add a matching Theme entry below.
     *   3. The new theme will appear automatically in the launcher icon settings page.
     */
    enum class Theme(
        val id: String,
        val aliasClass: String,
        val labelRes: Int,
        val iconRes: Int,
    ) {
        DEFAULT("default", DEFAULT_ALIAS, R.string.launcher_theme_default, R.mipmap.ic_launcher),
        CALC("calc", "com.ismartcoding.plain.LauncherAliasCalc", R.string.launcher_label_calc, R.mipmap.ic_launcher_calc),
        NOTES("notes", "com.ismartcoding.plain.LauncherAliasNotes", R.string.launcher_label_notes, R.mipmap.ic_launcher_notes),
        CALENDAR("calendar", "com.ismartcoding.plain.LauncherAliasCalendar", R.string.launcher_label_calendar, R.mipmap.ic_launcher_calendar),
        MUSIC("music", "com.ismartcoding.plain.LauncherAliasMusic", R.string.launcher_label_music, R.mipmap.ic_launcher_music),
        VAULT("vault", "com.ismartcoding.plain.LauncherAliasVault", R.string.launcher_label_vault, R.mipmap.ic_launcher_vault);

        companion object {
            fun fromId(id: String?): Theme = values().firstOrNull { it.id == id } ?: DEFAULT
        }
    }

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
        val component = ComponentName(context.packageName, DEFAULT_ALIAS)
        val state = pm.getComponentEnabledSetting(component)
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun setHidden(context: Context, hidden: Boolean) {
        val pm = context.packageManager
        val component = ComponentName(context.packageName, DEFAULT_ALIAS)
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
     * Returns the currently-enabled built-in launcher theme. If multiple aliases
     * happen to be enabled (for example after a manual edit), the first match wins.
     */
    fun getActiveTheme(context: Context): Theme {
        val pm = context.packageManager
        for (theme in Theme.values()) {
            val component = ComponentName(context.packageName, theme.aliasClass)
            val state = try {
                pm.getComponentEnabledSetting(component)
            } catch (_: Throwable) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            }
            val isOn = when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> theme == Theme.DEFAULT
                else -> false
            }
            if (isOn) return theme
        }
        return Theme.DEFAULT
    }

    /**
     * Switches the launcher entry to the given theme. The previously-active alias
     * is disabled and the new one enabled. The launcher cache is then refreshed
     * so the change is visible without a reboot.
     */
    fun setActiveTheme(context: Context, theme: Theme) {
        val pm = context.packageManager
        for (t in Theme.values()) {
            val component = ComponentName(context.packageName, t.aliasClass)
            val newState = if (t == theme) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            try {
                pm.setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP)
            } catch (e: Exception) {
                LogCat.e("Failed to set launcher theme ${t.id}: ${e.message}")
            }
        }
        try {
            forceLauncherRefresh(context)
        } catch (e: Exception) {
            LogCat.e("Failed to refresh launcher: ${e.message}")
        }
    }

    /**
     * Builds a perfect adaptive bitmap from any user-supplied image:
     *   1. Decodes it from the URI with reasonable down-sampling.
     *   2. Centre-crops it into a square.
     *   3. Scales to 432 x 432, the safe-zone size for adaptive icons.
     *   4. Paints it onto a coloured circular-friendly background so the launcher
     *      mask never shows transparency.
     */
    fun buildAdaptiveBitmapFromUri(context: Context, uri: Uri, backgroundColor: Int = Color.WHITE): Bitmap? {
        val src = decodeBitmap(context, uri, 1024) ?: return null
        val square = centerCropSquare(src)
        if (square != src) src.recycle()
        val target = 432
        val safeSize = (target * 0.78f).toInt()
        val scaled = Bitmap.createScaledBitmap(square, safeSize, safeSize, true)
        if (scaled != square) square.recycle()
        val out = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(backgroundColor)
        val pad = ((target - safeSize) / 2).toFloat()
        canvas.drawBitmap(scaled, pad, pad, null)
        scaled.recycle()
        return out
    }

    /**
     * Pins a launcher shortcut that uses the user's image as the icon and the
     * user's text as the label. This is how a fully-custom (image + name) entry
     * is added to the home screen at runtime — Android does not allow changing
     * the manifest icon or label of the actual app at runtime.
     *
     * @return true if the launcher accepted the pin request, false if pinning
     *         is unsupported (very old launchers).
     */
    fun pinCustomShortcut(context: Context, label: String, bitmap: Bitmap): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        launchIntent.action = Intent.ACTION_MAIN
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val safeLabel = label.ifBlank { "PlainApp" }.take(32)
        val id = "plain_custom_${System.currentTimeMillis()}"
        val icon = IconCompat.createWithAdaptiveBitmap(bitmap)
        val shortcut = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(safeLabel)
            .setLongLabel(safeLabel)
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()
        return try {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        } catch (e: Exception) {
            LogCat.e("Pin shortcut failed: ${e.message}")
            false
        }
    }

    private fun decodeBitmap(context: Context, uri: Uri, maxEdge: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while (w / sample > maxEdge * 2 || h / sample > maxEdge * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            LogCat.e("Failed to decode bitmap: ${e.message}")
            null
        }
    }

    private fun centerCropSquare(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        if (src.width == src.height) return src
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        return Bitmap.createBitmap(src, x, y, side, side)
    }

    /**
     * Many OEM home-screen apps (Samsung One UI, MIUI, ColorOS, FuntouchOS,
     * EMUI, MagicUI, etc.) keep a cached copy of every app icon in their drawer
     * even after PackageManager has been updated. Killing the launcher's
     * background process forces it to reload its app list on next launch and
     * the new icon/label appear. Requires KILL_BACKGROUND_PROCESSES (a normal
     * permission, granted at install time).
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
