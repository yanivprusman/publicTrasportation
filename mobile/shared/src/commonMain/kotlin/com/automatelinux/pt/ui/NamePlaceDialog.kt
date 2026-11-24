package com.automatelinux.pt.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.util.LocalAppStrings

/**
 * Names the destination for this phone only. [currentName] is the name it already
 * has here (null = none yet); when there is one, the dialog can also forget it.
 */
@Composable
fun NamePlaceDialog(
    currentName: String?,
    onSave: (String) -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    var name by rememberSaveable { mutableStateOf(currentName ?: "") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = { if (name.isNotBlank()) onSave(name.trim()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.nameThisPlace) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    placeholder = { Text(strings.placeNameHint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("place-name-input")
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        strings.placeNameOnlyOnPhone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = submit,
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("place-name-save")
            ) { Text(strings.save) }
        },
        dismissButton = {
            if (currentName != null) {
                TextButton(onClick = onForget, modifier = Modifier.testTag("place-name-forget")) {
                    Text(strings.forgetPlaceName, color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("place-name-cancel")) {
                    Text(strings.cancel)
                }
            }
        }
    )
}
