package com.automatelinux.pt.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
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
    onDebugLongClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        BottomSheetDefaults.DragHandle(
            modifier = Modifier.align(Alignment.Center)
        )
        Icon(
            Icons.Default.BugReport,
            contentDescription = strings.debugFill,
            tint = if (loading) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.38f)
                   else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .align(AbsoluteAlignment.CenterRight)
                .absolutePadding(right = 12.dp)
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
    onLanguageChange: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = activeTab == ActiveTab.ROUTE,
            onClick = { onTabChange(ActiveTab.ROUTE) },
            label = { Text(strings.routePlanner) },
            modifier = Modifier.padding(end = 8.dp)
        )
        FilterChip(
            selected = activeTab == ActiveTab.ARRIVALS,
            onClick = { onTabChange(ActiveTab.ARRIVALS) },
            label = { Text(strings.stationArrivals) }
        )
        FilterChip(
            selected = activeTab == ActiveTab.LINES,
            onClick = { onTabChange(ActiveTab.LINES) },
            label = { Text(strings.linesBrowser) },
            modifier = Modifier.padding(start = 8.dp)
        )
        Spacer(Modifier.weight(1f))
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
            }
        }
    }
}
