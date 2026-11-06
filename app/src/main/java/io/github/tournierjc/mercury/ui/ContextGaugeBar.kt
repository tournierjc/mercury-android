package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.dto.ContextSnapshotDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * The context gauge: how full the model's view of this conversation is.
 *
 * Sits above the composer like the code-mode bar — the number answers "how
 * close am I to the keeper rewriting history", a question asked before
 * sending. Hidden when the daemon has no keeper route (an older runtime) or
 * the conversation is empty. Tap [onClick] (the `/compact` gauge in chat)
 * when the daemon knows the command.
 */
@Composable
fun ContextGaugeBar(
    snapshot: ContextSnapshotDto?,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (snapshot == null || snapshot.isEmpty) return
    val colors = LocalMercuryColors.current
    // One threshold, two words: the number speaks before 80%, after it the bar is amber-red.
    val hot = snapshot.fraction >= 0.8f
    val track = if (hot) colors.warning else colors.accent
    val label = remember(snapshot.viewTokens, snapshot.budget, snapshot.percent) {
        "${snapshot.percent}% · ${fmtK(snapshot.viewTokens)}/${fmtK(snapshot.budget)}"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.surfaceMuted),
        ) {
            LinearProgressIndicator(
                progress = { snapshot.fraction },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = track,
                trackColor = Color.Transparent,
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (hot) FontWeight.SemiBold else FontWeight.Normal,
            color = if (hot) colors.warning else colors.textSecondary,
        )
        if (snapshot.needsCompression) {
            Text(
                text = stringResource(R.string.context_gauge_next_turn),
                fontSize = 11.sp,
                color = colors.warning,
            )
        }
    }
}

/** 12 400 → "12k" — the gauge has no room for thousands separators. */
internal fun fmtK(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
    tokens >= 1_000 -> "${tokens / 1_000}k"
    else -> tokens.toString()
}
