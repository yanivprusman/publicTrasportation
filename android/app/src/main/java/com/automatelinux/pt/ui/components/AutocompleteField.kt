package com.automatelinux.pt.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.coroutines.delay

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
    suppressSearch: Boolean = false
) {
    val strings = LocalAppStrings.current
    var suggestions by remember { mutableStateOf<List<T>>(emptyList()) }
    var showDropdown by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    var justSelected by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (justSelected || suppressSearch) {
            justSelected = false
            return@LaunchedEffect
        }
        if (value.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
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
                }
            },
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    hasFocus = state.isFocused
                    if (!state.isFocused) showDropdown = false
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
                                .clickable {
                                    justSelected = true
                                    onSelect(item)
                                    showDropdown = false
                                    suggestions = emptyList()
                                }
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
