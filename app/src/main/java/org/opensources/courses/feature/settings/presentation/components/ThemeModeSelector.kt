package org.opensources.courses.feature.settings.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.theme.themeColorScheme
import org.opensources.courses.feature.settings.domain.ThemeMode

/**
 * Three choices on one row, each painted with the background of its theme; "Système" fades from
 * the light background to the dark one in its middle.
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
            label = stringResource(R.string.theme_light),
            background = SolidColor(light.background),
            labelColor = light.onBackground,
            selected = selected == ThemeMode.LIGHT,
            onClick = { onSelect(ThemeMode.LIGHT) },
            modifier = Modifier.weight(1f),
        )
        ThemeOption(
            label = stringResource(R.string.theme_dark),
            background = SolidColor(dark.background),
            labelColor = dark.onBackground,
            selected = selected == ThemeMode.DARK,
            onClick = { onSelect(ThemeMode.DARK) },
            modifier = Modifier.weight(1f),
        )
        ThemeOption(
            label = stringResource(R.string.theme_system),
            background =
                Brush.horizontalGradient(
                    0f to light.background,
                    FADE_START to light.background,
                    FADE_END to dark.background,
                    1f to dark.background,
                ),
            // Half light, half dark: the label sits on a chip of the current theme to stay readable.
            labelColor = MaterialTheme.colorScheme.onSurface,
            labelBackground = MaterialTheme.colorScheme.surface,
            selected = selected == ThemeMode.SYSTEM,
            onClick = { onSelect(ThemeMode.SYSTEM) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ThemeOption(
    label: String,
    background: Brush,
    labelColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelBackground: Color = Color.Transparent,
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier =
            modifier
                .height(72.dp)
                .clip(shape)
                .background(background)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = shape,
                ).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.clip(MaterialTheme.shapes.small).background(labelBackground).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = labelColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = labelColor, maxLines = 1)
        }
    }
}

private const val FADE_START = 0.3f
private const val FADE_END = 0.7f
