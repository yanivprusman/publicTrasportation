package com.automatelinux.pt.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.coroutines.delay

data class PreSuggestion(
    val suggestion: GeocodeSuggestion,
    val icon: ImageVector,
    val label: String? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> AutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onSearch: suspend (String) -> List<T>,
    onSelect: (T) -> Unit,
    itemContent: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    onClear: () -> Unit = {},
    leadingIcon: @Composable (() -> Unit)? = null,
    debounceMs: Long = 300,
    suppressSearch: Boolean = false,
    preSuggestions: List<PreSuggestion> = emptyList(),
    onLongPressSuggestion: ((GeocodeSuggestion) -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    var suggestions by remember { mutableStateOf<List<T>>(emptyList()) }
    var showDropdown by remember { mutableStateOf(false) }
    var showPreSuggestions by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    var justSelected by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (justSelected || suppressSearch) {
            justSelected = false
            // A programmatically-set value must still close an open dropdown,
            // or it lingers over the panel (e.g. quick-destination chips).
            if (value.isNotEmpty()) {
                showPreSuggestions = false
                showDropdown = false
                suggestions = emptyList()
            }
            return@LaunchedEffect
        }
        if (value.length < 2) {
            suggestions = emptyList()
            showDropdown = false
            if (value.isEmpty() && hasFocus && preSuggestions.isNotEmpty()) {
                showPreSuggestions = true
            }
            return@LaunchedEffect
        }
        showPreSuggestions = false
        delay(debounceMs)
        val results = onSearch(value)
        suggestions = results
        showDropdown = results.isNotEmpty() && hasFocus
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { text ->
                onValueChange(text)
                if (text.isEmpty()) {
                    suggestions = emptyList()
                    showDropdown = false
                    if (hasFocus && preSuggestions.isNotEmpty()) {
                        showPreSuggestions = true
                    }
                } else {
                    showPreSuggestions = false
                }
            },
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    hasFocus = state.isFocused
                    if (state.isFocused && value.isEmpty() && preSuggestions.isNotEmpty()) {
                        showPreSuggestions = true
                    }
                    if (!state.isFocused) {
                        showDropdown = false
                        showPreSuggestions = false
                    }
                },
            singleLine = true,
            leadingIcon = leadingIcon,
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = {
                        onValueChange("")
                        suggestions = emptyList()
                        showDropdown = false
                        onClear()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = strings.clear)
                    }
                }
            }
        )

        if (showPreSuggestions && preSuggestions.isNotEmpty() && !showDropdown) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    itemsIndexed(preSuggestions) { index, item ->
                        @Suppress("UNCHECKED_CAST")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        justSelected = true
                                        showPreSuggestions = false
                                        (item.suggestion as? T)?.let { onSelect(it) }
                                            ?: run {
                                                onValueChange(item.suggestion.name)
                                                onSelect(item.suggestion as T)
                                            }
                                    },
                                    onLongClick = {
                                        onLongPressSuggestion?.invoke(item.suggestion)
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                if (item.label != null) {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = item.suggestion.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (index < preSuggestions.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }

        if (showDropdown && suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    itemsIndexed(suggestions) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        justSelected = true
                                        onSelect(item)
                                        showDropdown = false
                                        suggestions = emptyList()
                                    },
                                    onLongClick = {
                                        val geo = item as? GeocodeSuggestion
                                        if (geo != null) {
                                            onLongPressSuggestion?.invoke(geo)
                                        }
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            itemContent(item)
                        }
                        if (index < suggestions.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
