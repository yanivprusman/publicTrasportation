package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.data.model.LineSuggestion
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.ui.map.getModeColorWithRoute
import com.automatelinux.pt.ui.map.onColorFor
import com.automatelinux.pt.util.LocalAppStrings

/**
 * "Which bus are you on?" — the rider types the line; the lines passing here now, going
 * their way, are offered underneath. Nothing is chosen for them: two lines share every
 * stretch of an intercity road, and only the rider knows which one they boarded.
 *
 * Letters are allowed ("27א" is a Be'er Sheva line), so the keyboard is a text one.
 */
@Composable
fun RidingLineDialog(
    suggestions: List<LineSuggestion>?,
    loading: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    var line by rememberSaveable { mutableStateOf("") }
    // Until the first fix arrives there is no bus position to plan from.
    val canPick = !loading
    val submit = { if (line.isNotBlank() && canPick) onPick(line.trim()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.whichBusTitle) },
        text = {
            Column {
                OutlinedTextField(
                    value = line,
                    onValueChange = { line = it.take(8) },
                    label = { Text(strings.lineNumberLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = strings.ridingSuggestionsHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                when {
                    loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.ridingLocating, style = MaterialTheme.typography.bodySmall)
                    }
                    suggestions.isNullOrEmpty() -> Text(
                        strings.ridingNoSuggestions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        suggestions.forEach { suggestion ->
                            SuggestionRow(suggestion, onClick = { onPick(suggestion.line) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = line.isNotBlank() && canPick) {
                Text(strings.planFromThisBus)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
private fun SuggestionRow(suggestion: LineSuggestion, onClick: () -> Unit) {
    val mode = TransitMode.entries.firstOrNull { it.name == suggestion.mode } ?: TransitMode.BUS
    val badgeColor = getModeColorWithRoute(mode, null)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(badgeColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = suggestion.line,
                color = onColorFor(badgeColor),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            // GTFS writes headsigns as "city_stop"; the underscore is not for people.
            Text(
                text = suggestion.headsign.replace('_', ' '),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (suggestion.nextStop.isNotBlank()) {
                Text(
                    text = suggestion.nextStop,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
