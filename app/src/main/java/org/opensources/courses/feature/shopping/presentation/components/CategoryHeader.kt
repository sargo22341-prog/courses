package org.opensources.courses.feature.shopping.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opensources.courses.core.designsystem.theme.isDark
import org.opensources.courses.feature.catalog.domain.GroceryCategory

/** Section title in the list: tinted pictogram, name in the section colour and a thin rule. */
@Composable
fun CategoryHeader(
    category: GroceryCategory,
    modifier: Modifier = Modifier,
) {
    val style = category.style()
    val background = MaterialTheme.colorScheme.background
    // Accents are dark tones made for the light theme; lightened, they stay readable on the dark one.
    val accent = if (MaterialTheme.colorScheme.isDark) lerp(style.accent, Color.White, DARK_THEME_LIGHTENING) else style.accent
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp, bottom = 2.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(lerp(background, accent, BADGE_TINT), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(style.icon), fontSize = 16.sp, modifier = Modifier.clearAndSetSemantics {})
        }
        Spacer(Modifier.width(12.dp))
        Text(stringResource(style.label), style = MaterialTheme.typography.titleSmall, color = accent)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(2.dp).background(accent.copy(alpha = RULE_ALPHA), CircleShape))
    }
}

private const val DARK_THEME_LIGHTENING = 0.45f
private const val BADGE_TINT = 0.2f
private const val RULE_ALPHA = 0.35f
