package com.ismartcoding.plain.ui.page.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ismartcoding.plain.R
import com.ismartcoding.plain.TempData
import com.ismartcoding.plain.preferences.CloudflareTunnelHostnamePreference
import com.ismartcoding.plain.preferences.HttpsPreference
import com.ismartcoding.plain.ui.base.PIconTextButton
import com.ismartcoding.plain.ui.base.POutlinedButton
import com.ismartcoding.plain.ui.base.Tips
import com.ismartcoding.plain.ui.base.VerticalSpace
import com.ismartcoding.plain.ui.components.HttpHttpsSegmentedButton
import com.ismartcoding.plain.ui.components.WebAddressBar
import com.ismartcoding.plain.ui.components.WebAddressBarQrDialog
import com.ismartcoding.plain.ui.components.WebAddressBarRow
import com.ismartcoding.plain.ui.helpers.WebHelper
import com.ismartcoding.plain.ui.models.MainViewModel
import com.ismartcoding.plain.ui.nav.Routing
import com.ismartcoding.plain.ui.theme.cardBackgroundNormal
import kotlinx.coroutines.launch

@Composable
fun HomeWebAddressSection(
    context: Context,
    navController: NavHostController,
    mainVM: MainViewModel,
    isError: Boolean
) {
    var isHttps by remember { mutableStateOf(TempData.webHttps) }
    var tunnelHostname by remember { mutableStateOf("") }
    var tunnelQrUrl by remember { mutableStateOf("") }
    var tunnelQrVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        tunnelHostname = CloudflareTunnelHostnamePreference.getAsync(context).trim()
    }

    Column {
        Text(
            text = stringResource(R.string.web_address_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        VerticalSpace(12.dp)
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            WebAddressBar(context = context, mainVM = mainVM, isHttps = isHttps)
            if (tunnelHostname.isNotEmpty()) {
                VerticalSpace(8.dp)
                Text(
                    text = stringResource(R.string.cloudflare_tunnel_public_url),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
                VerticalSpace(4.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.cardBackgroundNormal,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(vertical = 8.dp),
                ) {
                    val url = "https://$tunnelHostname"
                    Row(
                        modifier = Modifier.height(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WebAddressBarRow(
                            url = url,
                            isHostnameRow = true,
                            onEditClick = { navController.navigate(Routing.CloudflareTunnel) },
                            onQrClick = {
                                tunnelQrUrl = url
                                tunnelQrVisible = true
                            },
                        )
                    }
                }
            }
            VerticalSpace(12.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HttpHttpsSegmentedButton(
                    isHttps = isHttps,
                    onSelect = { https ->
                        isHttps = https
                        scope.launch { HttpsPreference.putAsync(context, https) }
                    },
                )
                if (isError) {
                    POutlinedButton(
                        stringResource(R.string.troubleshoot),
                        onClick = {
                            WebHelper.open(
                                context,
                                "https://plainapp.app/troubleshooting"
                            )
                        },
                    )
                } else {
                    PIconTextButton(R.drawable.settings, stringResource(R.string.web_settings)) {
                        navController.navigate(Routing.WebSettings)
                    }
                }
            }
        }
        VerticalSpace(8.dp)
        Tips(text = stringResource(R.string.same_network_hint))
    }

    if (tunnelQrVisible) {
        WebAddressBarQrDialog(
            url = tunnelQrUrl,
            onClose = { tunnelQrVisible = false },
        )
    }
}
