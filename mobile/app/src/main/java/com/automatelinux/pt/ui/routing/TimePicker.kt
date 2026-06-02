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
import androidx.compose.material3.TimePickerState
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class TimeMode { NOW, DEPART, ARRIVE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSection(
    departureTime: ZonedDateTime?,
    onTimeChange: (ZonedDateTime?) -> Unit,
    arriveBy: Boolean,
    onArriveByChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var mode by remember { mutableStateOf(TimeMode.NOW) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTime by remember { mutableStateOf(LocalTime.now()) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = mode == TimeMode.NOW,
                onClick = {
                    mode = TimeMode.NOW
                    onTimeChange(null)
                    onArriveByChange(false)
                },
                label = { Text(strings.now) }
            )
            FilterChip(
                selected = mode == TimeMode.DEPART,
                onClick = {
                    mode = TimeMode.DEPART
                    onArriveByChange(false)
                    showDatePicker = true
                },
                label = { Text(strings.departAt) }
            )
            FilterChip(
                selected = mode == TimeMode.ARRIVE,
                onClick = {
                    mode = TimeMode.ARRIVE
                    onArriveByChange(true)
                    showDatePicker = true
                },
                label = { Text(strings.arriveBy) }
            )
        }

        if (mode != TimeMode.NOW && departureTime != null) {
            Text(
                text = departureTime.format(DateTimeFormatter.ofPattern("EEE, MMM d  HH:mm")),
                modifier = Modifier.padding(top = 4.dp),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
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
                    selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    val zdt = ZonedDateTime.of(selectedDate, selectedTime, ZoneId.systemDefault())
                    onTimeChange(zdt)
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
