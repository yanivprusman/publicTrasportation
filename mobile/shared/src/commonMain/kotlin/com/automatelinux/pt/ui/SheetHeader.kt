package com.automatelinux.pt.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.automatelinux.pt.ui.components.PtSegment
import com.automatelinux.pt.ui.components.PtSegmented
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
            width = 44.dp,
            height = 4.dp,
            color = MaterialTheme.colorScheme.outline,
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
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // One of three views: a segmented track, which says so. It takes the width
        // the gear and the AI button leave, and scrolls sideways inside itself rather
        // than squeezing a label onto two lines on a narrow phone.
        val tabs = listOf(ActiveTab.ROUTE, ActiveTab.ARRIVALS, ActiveTab.LINES)
        PtSegmented(
            segments = listOf(
                PtSegment(strings.routePlanner),
                PtSegment(strings.stationArrivals),
                PtSegment(strings.linesBrowser)
            ),
            selectedIndex = tabs.indexOf(activeTab),
            onSelect = { onTabChange(tabs[it]) },
            modifier = Modifier.weight(1f)
        )
        // Not a fourth view but a different way in, so it is a button beside the
        // track and never wears the picked state.
        if (onOpenAi != null) {
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(onClick = onOpenAi)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    strings.aiChip,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = strings.settings,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
