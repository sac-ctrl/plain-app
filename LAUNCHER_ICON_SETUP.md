# Launcher icon and app name — full setup guide

This guide explains every way you can change the **home-screen icon** and the
**app name** of PlainApp, both from the in-app Settings screen and directly
from code.

---

## 1. From the in-app Settings (no code, no rebuild)

Open the app on your phone and go to:

> **Settings → Launcher icon and name**

You will see two sections.

### A. Built-in themes

The page lists six ready-made themes. Tap any of them to apply.

| Theme     | Home-screen label | Looks like                |
|-----------|-------------------|---------------------------|
| Default   | PlainApp          | The original blue P icon  |
| Calculator | Calculator       | A standard calculator app |
| My Notes  | My Notes          | A yellow notepad          |
| Calendar  | Calendar          | A red calendar            |
| Music     | Music             | A purple music app        |
| Vault     | Vault             | A dark padlock            |

Switching a theme:
- Toggles a different `<activity-alias>` in the manifest.
- Forces nearby launcher apps (Pixel, One UI, MIUI, ColorOS, etc.) to refresh
  their icon cache, so the change appears within a few seconds.
- Does **not** uninstall or restart the app — your data stays exactly the same.

### B. Use your own image

1. Tap **Pick image** and choose any photo, screenshot, logo, or drawing.
2. Type a name into the text field (e.g. `Calculator`, `Files`, anything).
3. Tap **Pin to home screen**.

The app will:
- Centre-crop your image to a square.
- Scale it to 432 × 432 (the official Android adaptive-icon size).
- Place it on a clean background so the icon looks perfect on any launcher
  shape (circle, squircle, rounded square, teardrop).
- Ask Android to pin the new shortcut to your home screen with your custom
  name as the label.

> Custom shortcuts are added **alongside** the built-in icon. To get a
> truly fully-disguised look, also pick a built-in theme so the original
> icon hides too.

### Image specs (what works best)

- **Aspect ratio:** square (1:1).
- **Recommended size:** 1024 × 1024 px or larger.
- **Formats:** PNG, JPG, WebP.
- **Safe zone:** keep the meaningful content inside the **centre 66 %** of
  the image — the launcher may crop the outer edges depending on its mask
  shape.
- **Backgrounds:** solid or simple backgrounds look cleanest; transparent
  PNGs are fine, the app fills the background automatically.

---

## 2. From code (replacing the bundled icon)

If you want to ship a different default icon in the APK itself, you only need
to drop files into a few folders.

### 2.1 Replace the default app icon

The default adaptive icon lives at:

```
app/src/main/res/mipmap/ic_launcher.xml
app/src/main/res/mipmap/ic_launcher_round.xml
```

These are XML adaptive icons and reference two drawables:

```
app/src/main/res/drawable/launcher_foreground.xml   ← the icon shape
app/src/main/res/drawable/launcher_background.xml   ← the background
app/src/main/res/drawable/launcher_monochrome.xml   ← Android 13+ themed icon
```

You have two options.

#### Option A — drop in PNG bitmaps (easiest)

If you only have a PNG, create these files (use Android Studio's
**Image Asset Studio**: New → Image Asset, or any online generator):

```
app/src/main/res/mipmap-mdpi/ic_launcher.png        48 × 48
app/src/main/res/mipmap-hdpi/ic_launcher.png        72 × 72
app/src/main/res/mipmap-xhdpi/ic_launcher.png       96 × 96
app/src/main/res/mipmap-xxhdpi/ic_launcher.png    144 × 144
app/src/main/res/mipmap-xxxhdpi/ic_launcher.png   192 × 192
```

For perfect adaptive icons (Android 8+), also drop the foreground at:

```
app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png        108 × 108
app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png        162 × 162
app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png       216 × 216
app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png      324 × 324
app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png     432 × 432
```

Then point the adaptive icon at the bitmap by editing
`app/src/main/res/mipmap/ic_launcher.xml` to:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
```

#### Option B — keep the vector pipeline (sharpest)

Replace the **path data** inside
`app/src/main/res/drawable/launcher_foreground.xml`. The viewport is
108 × 108 dp and the safe area is the inner 72 × 72 dp.

You can change the brand colour by editing
`app/src/main/res/values/colors.xml`:

```xml
<color name="app_icon">#1A73E8</color>          ← foreground tint
<color name="launcher_background">#FFFFFF</color>  ← background colour
```

### 2.2 Change the default app name

The app name comes from a single string resource:

```
app/src/main/res/values/strings_settings.xml
```

Edit this line:

```xml
<string name="app_name" translatable="false">PlainApp</string>
```

For Hindi:

```
app/src/main/res/values-hi/strings_settings.xml
```

```xml
<string name="app_name">प्लेनऐप</string>
```

After editing, rebuild the app — every launcher entry that uses
`@string/app_name` will pick it up.

---

## 3. Adding a brand-new built-in theme

Suppose you want a new theme called **Files** that looks like a file manager.

### Step 1 — drawables

Create:

```
app/src/main/res/drawable/launcher_files_foreground.xml
```

(Any 108 × 108 vector drawable will do. Copy
`launcher_calc_foreground.xml` as a template.)

### Step 2 — adaptive icon

```
app/src/main/res/mipmap/ic_launcher_files.xml
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_bg_files"/>
    <foreground android:drawable="@drawable/launcher_files_foreground"/>
    <monochrome android:drawable="@drawable/launcher_files_foreground" />
</adaptive-icon>
```

### Step 3 — colour and label

In `app/src/main/res/values/colors.xml`:

```xml
<color name="launcher_bg_files">#0D47A1</color>
```

In `app/src/main/res/values/strings_settings.xml`:

```xml
<string name="launcher_label_files">Files</string>
```

### Step 4 — manifest alias

In `app/src/main/AndroidManifest.xml`, under `<application>`, add:

```xml
<activity-alias
    android:name=".LauncherAliasFiles"
    android:enabled="false"
    android:exported="true"
    android:icon="@mipmap/ic_launcher_files"
    android:label="@string/launcher_label_files"
    android:targetActivity=".ui.MainActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity-alias>
```

### Step 5 — register in code

Open
`app/src/main/java/com/ismartcoding/plain/helpers/LauncherIconHelper.kt`
and add an entry to the `Theme` enum:

```kotlin
FILES(
    "files",
    "com.ismartcoding.plain.LauncherAliasFiles",
    R.string.launcher_label_files,
    R.mipmap.ic_launcher_files,
),
```

That is the only Kotlin change required. The new theme will show up
automatically in the in-app Settings screen.

---

## 4. How the system works under the hood

- The `<application android:icon="...">` value in `AndroidManifest.xml`
  is **not** what shows up on the home screen. The launcher entry comes
  from whichever `<activity-alias>` (or activity) holds the
  `MAIN/LAUNCHER` intent filter.
- The app declares **six** activity-aliases — one per theme. Only one is
  enabled at a time. When you switch themes, the helper calls
  `PackageManager.setComponentEnabledSetting` to disable the previous
  alias and enable the new one. No reinstall, no restart.
- Custom user-image icons cannot replace the manifest icon at runtime
  (Android does not allow it). Instead, they are added as **pinned
  shortcuts** with `ShortcutManagerCompat.requestPinShortcut`.
- After every change, the helper kills the launcher's background
  process so it reloads its icon cache. This is why the new icon
  appears within a few seconds without needing a phone restart.

---

## 5. Quick file map

| What you want to change          | File / folder                                                                |
|----------------------------------|------------------------------------------------------------------------------|
| Default icon (vector)            | `app/src/main/res/drawable/launcher_foreground.xml`                          |
| Default icon (PNG fallback)      | `app/src/main/res/mipmap-*/ic_launcher.png`                                  |
| Default icon background colour   | `app/src/main/res/values/colors.xml` → `launcher_background`                 |
| Default app name                 | `app/src/main/res/values/strings_settings.xml` → `app_name`                  |
| Hindi app name                   | `app/src/main/res/values-hi/strings_settings.xml` → `app_name`               |
| Manifest aliases                 | `app/src/main/AndroidManifest.xml`                                           |
| Built-in theme list              | `app/src/main/java/com/ismartcoding/plain/helpers/LauncherIconHelper.kt`     |
| In-app Settings page             | `app/src/main/java/com/ismartcoding/plain/ui/page/settings/LauncherIconPage.kt` |
| Strings for the Settings page    | `app/src/main/res/values/strings_settings.xml` (search `launcher_icon_*`)    |
