package com.automatelinux.pt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.journey.StartedTrip
import com.automatelinux.pt.util.LocalAppStrings

/**
 * Asked when the rider picks a finished trip with an end that was the phone's GPS
 * fix. That end means "where I was then", and the rider is usually somewhere else
 * now — only they know whether they mean the old spot or their current one, so the
 * app asks rather than guessing either way.
 *
 * One GPS end: the two places are the two buttons, one tap answers it. Both ends
 * GPS: a choice per end and a plan button, which stays off while both are "now" —
 * that is one point twice, nothing to route.
 *
 * [canUseGps] false (permission off): "now" cannot be fetched, so it is shown
 * disabled with the reason instead of failing silently after the tap.
 */
@Composable
fun GpsEndChoiceDialog(
    trip: StartedTrip,
    canUseGps: Boolean,
    onChoose: (originNow: Boolean, destinationNow: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val originThen = strings.gpsWhereIWas(trip.originLabel ?: strings.placeYourLocation)
    val destinationThen = strings.gpsWhereIWas(trip.destinationLabel ?: strings.placeYourDestination)
    val both = trip.originFromGps && trip.destinationFromGps

    if (!both) {
        val isOrigin = trip.originFromGps
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (isOrigin) strings.gpsChooseStart else strings.gpsChooseDestination) },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(strings.gpsTripExplain, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { onChoose(isOrigin, !isOrigin) },
                        enabled = canUseGps,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.gpsWhereIAmNow) }
                    if (!canUseGps) {
                        Text(
                            strings.gpsPermissionOff,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    OutlinedButton(
                        onClick = { onChoose(false, false) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isOrigin) originThen else destinationThen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
        )
        return
    }

    var originNow by remember { mutableStateOf(canUseGps) }
    var destinationNow by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.gpsChooseBoth) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.gpsTripExplain, style = MaterialTheme.typography.bodyMedium)
                Text(strings.from, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp))
                ChoiceRow(strings.gpsWhereIAmNow, originNow, canUseGps) { originNow = true }
                ChoiceRow(originThen, !originNow, true) { originNow = false }
                Text(strings.to, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp))
                ChoiceRow(strings.gpsWhereIAmNow, destinationNow, canUseGps) { destinationNow = true }
                ChoiceRow(destinationThen, !destinationNow, true) { destinationNow = false }
                if (!canUseGps) {
                    Text(
                        strings.gpsPermissionOff,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onChoose(originNow, destinationNow) },
                enabled = !(originNow && destinationNow)
            ) { Text(strings.gpsPlan) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}
