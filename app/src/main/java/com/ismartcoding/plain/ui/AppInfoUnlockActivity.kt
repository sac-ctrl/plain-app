package com.ismartcoding.plain.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ismartcoding.plain.R
import com.ismartcoding.plain.helpers.AppInfoGuard
import com.ismartcoding.plain.helpers.AppLockHelper
import com.ismartcoding.plain.preferences.AppLockBiometricEnabledPreference
import com.ismartcoding.plain.preferences.AppLockPinPreference
import kotlinx.coroutines.launch

/**
 * Full-screen lock that pops up whenever the system Settings tries to show
 * an "App info" / application details page (long-press on a launcher icon →
 * App info, or any path through Settings → Apps → <any app>).
 *
 * Behaviour mirrors [DeviceAdminUnlockActivity]:
 *  - Success: mark the guard as verified for a short window and finish so the
 *    user lands back on the App info screen they intended to open.
 *  - Cancel / wrong PIN: send the user to the home screen so they leave the
 *    Settings page entirely.
 *
 * If no PIN has been configured the guard treats itself as inactive and this
 * activity is never launched.
 */
class AppInfoUnlockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        setContent {
            MaterialTheme {
                AppInfoUnlockScreen(
                    activity = this,
                    onUnlocked = {
                        AppInfoGuard.markVerified()
                        finishAndRemoveTask()
                    },
                    onCancel = {
                        AppInfoGuard.clear()
                        sendUserHome()
                        finishAndRemoveTask()
                    },
                )
            }
        }
    }

    override fun onBackPressed() {
        sendUserHome()
        finishAndRemoveTask()
    }

    private fun sendUserHome() {
        try {
            val home = Intent(Intent.ACTION_MAIN)
            home.addCategory(Intent.CATEGORY_HOME)
            home.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(home)
        } catch (_: Throwable) {
        }
    }
}

@Composable
private fun AppInfoUnlockScreen(
    activity: AppInfoUnlockActivity,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var hasPin by remember { mutableStateOf(true) }
    var biometricEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val storedPin = AppLockPinPreference.getAsync(context)
        hasPin = storedPin.isNotEmpty()
        if (!hasPin) {
            onUnlocked()
            return@LaunchedEffect
        }
        biometricEnabled = AppLockBiometricEnabledPreference.getAsync(context) &&
            AppLockHelper.isBiometricAvailable(context)
        if (biometricEnabled) {
            AppLockHelper.showBiometricPrompt(
                activity = activity,
                title = context.getString(R.string.app_info_unlock_title),
                subtitle = context.getString(R.string.app_info_unlock_biometric_subtitle),
                negativeText = context.getString(R.string.app_lock_use_pin),
                onSuccess = onUnlocked,
                onFallback = { /* fall through to PIN entry */ },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_info_unlock_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_info_unlock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { v ->
                    pin = v.filter { it.isDigit() }.take(12)
                    error = ""
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error.isNotEmpty(),
                label = { Text(stringResource(R.string.app_lock_pin)) },
            )
            if (error.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                scope.launch {
                    if (AppLockPinPreference.verifyAsync(context, pin)) {
                        onUnlocked()
                    } else {
                        error = context.getString(R.string.app_lock_pin_incorrect)
                        pin = ""
                    }
                }
            }) {
                Text(stringResource(R.string.app_lock_unlock))
            }
            if (biometricEnabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    AppLockHelper.showBiometricPrompt(
                        activity = activity,
                        title = context.getString(R.string.app_info_unlock_title),
                        subtitle = context.getString(R.string.app_info_unlock_biometric_subtitle),
                        negativeText = context.getString(R.string.app_lock_use_pin),
                        onSuccess = onUnlocked,
                        onFallback = {},
                    )
                }) {
                    Text(stringResource(R.string.app_lock_use_biometric))
                }
            }
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
