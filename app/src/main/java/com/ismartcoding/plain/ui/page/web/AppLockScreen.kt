package com.ismartcoding.plain.ui.page.web

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.fragment.app.FragmentActivity
import com.ismartcoding.plain.R
import com.ismartcoding.plain.helpers.AppLockHelper
import com.ismartcoding.plain.preferences.AppLockBiometricEnabledPreference
import com.ismartcoding.plain.preferences.AppLockPinPreference
import kotlinx.coroutines.launch

@Composable
fun AppLockScreen(activity: FragmentActivity, onUnlock: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var biometricEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        biometricEnabled = AppLockBiometricEnabledPreference.getAsync(context) &&
            AppLockHelper.isBiometricAvailable(context)
        if (biometricEnabled) {
            AppLockHelper.showBiometricPrompt(
                activity = activity,
                title = context.getString(R.string.app_lock_unlock_title),
                subtitle = context.getString(R.string.app_lock_unlock_biometric_subtitle),
                negativeText = context.getString(R.string.app_lock_use_pin),
                onSuccess = onUnlock,
                onFallback = { /* fall through to PIN entry */ }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_lock_unlock_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_lock_unlock_pin_subtitle),
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
                label = { Text(stringResource(R.string.app_lock_pin)) }
            )
            if (error.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                scope.launch {
                    if (AppLockPinPreference.verifyAsync(context, pin)) {
                        onUnlock()
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
                        title = context.getString(R.string.app_lock_unlock_title),
                        subtitle = context.getString(R.string.app_lock_unlock_biometric_subtitle),
                        negativeText = context.getString(R.string.app_lock_use_pin),
                        onSuccess = onUnlock,
                        onFallback = {}
                    )
                }) {
                    Text(stringResource(R.string.app_lock_use_biometric))
                }
            }
        }
    }
}
