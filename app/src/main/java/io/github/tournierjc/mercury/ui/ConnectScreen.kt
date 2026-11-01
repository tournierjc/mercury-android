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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.ServerAddress
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

@Composable
fun ConnectScreen(
    initialUrl: String,
    initialUsername: String,
    busy: Boolean,
    error: io.github.tournierjc.mercury.data.Notice?,
    onSubmit: (url: String, username: String, password: String) -> Unit,
) {
    val colors = LocalMercuryColors.current
    val focusManager = LocalFocusManager.current
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // The same address rule as Réglages, from the same place: ServerAddress owns the rule, the
    // screen only translates its answer. An empty field stays quiet — the button already says
    // the form is not ready — but a typed address that cannot be one says why.
    val urlProblem = ServerAddress.validate(url)
    val urlProblemText = when (urlProblem) {
        null -> null
        ServerAddress.Problem.Empty -> if (url.isBlank()) null else stringResource(R.string.settings_server_problem_empty)
        ServerAddress.Problem.UnsupportedScheme -> stringResource(R.string.settings_server_problem_scheme)
        ServerAddress.Problem.MissingHost -> stringResource(R.string.settings_server_problem_host)
    }
    val canSubmit = !busy && urlProblem == null && username.isNotBlank() && password.isNotBlank()
    val submit: () -> Unit = { if (canSubmit) onSubmit(url, username, password) }

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
            text = stringResource(R.string.connect_brand),
            style = MaterialTheme.typography.displayLarge,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.connect_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp, bottom = 32.dp),
        )

        Field(
            value = url,
            onValueChange = { url = it },
            label = stringResource(R.string.connect_label_server),
            hint = "https://mercury.example.com:8443",
            keyboardType = KeyboardType.Uri,
            error = urlProblemText,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )
        Field(
            value = username,
            onValueChange = { username = it },
            label = stringResource(R.string.connect_label_username),
            hint = "mercury",
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )
        Field(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.connect_label_password),
            hint = "••••••••",
            isPassword = true,
            revealed = passwordVisible,
            onToggleRevealed = { passwordVisible = !passwordVisible },
            onDone = submit,
        )

        if (error != null) {
            Text(
                text = noticeText(error),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.danger,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }

        Button(
            onClick = submit,
            enabled = canSubmit,
            shape = MaterialTheme.shapes.extraLarge,
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
                Text(stringResource(R.string.connect_sign_in), modifier = Modifier.padding(vertical = 6.dp))
            }
        }

        Text(
            text = stringResource(R.string.connect_reachability_hint),
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
    /** A password the owner chose to see; a toggle without it is a value typed blind. */
    revealed: Boolean = false,
    onToggleRevealed: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    /** Why the value cannot be one yet, in the screen's language. */
    error: String? = null,
    onNext: (() -> Unit)? = null,
    /** The last field of the form hands its press to the action, not to a dead Next. */
    onDone: (() -> Unit)? = null,
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
            isError = error != null,
            visualTransformation = if (isPassword && !revealed) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = onNext?.let { action -> { action() } },
                onDone = onDone?.let { action -> { action() } },
            ),
            trailingIcon = if (isPassword && onToggleRevealed != null) {
                {
                    IconButton(onClick = onToggleRevealed) {
                        Icon(
                            imageVector = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(
                                if (revealed) R.string.connect_password_hide else R.string.connect_password_show,
                            ),
                            tint = colors.textSecondary,
                        )
                    }
                }
            } else {
                null
            },
            shape = MaterialTheme.shapes.small,
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
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.danger,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
