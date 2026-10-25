package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

@Composable
fun ConnectScreen(
    initialUrl: String,
    initialUsername: String,
    busy: Boolean,
    error: String?,
    onSubmit: (url: String, username: String, password: String) -> Unit,
) {
    val colors = LocalMercuryColors.current
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Mercury",
            style = MaterialTheme.typography.displayLarge,
            color = colors.textPrimary,
        )
        Text(
            text = "Ton agent, dans ta poche.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp, bottom = 32.dp),
        )

        Field(
            value = url,
            onValueChange = { url = it },
            label = "Serveur",
            hint = "https://spark-a286.tail128204.ts.net:8443",
            keyboardType = KeyboardType.Uri,
        )
        Field(
            value = username,
            onValueChange = { username = it },
            label = "Identifiant",
            hint = "mercury",
        )
        Field(
            value = password,
            onValueChange = { password = it },
            label = "Mot de passe",
            hint = "••••••••",
            isPassword = true,
        )

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.danger,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }

        Button(
            onClick = { onSubmit(url, username, password) },
            enabled = !busy && url.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.textPrimary,
                contentColor = colors.canvas,
                disabledContainerColor = colors.surfaceMuted,
                disabledContentColor = colors.textSecondary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = colors.textSecondary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Se connecter", modifier = Modifier.padding(vertical = 6.dp))
            }
        }

        Text(
            text = "Le dashboard Mercury doit être joignable depuis le téléphone " +
                "(Tailscale + `tailscale serve`, ou tunnel SSH).",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = LocalMercuryColors.current
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text(hint, color = colors.textSecondary) },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = ImeAction.Next,
            ),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedIndicatorColor = colors.accent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = colors.accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
