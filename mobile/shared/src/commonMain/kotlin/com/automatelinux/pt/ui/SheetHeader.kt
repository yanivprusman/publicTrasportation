package com.automatelinux.pt.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.util.AppStrings

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SheetDragHandleRow(
    strings: AppStrings,
    loading: Boolean,
    onDebugFill: () -> Unit,
    onDebugLongClick: () -> Unit,
    showDebugFill: Boolean = true
) {
    // The handle row exists to say "drag me" — one prominent, centered handle and
    // nothing competing with it. (The opacity slider that used to live here is a
    // settings concern; the gear menu opens the full opacity controls.)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        BottomSheetDefaults.DragHandle(
            width = 56.dp,
            height = 5.dp,
            modifier = Modifier.align(Alignment.Center)
        )
        if (showDebugFill) {
            Icon(
                Icons.Default.BugReport,
                contentDescription = strings.debugFill,
                tint = if (loading) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.38f)
                       else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(36.dp)
                    .combinedClickable(
                        enabled = !loading,
                        onClick = onDebugFill,
                        onLongClick = onDebugLongClick
                    )
                    .padding(6.dp)
            )
        }
    }
}

@Composable
fun SheetTabRow(
    activeTab: ActiveTab,
    onTabChange: (ActiveTab) -> Unit,
    strings: AppStrings,
    showOpacitySlider: Boolean,
    onToggleOpacitySlider: () -> Unit,
    locationIconStyle: String,
    onLocationIconStyleChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    journeyAlertsEnabled: Boolean,
    onJourneyAlertsChange: (Boolean) -> Unit,
    onDeleteAccount: () -> Unit,
    /** Opens the AI navigator; null hides its chip (a build without the AI server). */
    onOpenAi: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The chips scroll sideways as a group and never wrap their labels: on a
        // narrow phone four chips + the gear don't fit, and Compose used to squeeze
        // the last chip until "AI" broke onto two lines and the gear left the screen.
        // The gear sits outside the scroller so it is always reachable.
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
        FilterChip(
            selected = activeTab == ActiveTab.ROUTE,
            onClick = { onTabChange(ActiveTab.ROUTE) },
            label = { Text(strings.routePlanner, maxLines = 1, softWrap = false) },
            modifier = Modifier.padding(end = 8.dp)
        )
        FilterChip(
            selected = activeTab == ActiveTab.ARRIVALS,
            onClick = { onTabChange(ActiveTab.ARRIVALS) },
            label = { Text(strings.stationArrivals, maxLines = 1, softWrap = false) }
        )
        FilterChip(
            selected = activeTab == ActiveTab.LINES,
            onClick = { onTabChange(ActiveTab.LINES) },
            label = { Text(strings.linesBrowser, maxLines = 1, softWrap = false) },
            modifier = Modifier.padding(start = 8.dp)
        )
        if (onOpenAi != null) {
            FilterChip(
                selected = false,
                onClick = onOpenAi,
                label = { Text(strings.aiChip, maxLines = 1, softWrap = false) },
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = strings.settings,
                    modifier = Modifier.size(22.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (showOpacitySlider) strings.hideOpacitySettings
                            else strings.opacitySettings
                        )
                    },
                    onClick = {
                        onToggleOpacitySlider()
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (locationIconStyle == "dot") strings.locationIconPerson
                            else strings.locationIconDot
                        )
                    },
                    onClick = {
                        val newStyle = if (locationIconStyle == "dot") "person" else "dot"
                        onLocationIconStyleChange(newStyle)
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (language == "he") "English"
                            else "עברית"
                        )
                    },
                    onClick = {
                        val newLang = if (language == "he") "en" else "he"
                        onLanguageChange(newLang)
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (journeyAlertsEnabled) strings.journeyAlertsOff
                            else strings.journeyAlertsOn
                        )
                    },
                    onClick = {
                        onJourneyAlertsChange(!journeyAlertsEnabled)
                        menuExpanded = false
                    }
                )
                // Last, and only here: deleting an account is not a setting the
                // hand slips onto while reaching for the language toggle.
                DropdownMenuItem(
                    text = {
                        Text(
                            strings.deleteAccount,
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDeleteAccount()
                    }
                )
            }
        }
    }
}
