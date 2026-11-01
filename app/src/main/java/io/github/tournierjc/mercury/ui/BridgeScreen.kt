package io.github.tournierjc.mercury.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.SecurePrefs
import io.github.tournierjc.mercury.device.BridgeAddress
import io.github.tournierjc.mercury.device.BridgePrefs
import io.github.tournierjc.mercury.device.BridgeService
import io.github.tournierjc.mercury.device.DeviceReaders
import io.github.tournierjc.mercury.device.NotificationStore
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

private data class PermissionRow(val permission: String, val label: String, val detail: String)

@Composable
fun BridgeScreen(onBack: () -> Unit) {
    val colors = LocalMercuryColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val prefs = remember { BridgePrefs(context) }

    var enabled by remember { mutableStateOf(prefs.enabled) }
    var token by remember { mutableStateOf(prefs.token) }
    var showToken by remember { mutableStateOf(false) }
    var providers by remember { mutableStateOf(DeviceReaders(context).grantedProviders()) }

    // Read here, in the composable: the rows used to be built inside remember { }, where a
    // resource lookup is not allowed — and would not follow a language change anyway.
    val calendarLabel = stringResource(R.string.bridge_calendar)
    val calendarDetail = stringResource(R.string.bridge_read_events)
    val contactsLabel = stringResource(R.string.bridge_contacts)
    val contactsDetail = stringResource(R.string.bridge_contacts_detail)
    val smsDetail = stringResource(R.string.bridge_read_received)
    val rows = remember(calendarLabel, calendarDetail, contactsLabel, contactsDetail, smsDetail) {
        listOf(
            PermissionRow(Manifest.permission.READ_CALENDAR, calendarLabel, calendarDetail),
            PermissionRow(Manifest.permission.READ_CONTACTS, contactsLabel, contactsDetail),
            PermissionRow(Manifest.permission.READ_SMS, "SMS", smsDetail),
        )
    }

    fun refresh() {
        providers = DeviceReaders(context).grantedProviders()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh() }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.bridge_title),
            onBack = onBack,
            // The permissions it shows change outside this screen (system dialogs, a revocation in
            // Réglages), so the header carries the same refresh as every other screen.
            onRefresh = { refresh() },
            // A local permissions read: instant, so the button never spins.
            refreshing = false,
        )
        Text(
            text = stringResource(R.string.bridge_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )

        val degraded = remember { SecurePrefs.degraded }
        if (degraded) {
            Text(
                text = stringResource(R.string.bridge_degraded),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(colors.surface)
                    .padding(16.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.bridge_active), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                Text(
                    text = if (enabled) {
                        stringResource(R.string.bridge_listening, prefs.port)
                    } else {
                        stringResource(R.string.bridge_stopped)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Switch(
                checked = enabled,
                // Keystore cassé : le token ne serait pas chiffré, le pont ne sert rien.
                enabled = !degraded,
                onCheckedChange = { wantEnabled ->
                    if (wantEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        BridgeService.start(context)
                    } else {
                        BridgeService.stop(context)
                    }
                    enabled = wantEnabled
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.canvas,
                    checkedTrackColor = colors.accent,
                ),
            )
        }

        SectionTitle(stringResource(R.string.bridge_access_title))
        rows.forEach { row ->
            val granted = ContextCompat.checkSelfPermission(context, row.permission) ==
                PackageManager.PERMISSION_GRANTED
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                    Text(
                        text = if (granted) stringResource(R.string.bridge_granted, row.detail) else row.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (granted) colors.accent else colors.textSecondary,
                    )
                }
                if (!granted) {
                    OutlinedButton(
                        onClick = { permissionLauncher.launch(arrayOf(row.permission)) },
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                    ) { Text(stringResource(R.string.bridge_allow)) }
                }
            }
        }

        val notificationsGranted = NotificationStore.isAccessGranted(context)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.bridge_app_messages), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                Text(
                    text = if (notificationsGranted) {
                        stringResource(R.string.bridge_notif_granted)
                    } else {
                        stringResource(R.string.bridge_notif_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (notificationsGranted) colors.accent else colors.textSecondary,
                )
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                },
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
            ) { Text(if (notificationsGranted) stringResource(R.string.drawer_settings) else stringResource(R.string.bridge_enable)) }
        }

        SectionTitle(stringResource(R.string.bridge_give_title))
        // The value is the button: tapping the field copies it (the three-button row it replaces
        // spent a third of the block on controls). The eye sits in the token's own field, where a
        // password field keeps it, instead of doubling the row with « Afficher » / « Masquer ».
        var copiedUrl by remember { mutableStateOf(false) }
        var copiedToken by remember { mutableStateOf(false) }
        val urlValue = BridgeAddress.url(prefs.port, stringResource(R.string.bridge_no_address))

        CopyableField(
            label = stringResource(R.string.bridge_url_label),
            value = urlValue,
            copied = copiedUrl,
            onClick = {
                clipboard.setText(AnnotatedString(urlValue))
                copiedUrl = true
            },
            onCopiedShown = { copiedUrl = false },
        )
        CopyableField(
            label = stringResource(R.string.bridge_token_label),
            value = if (showToken) token else "•".repeat(24),
            copied = copiedToken,
            onClick = {
                clipboard.setText(AnnotatedString(token))
                copiedToken = true
            },
            onCopiedShown = { copiedToken = false },
            trailing = {
                IconButton(onClick = { showToken = !showToken }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showToken) stringResource(R.string.bridge_hide_token) else stringResource(R.string.bridge_show_token),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            },
        )

        Text(
            text = stringResource(R.string.bridge_security_note),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )

        Button(
            onClick = {
                token = prefs.rotateToken()
                if (enabled) {
                    BridgeService.stop(context)
                    BridgeService.start(context)
                }
            },
            shape = MaterialTheme.shapes.extraSmall,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.danger.copy(alpha = 0.16f),
                contentColor = colors.danger,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(52.dp),
        ) { Text(stringResource(R.string.bridge_revoke)) }

        SectionTitle(stringResource(R.string.bridge_readable_title))
        Text(
            text = stringResource(R.string.bridge_endpoints),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.surface)
                .padding(16.dp),
        )

        Text(
            text = stringResource(R.string.bridge_footer),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 16.dp, bottom = 28.dp),
        )
    }
}

/**
 * A value you can copy by tapping it.
 *
 * It reads as a field — label above, monospace value inside a rounded box — and says « Copié »
 * for a moment after the tap, because a copy with no feedback is indistinguishable from a tap
 * that missed.
 */
@Composable
private fun CopyableField(
    label: String,
    value: String,
    copied: Boolean,
    onClick: () -> Unit,
    onCopiedShown: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalMercuryColors.current
    if (copied) {
        LaunchedEffect(Unit) {
            delay(1400)
            onCopiedShown()
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Text(
                text = if (copied) stringResource(R.string.bridge_copied) else value,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (copied) colors.accent else colors.textPrimary,
                maxLines = 1,
            )
        }
        trailing?.invoke()
    }
}

