package org.opensources.courses.feature.settings.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.theme.themeColorScheme
import org.opensources.courses.feature.settings.domain.ThemeMode

/**
 * Three choices on one row, each painted with the background of its theme; "Système" is cut by a
 * slash, light on one side and dark on the other.
 */
@Composable
fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val light = themeColorScheme(dark = false)
    val dark = themeColorScheme(dark = true)
    Row(modifier = modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeOption(
            selected = selected == ThemeMode.LIGHT,
            onClick = { onSelect(ThemeMode.LIGHT) },
            background = light.background,
            modifier = Modifier.weight(1f),
        ) { ThemeLabel(stringResource(R.string.theme_light), light.onBackground, selected == ThemeMode.LIGHT) }
        ThemeOption(
            selected = selected == ThemeMode.DARK,
            onClick = { onSelect(ThemeMode.DARK) },
            background = dark.background,
            modifier = Modifier.weight(1f),
        ) { ThemeLabel(stringResource(R.string.theme_dark), dark.onBackground, selected == ThemeMode.DARK) }
        SystemThemeOption(
            selected = selected == ThemeMode.SYSTEM,
            onClick = { onSelect(ThemeMode.SYSTEM) },
            lightBackground = light.background,
            lightLabel = light.onBackground,
            darkBackground = dark.background,
            darkLabel = dark.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SystemThemeOption(
    selected: Boolean,
    onClick: () -> Unit,
    lightBackground: Color,
    lightLabel: Color,
    darkBackground: Color,
    darkLabel: Color,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.theme_system)
    ThemeOption(
        selected = selected,
        onClick = onClick,
        background = lightBackground,
        modifier =
            modifier.drawWithCache {
                val darkSide = slashSide(size, dark = true)
                onDrawBehind { drawPath(darkSide, darkBackground) }
            },
    ) {
        // The slash also cuts the label: each part is written in the colour readable on its side.
        ThemeLabel(label, lightLabel, selected, Modifier.matchParentSize().clipToSlashSide(dark = false))
        ThemeLabel(label, darkLabel, selected, Modifier.matchParentSize().clipToSlashSide(dark = true).clearAndSetSemantics {})
    }
}

@Composable
private fun ThemeOption(
    selected: Boolean,
    onClick: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier =
            Modifier
                .height(OPTION_HEIGHT)
                .clip(shape)
                .drawWithCache { onDrawBehind { drawRect(background) } }
                .then(modifier)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = shape,
                ).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun ThemeLabel(
    label: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
        }
    }
}

private fun Modifier.clipToSlashSide(dark: Boolean): Modifier =
    drawWithCache {
        val side = slashSide(size, dark)
        onDrawWithContent { clipPath(side) { this@onDrawWithContent.drawContent() } }
    }

/** One side of a "/" crossing the middle of [size]: the light side is left of it, the dark side right. */
private fun slashSide(
    size: Size,
    dark: Boolean,
): Path {
    val run = size.height * SLASH_RUN
    val top = size.width / 2 + run
    val bottom = size.width / 2 - run
    val edge = if (dark) size.width else 0f
    return Path().apply {
        moveTo(edge, 0f)
        lineTo(top, 0f)
        lineTo(bottom, size.height)
        lineTo(edge, size.height)
        close()
    }
}

private val OPTION_HEIGHT = 48.dp

/** Horizontal distance from the centre to each end of the slash, relative to the height. */
private const val SLASH_RUN = 0.25f
