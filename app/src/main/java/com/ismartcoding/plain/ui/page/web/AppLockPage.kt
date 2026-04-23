package com.ismartcoding.plain.ui.page.web

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ismartcoding.plain.R
import com.ismartcoding.plain.helpers.AppLockHelper
import com.ismartcoding.plain.preferences.AppLockBiometricEnabledPreference
import com.ismartcoding.plain.preferences.AppLockEnabledPreference
import com.ismartcoding.plain.preferences.AppLockPinPreference
import com.ismartcoding.plain.ui.base.BottomSpace
import com.ismartcoding.plain.ui.base.PCard
import com.ismartcoding.plain.ui.base.PListItem
import com.ismartcoding.plain.ui.base.PScaffold
import com.ismartcoding.plain.ui.base.PSwitch
import com.ismartcoding.plain.ui.base.PTopAppBar
import com.ismartcoding.plain.ui.base.Subtitle
import com.ismartcoding.plain.ui.base.Tips
import com.ismartcoding.plain.ui.base.TopSpace
import com.ismartcoding.plain.ui.base.VerticalSpace
import com.ismartcoding.plain.ui.helpers.DialogHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockPage(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lockEnabled by remember { mutableStateOf(false) }
    var biometricEnabled by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(false) }
    var biometricAvailable by remember { mutableStateOf(false) }

    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        lockEnabled = AppLockEnabledPreference.getAsync(context)
        biometricEnabled = AppLockBiometricEnabledPreference.getAsync(context)
        hasPin = AppLockPinPreference.getAsync(context).isNotEmpty()
        biometricAvailable = AppLockHelper.isBiometricAvailable(context)
    }

    PScaffold(
        topBar = { PTopAppBar(navController = navController, title = stringResource(R.string.app_lock_title)) },
        content = { paddingValues ->
            LazyColumn(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
                item {
                    TopSpace()
                    PCard {
                        PListItem(
                            modifier = Modifier.clickable {
                                val target = !lockEnabled
                                scope.launch {
                                    if (target && !hasPin) {
                                        DialogHelper.showMessage(context.getString(R.string.app_lock_set_pin_first))
                                        return@launch
                                    }
                                    AppLockEnabledPreference.putAsync(context, target)
                                    lockEnabled = target
                                }
                            },
                            title = stringResource(R.string.app_lock_enable),
                            subtitle = stringResource(R.string.app_lock_enable_desc),
                        ) {
                            PSwitch(activated = lockEnabled) { enable ->
                                scope.launch {
                                    if (enable && !hasPin) {
                                        DialogHelper.showMessage(context.getString(R.string.app_lock_set_pin_first))
                                        return@launch
                                    }
                                    AppLockEnabledPreference.putAsync(context, enable)
                                    lockEnabled = enable
                                }
                            }
                        }
                        if (biometricAvailable) {
                            PListItem(
                                modifier = Modifier.clickable {
                                    val target = !biometricEnabled
                                    scope.launch {
                                        AppLockBiometricEnabledPreference.putAsync(context, target)
                                        biometricEnabled = target
                                    }
                                },
                                title = stringResource(R.string.app_lock_biometric),
                                subtitle = stringResource(R.string.app_lock_biometric_desc),
                            ) {
                                PSwitch(activated = biometricEnabled) { enable ->
                                    scope.launch {
                                        AppLockBiometricEnabledPreference.putAsync(context, enable)
                                        biometricEnabled = enable
                                    }
                                }
                            }
                        }
                    }
                    Tips(stringResource(R.string.app_lock_does_not_affect_services))
                    VerticalSpace(dp = 16.dp)
                    Subtitle(text = stringResource(if (hasPin) R.string.app_lock_change_pin else R.string.app_lock_set_pin))
                    PCard {
                        if (hasPin) {
                            OutlinedTextField(
                                value = currentPin,
                                onValueChange = { currentPin = it.filter { c -> c.isDigit() }.take(12); msg = "" },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                label = { Text(stringResource(R.string.app_lock_current_pin)) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(12); msg = "" },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            label = { Text(stringResource(R.string.app_lock_new_pin)) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { confirmPin = it.filter { c -> c.isDigit() }.take(12); msg = "" },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            label = { Text(stringResource(R.string.app_lock_confirm_pin)) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        if (msg.isNotEmpty()) {
                            Text(msg, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        Button(
                            modifier = Modifier.padding(16.dp),
                            onClick = {
                                scope.launch {
                                    if (newPin.length < 4) {
                                        msg = context.getString(R.string.app_lock_pin_too_short); return@launch
                                    }
                                    if (newPin != confirmPin) {
                                        msg = context.getString(R.string.app_lock_pin_mismatch); return@launch
                                    }
                                    if (hasPin && !AppLockPinPreference.verifyAsync(context, currentPin)) {
                                        msg = context.getString(R.string.app_lock_pin_incorrect); return@launch
                                    }
                                    AppLockPinPreference.setPinAsync(context, newPin)
                                    hasPin = true
                                    currentPin = ""; newPin = ""; confirmPin = ""
                                    DialogHelper.showMessage(context.getString(R.string.saved))
                                }
                            }
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                    if (hasPin) {
                        VerticalSpace(dp = 16.dp)
                        PCard {
                            PListItem(
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        AppLockPinPreference.setPinAsync(context, "")
                                        AppLockEnabledPreference.putAsync(context, false)
                                        AppLockBiometricEnabledPreference.putAsync(context, false)
                                        hasPin = false; lockEnabled = false; biometricEnabled = false
                                        DialogHelper.showMessage(context.getString(R.string.app_lock_pin_removed))
                                    }
                                },
                                title = stringResource(R.string.app_lock_remove_pin),
                                subtitle = stringResource(R.string.app_lock_remove_pin_desc),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                item { BottomSpace(paddingValues) }
            }
        }
    )
}
