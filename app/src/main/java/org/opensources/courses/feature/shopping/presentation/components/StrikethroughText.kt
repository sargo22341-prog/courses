package org.opensources.courses.feature.shopping.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A name crossed out by a line drawn from its start, line after line, when it is [struck]; the line
 * draws back when it is not. Shown already crossed out when it first appears struck: only a change
 * is animated. Drawn, not recomposed, at each frame.
 */
@Composable
internal fun StrikethroughText(
    text: String,
    struck: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(if (struck) 1f else 0f) }
    LaunchedEffect(struck) { progress.animateTo(if (struck) 1f else 0f, tween(STRIKE_MILLIS, easing = FastOutSlowInEasing)) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        modifier =
            modifier.drawWithContent {
                drawContent()
                layout?.let { drawStrike(it, progress.value, color) }
            },
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
    )
}

private fun DrawScope.drawStrike(
    layout: TextLayoutResult,
    progress: Float,
    color: Color,
) {
    if (progress <= 0f) return
    val lines = (0 until layout.lineCount).map { layout.getLineLeft(it) to layout.getLineRight(it) }
    var remaining = lines.sumOf { (left, right) -> (right - left).toDouble() }.toFloat() * progress
    lines.forEachIndexed { line, (left, right) ->
        if (remaining <= 0f) return
        val length = minOf(right - left, remaining)
        val baseline = layout.getLineBaseline(line)
        // Through the middle of lower-case letters, where a typed line-through would be.
        val y = baseline - (baseline - layout.getLineTop(line)) * STRIKE_HEIGHT
        drawLine(color, Offset(left, y), Offset(left + length, y), STRIKE_WIDTH.toPx(), StrokeCap.Round)
        remaining -= length
    }
}

private const val STRIKE_MILLIS = 280
private const val STRIKE_HEIGHT = 0.3f
private val STRIKE_WIDTH = 1.5.dp
