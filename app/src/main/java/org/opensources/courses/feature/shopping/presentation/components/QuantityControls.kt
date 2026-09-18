package org.opensources.courses.feature.shopping.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.opensources.courses.R
import org.opensources.courses.feature.shopping.domain.QuantityFormatter
import org.opensources.courses.feature.shopping.domain.ShoppingItem
import org.opensources.courses.feature.shopping.domain.canDecreaseQuantity
import java.util.Locale

/** "−  2  +" at the end of a row still to buy; the steps are the domain's ([canDecreaseQuantity]). */
@Composable
internal fun QuantityControls(
    item: ShoppingItem,
    locale: Locale,
    onChangeQuantity: (ShoppingItem, increase: Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onChangeQuantity(item, false)
            },
            enabled = item.canDecreaseQuantity,
        ) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.shopping_quantity_decrease, item.name))
        }
        QuantityText(item, locale)
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onChangeQuantity(item, true)
            },
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shopping_quantity_increase, item.name))
        }
    }
}

/** The quantity, rolling up or down when it changes. */
@Composable
internal fun QuantityText(
    item: ShoppingItem,
    locale: Locale,
) {
    RollingValue(item.quantity) { quantity ->
        Text(
            text = QuantityFormatter.format(quantity, item.unit, locale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
