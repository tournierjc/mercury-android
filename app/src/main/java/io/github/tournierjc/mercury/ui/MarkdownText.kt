package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tournierjc.mercury.markdown.MdBlock
import io.github.tournierjc.mercury.diff.Diff
import io.github.tournierjc.mercury.openui.OpenUi
import io.github.tournierjc.mercury.markdown.MdSpan
import io.github.tournierjc.mercury.markdown.MiniMarkdown
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalMercuryColors.current.textPrimary,
    /** The answer is still arriving: an openui block draws what is complete instead of waiting. */
    streaming: Boolean = false,
) {
    val colors = LocalMercuryColors.current
    val blocks = remember(text) { MiniMarkdown.parse(text) }
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            val spacing = if (index == 0) 0.dp else 10.dp
            when (block) {
                is MdBlock.Paragraph -> Text(
                    text = annotated(block.spans, color, colors.accent),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = spacing),
                )

                is MdBlock.Heading -> Text(
                    text = annotated(block.spans, color, colors.accent),
                    style = MaterialTheme.typography.headlineSmall,
                    color = color,
                    modifier = Modifier.padding(top = spacing),
                )

                is MdBlock.Bullets -> Column(modifier = Modifier.padding(top = spacing)) {
                    block.items.forEach { item ->
                        Row(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(
                                text = annotated(item, color, colors.accent),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                is MdBlock.Code -> when {
                    // A rendered answer from the openui contract: components instead of source.
                    block.language.trim().lowercase() == OpenUi.FENCE -> Column(modifier = Modifier.padding(top = spacing)) {
                        OpenUiBlock(spec = block.content, streaming = streaming)
                    }
                    // A patch: the same fence the dashboard's diff-view plugin claims.
                    block.language.trim().lowercase() == Diff.FENCE -> Column(modifier = Modifier.padding(top = spacing)) {
                        DiffBlock(text = block.content)
                    }
                    // A model that forgot the tag: claim the block only when it really is a spec.
                    block.language.isBlank() && !streaming && OpenUi.looksLikeSpec(block.content) ->
                        Column(modifier = Modifier.padding(top = spacing)) {
                            OpenUiBlock(spec = block.content, streaming = false)
                        }
                    else -> Column(
                        modifier = Modifier
                            .padding(top = spacing)
                            .fillMaxWidth()
                            .background(colors.surface, MaterialTheme.shapes.extraSmall)
                            .padding(14.dp),
                    ) {
                        if (block.language.isNotBlank()) {
                            Text(
                                text = block.language.lowercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        SelectionContainer {
                            Text(
                                text = block.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textPrimary,
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun annotated(spans: List<MdSpan>, color: Color, link: Color): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        val target = span.url
        if (target != null) {
            // A LinkAnnotation is what makes the run tappable at all: styled text that looks like a
            // link but is not one is a dead end, and the platform opens what it points at.
            withLink(
                LinkAnnotation.Url(
                    url = target,
                    styles = TextLinkStyles(
                        style = SpanStyle(color = link, textDecoration = TextDecoration.Underline),
                    ),
                ),
            ) { append(span.text) }
            return@forEach
        }
        val style = SpanStyle(
            color = if (span.code) LocalCodeColor else color,
            fontWeight = if (span.bold) FontWeight.SemiBold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
            fontSize = if (span.code) 15.sp else TextUnit.Unspecified,
        )
        withStyle(style) { append(span.text) }
    }
}

private val LocalCodeColor = Color(0xFF9CE8FF)
