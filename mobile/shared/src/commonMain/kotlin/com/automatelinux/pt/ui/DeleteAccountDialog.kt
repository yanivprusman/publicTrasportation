package com.automatelinux.pt.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.automatelinux.pt.util.LocalAppStrings

/**
 * Confirms account deletion.
 *
 * Says what actually goes, in the same words the privacy policy and the web
 * page use, because a person about to erase their account deserves to know the
 * scope before tapping and not after. The destructive action is coloured as
 * destructive and sits where the affirmative button sits; "Cancel" is the plain
 * one. Nothing here is a spinner-gated flow: deletion is one request, and the
 * caller reports the outcome.
 */
@Composable
fun DeleteAccountDialog(
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(strings.deleteAccountTitle) },
        text = { Text(strings.deleteAccountBody) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(
                    strings.deleteAccountConfirm,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(strings.cancel)
            }
        }
    )
}
