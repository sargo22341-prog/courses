package org.opensources.courses.feature.shopping.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.ConfirmDialog

/** Shared by the "Achetés" header button and the footer menu: deleting never happens without it. */
@Composable
fun DeletePurchasedDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(R.string.shopping_delete_purchased_title),
        text = stringResource(R.string.shopping_delete_purchased_body),
        confirmLabel = stringResource(R.string.action_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
