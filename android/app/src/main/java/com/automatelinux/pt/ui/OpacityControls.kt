package com.automatelinux.pt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.util.AppStrings

@Composable
fun OpacityControls(
    strings: AppStrings,
    sheetOpacity: Float,
    onSheetOpacityChange: (Float) -> Unit,
    onSheetOpacityFinished: () -> Unit,
    cardOpacity: Float,
    onCardOpacityChange: (Float) -> Unit,
    onCardOpacityFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                strings.sheet,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(40.dp)
            )
            Slider(
                value = sheetOpacity,
                onValueChange = onSheetOpacityChange,
                onValueChangeFinished = onSheetOpacityFinished,
                valueRange = 0.05f..1f,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(sheetOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                strings.cards,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(40.dp)
            )
            Slider(
                value = cardOpacity,
                onValueChange = onCardOpacityChange,
                onValueChangeFinished = onCardOpacityFinished,
                valueRange = 0.2f..1f,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(cardOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
