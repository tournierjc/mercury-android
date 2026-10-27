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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

    val rows = remember {
        listOf(
            PermissionRow(Manifest.permission.READ_CALENDAR, "Calendrier", "lire les événements"),
            PermissionRow(Manifest.permission.READ_CONTACTS, "Contacts", "noms, numéros, e-mails"),
            PermissionRow(Manifest.permission.READ_SMS, "SMS", "lire les messages reçus"),
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour", tint = colors.textPrimary)
            }
            Text(
                text = "Pont appareil",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
        }

        Text(
            text = "Mercury lit ton téléphone en lecture seule, à la demande, via le tailnet. " +
                "Rien n'est copié sur le Spark : chaque requête de l'agent relit la donnée locale.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Pont actif", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                Text(
                    text = if (enabled) "Le serveur écoute sur le port ${prefs.port}" else "Serveur arrêté",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Switch(
                checked = enabled,
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

        SectionTitle("Accès aux données")
        rows.forEach { row ->
            val granted = ContextCompat.checkSelfPermission(context, row.permission) ==
                PackageManager.PERMISSION_GRANTED
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                    Text(
                        text = if (granted) "accordé · ${row.detail}" else row.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (granted) colors.accent else colors.textSecondary,
                    )
                }
                if (!granted) {
                    OutlinedButton(
                        onClick = { permissionLauncher.launch(arrayOf(row.permission)) },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                    ) { Text("Autoriser") }
                }
            }
        }

        val notificationsGranted = NotificationStore.isAccessGranted(context)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Messages des apps", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                Text(
                    text = if (notificationsGranted) {
                        "accès notifications accordé · WhatsApp, Signal, Messenger…"
                    } else {
                        "via l'accès notifications (WhatsApp, Signal…)"
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
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
            ) { Text(if (notificationsGranted) "Réglages" else "Activer") }
        }

        SectionTitle("À donner à Mercury")
        // The value is the button: tapping the field copies it (the three-button row it replaces
        // spent a third of the block on controls). The eye sits in the token's own field, where a
        // password field keeps it, instead of doubling the row with « Afficher » / « Masquer ».
        var copiedUrl by remember { mutableStateOf(false) }
        var copiedToken by remember { mutableStateOf(false) }
        val urlValue = BridgeAddress.url(prefs.port)

        CopyableField(
            label = "URL",
            value = urlValue,
            copied = copiedUrl,
            onClick = {
                clipboard.setText(AnnotatedString(urlValue))
                copiedUrl = true
            },
            onCopiedShown = { copiedUrl = false },
        )
        CopyableField(
            label = "Token",
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
                        contentDescription = if (showToken) "Masquer le token" else "Afficher le token",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            },
        )
        Button(
            onClick = {
                token = prefs.rotateToken()
                if (enabled) {
                    BridgeService.stop(context)
                    BridgeService.start(context)
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.danger.copy(alpha = 0.16f),
                contentColor = colors.danger,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(52.dp),
        ) { Text("Révoquer le token") }

        SectionTitle("Ce que l'agent peut lire")
        Text(
            text = buildString {
                appendLine("GET /health · version, appareil, providers actifs")
                appendLine("GET /device/status · batterie, réseau, fuseau")
                appendLine("GET /calendar?from=&to=&limit= · événements (ISO 8601)")
                appendLine("GET /contacts?q=&limit= · noms, numéros, e-mails")
                appendLine("GET /sms?limit=&address= · messages SMS reçus")
                appendLine("GET /messages?limit= · SMS + notifications d'apps")
                append("En-tête requis : Authorization: Bearer <token>")
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .padding(16.dp),
        )

        Text(
            text = "Sans le pont, l'agent ne voit rien du téléphone. Avec, il ne voit que ce que tu as " +
                "autorisé, pendant que le service tourne. « Révoquer » invalide l'ancien token.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 16.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    val colors = LocalMercuryColors.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
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
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Text(
                text = if (copied) "Copié" else value,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (copied) colors.accent else colors.textPrimary,
                maxLines = 1,
            )
        }
        trailing?.invoke()
    }
}

