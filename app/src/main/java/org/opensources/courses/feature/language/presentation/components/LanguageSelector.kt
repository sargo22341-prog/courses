package org.opensources.courses.feature.language.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.feature.language.domain.AppLanguage

/** Every [AppLanguage], each written in itself so that anyone finds their own whatever is displayed. */
@Composable
fun LanguageSelector(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
) {
    FlowRow(modifier = modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = horizontalArrangement) {
        AppLanguage.entries.forEach { language ->
            val isSelected = language == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(language) },
                label = { Text(stringResource(language.nameRes())) },
                leadingIcon =
                    if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
            )
        }
    }
}

@StringRes
private fun AppLanguage.nameRes(): Int =
    when (this) {
        AppLanguage.GERMAN -> R.string.language_name_de
        AppLanguage.ENGLISH -> R.string.language_name_en
        AppLanguage.SPANISH -> R.string.language_name_es
        AppLanguage.FRENCH -> R.string.language_name_fr
        AppLanguage.ITALIAN -> R.string.language_name_it
        AppLanguage.PORTUGUESE -> R.string.language_name_pt
    }
