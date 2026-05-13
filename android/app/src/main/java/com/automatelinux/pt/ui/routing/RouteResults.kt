package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.RouteResult

@Composable
fun RouteResults(
    results: RouteResult?,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    loading: Boolean,
    error: String?,
    onRetry: (() -> Unit)? = null,
    cardOpacity: Float = 0.6f,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Searching routes...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (onRetry != null) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }
            results != null && results.itineraries.isNotEmpty() -> {
                Column {
                    results.itineraries.forEachIndexed { index, itinerary ->
                        ItineraryCard(
                            itinerary = itinerary,
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                            cardOpacity = cardOpacity
                        )
                    }
                }
            }
        }
    }
}
