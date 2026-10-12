package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker as M3TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

enum class TimeMode { NOW, DEPART, ARRIVE }

private val sysTz get() = TimeZone.currentSystemDefault()

// Material 3's DatePicker counts days in UTC: the millis it takes and gives back
// are UTC midnights. Seeding it with a LOCAL midnight (21:00 UTC the evening
// before, in Israel) made it highlight yesterday and hand yesterday back on
// confirm, so "depart at" with the date untouched searched the previous day
// (found 2026-10-12: results stamped אתמול / כבר יצא). The date crosses this
// boundary in UTC both ways; only the final LocalDateTime → Instant is local.
private val pickerTz = TimeZone.UTC

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSection(
    departureTime: Instant?,
    onTimeChange: (Instant?) -> Unit,
    arriveBy: Boolean,
    onArriveByChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    // Derived from actual search state so the chips can't lie: Earlier/Later set a
    // departure time programmatically, and canceling the pickers leaves no time set.
    val mode = when {
        departureTime == null -> TimeMode.NOW
        arriveBy -> TimeMode.ARRIVE
        else -> TimeMode.DEPART
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(Clock.System.todayIn(sysTz)) }
    var selectedTime by remember { mutableStateOf(Clock.System.now().toLocalDateTime(sysTz).time) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = mode == TimeMode.NOW,
                onClick = {
                    onTimeChange(null)
                    onArriveByChange(false)
                },
                label = { Text(strings.now) }
            )
            FilterChip(
                selected = mode == TimeMode.DEPART,
                onClick = {
                    onArriveByChange(false)
                    showDatePicker = true
                },
                label = { Text(strings.departAt) }
            )
            FilterChip(
                selected = mode == TimeMode.ARRIVE,
                onClick = {
                    onArriveByChange(true)
                    showDatePicker = true
                },
                label = { Text(strings.arriveBy) }
            )
        }

        if (departureTime != null) {
            // Same words as the folded summary — "מחר 06:46", "אתמול 06:46", or just the
            // time for today — not an English "Sun, Oct 11" in the middle of a Hebrew form.
            val local = departureTime.toLocalDateTime(sysTz)
            val hm = local.time.toString().take(5)
            val day = departureDayLabel(departureTime.toString(), Clock.System.now(), strings)
            Text(
                text = if (day != null) "$day $hm" else hm,
                modifier = Modifier.padding(top = 4.dp),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDayIn(pickerTz).toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(pickerTz).date
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) { Text(strings.next) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(strings.cancel) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute,
            is24Hour = true
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime = LocalTime(timePickerState.hour, timePickerState.minute)
                    val instant = LocalDateTime(selectedDate, selectedTime).toInstant(sysTz)
                    onTimeChange(instant)
                    showTimePicker = false
                }) { Text(strings.ok) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(strings.cancel) }
            },
            text = { M3TimePicker(state = timePickerState) }
        )
    }
}
