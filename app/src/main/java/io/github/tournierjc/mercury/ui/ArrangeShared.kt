package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.data.BotJson

/**
 * La barre du rangement, telle que la PR #116 l'a réglée dans le menu : un titre, « Terminé »,
 * et rien d'autre. Le menu (ThreadDrawer) et l'écran Équipiers (BotsScreen) rendent la même —
 * deux manières de ranger un même équipage doivent se resser, sinon elles ne sont pas le même rangement.
 */
@Composable
fun ArrangeBar(title: String, doneLabel: String, onDone: () -> Unit) {
    val colors = io.github.tournierjc.mercury.ui.theme.LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDone) {
            Text(
                text = doneLabel,
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
            )
        }
    }
}

/**
 * Les deux flèches du rangement. Une seule implémentation : le menu les a eues en premier (PR #115),
 * l'écran Équipiers doit les montrer pareil.
 */
@Composable
fun MoveArrows(
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val colors = io.github.tournierjc.mercury.ui.theme.LocalMercuryColors.current
    val upLabel = androidx.compose.ui.res.stringResource(io.github.tournierjc.mercury.R.string.drawer_move_up)
    val downLabel = androidx.compose.ui.res.stringResource(io.github.tournierjc.mercury.R.string.drawer_move_down)
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Sans taille imposée : la cible de doigt de l'IconButton par défaut atteint 48 dp.
        androidx.compose.material3.IconButton(onClick = onUp, enabled = canUp) {
            Icon(
                Icons.Rounded.KeyboardArrowUp,
                contentDescription = upLabel,
                tint = if (canUp) colors.textPrimary else colors.hairlineStrong,
            )
        }
        androidx.compose.material3.IconButton(onClick = onDown, enabled = canDown) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = downLabel,
                tint = if (canDown) colors.textPrimary else colors.hairlineStrong,
            )
        }
    }
}

/**
 * Une rangée d'équipier en mode rangement, dans la hiérarchie que l'œil connaît : un retrait pour
 * l'équipier sous son leader, et les flèches à droite. Conçue à partir de la rangée du menu (PR #116)
 * et de celle de l'écran Équipiers — les deux écartaient les mêmes éléments aux mêmes places.
 */
@Composable
fun ArrangeRow(
    bot: kotlinx.serialization.json.JsonObject,
    /** Retrait de l'équipier sous son leader (le menu réserve 24 dp, l'écran 40 — on prend le menu, qui est la référence du rangement). */
    under: Boolean,
    /** L'état du bot, sous le nom — le même texte que la rangée de lecture. */
    stateLine: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = io.github.tournierjc.mercury.ui.theme.LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .heightIn(min = 48.dp)
            .padding(start = if (under) 24.dp else 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            // Le rangement ne navigue pas : une rangée qui bouge ET s'ouvre ne fait ni l'un ni l'autre.
            .semantics { contentDescription = BotJson.name(bot) },
    ) {
        Spacer(Modifier.width(36.dp))
        io.github.tournierjc.mercury.ui.BotFace(bot, null, small = true)
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = BotJson.name(bot),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (stateLine.isNotBlank()) {
                Text(
                    text = stateLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }
        trailing?.invoke()
    }
}
