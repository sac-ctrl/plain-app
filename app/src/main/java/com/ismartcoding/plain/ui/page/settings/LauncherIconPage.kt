package com.ismartcoding.plain.ui.page.settings

import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavHostController
import com.ismartcoding.plain.R
import com.ismartcoding.plain.helpers.LauncherIconHelper
import com.ismartcoding.plain.helpers.LauncherIconHelper.Theme
import com.ismartcoding.plain.preferences.CustomLauncherNamePreference
import com.ismartcoding.plain.preferences.LauncherThemePreference
import com.ismartcoding.plain.ui.base.BottomSpace
import com.ismartcoding.plain.ui.base.PCard
import com.ismartcoding.plain.ui.base.PScaffold
import com.ismartcoding.plain.ui.base.PTopAppBar
import com.ismartcoding.plain.ui.base.TopSpace
import com.ismartcoding.plain.ui.base.VerticalSpace
import com.ismartcoding.plain.ui.helpers.DialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherIconPage(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var activeTheme by remember { mutableStateOf(LauncherIconHelper.getActiveTheme(context)) }
    var customName by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            customName = CustomLauncherNamePreference.getAsync(context)
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            pickedUri = uri
            scope.launch(Dispatchers.IO) {
                val bg = context.getColor(R.color.launcher_background)
                preview = LauncherIconHelper.buildAdaptiveBitmapFromUri(context, uri, bg)
            }
        }
    }

    PScaffold(
        topBar = { PTopAppBar(navController = navController, title = stringResource(R.string.launcher_icon)) },
        content = { padding ->
            LazyColumn(modifier = Modifier.padding(top = padding.calculateTopPadding())) {
                item { TopSpace() }
                item {
                    Text(
                        text = stringResource(R.string.launcher_icon_subtitle),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VerticalSpace(dp = 12.dp)
                }
                item {
                    SectionTitle(stringResource(R.string.launcher_icon_built_in))
                    PCard {
                        Text(
                            text = stringResource(R.string.launcher_icon_built_in_desc),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        Theme.values().forEach { theme ->
                            ThemeRow(
                                theme = theme,
                                active = activeTheme == theme,
                                onApply = {
                                    LauncherIconHelper.setActiveTheme(context, theme)
                                    activeTheme = theme
                                    scope.launch(Dispatchers.IO) {
                                        LauncherThemePreference.putAsync(context, theme.id)
                                    }
                                    DialogHelper.showMessage(context.getString(R.string.launcher_icon_applied))
                                },
                            )
                        }
                    }
                    VerticalSpace(dp = 16.dp)
                }
                item {
                    SectionTitle(stringResource(R.string.launcher_icon_custom))
                    PCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.launcher_icon_custom_desc),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val bmp = preview
                                    if (bmp != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)),
                                        )
                                    } else {
                                        Text(text = "+", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                                        Text(stringResource(R.string.launcher_icon_pick_image))
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = customName,
                                onValueChange = { if (it.length <= 32) customName = it },
                                placeholder = { Text(stringResource(R.string.launcher_icon_custom_name_hint)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val bmp = preview
                                    if (bmp == null) {
                                        DialogHelper.showMessage(context.getString(R.string.launcher_icon_pick_first))
                                        return@Button
                                    }
                                    keyboard?.hide()
                                    val label = customName.ifBlank { context.getString(R.string.app_name) }
                                    val ok = LauncherIconHelper.pinCustomShortcut(context, label, bmp)
                                    if (ok) {
                                        scope.launch(Dispatchers.IO) {
                                            CustomLauncherNamePreference.putAsync(context, label)
                                        }
                                        DialogHelper.showMessage(context.getString(R.string.launcher_icon_pinned))
                                    } else {
                                        DialogHelper.showMessage(context.getString(R.string.launcher_icon_pin_unsupported))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.launcher_icon_pin_shortcut))
                            }
                        }
                    }
                    VerticalSpace(dp = 16.dp)
                }
                item {
                    SectionTitle(stringResource(R.string.launcher_icon_specs_title))
                    PCard {
                        Text(
                            text = stringResource(R.string.launcher_icon_specs_body),
                            modifier = Modifier.padding(16.dp),
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    VerticalSpace(dp = 16.dp)
                }
                item {
                    SectionTitle(stringResource(R.string.launcher_icon_code_title))
                    PCard {
                        Text(
                            text = stringResource(R.string.launcher_icon_code_body),
                            modifier = Modifier.padding(16.dp),
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
                item { BottomSpace(padding) }
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ThemeRow(theme: Theme, active: Boolean, onApply: () -> Unit) {
    val context = LocalContext.current
    val iconBitmap = remember(theme) {
        val drawable = context.getDrawable(theme.iconRes)
        when (drawable) {
            is AdaptiveIconDrawable -> drawable.toBitmap(160, 160, Bitmap.Config.ARGB_8888)
            else -> drawable?.toBitmap(160, 160, Bitmap.Config.ARGB_8888)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!active) onApply() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (iconBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = iconBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(theme.labelRes), fontWeight = FontWeight.SemiBold)
            Text(
                text = "alias: ${theme.aliasClass.substringAfterLast('.')}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (active) {
            Text(
                text = stringResource(R.string.launcher_icon_active),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            OutlinedButton(onClick = onApply) {
                Text(stringResource(R.string.launcher_icon_apply))
            }
        }
    }
}
