package com.automatelinux.pt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.util.LocalAppStrings

/**
 * Up-front pricing disclosure.
 *
 * Shown on first launch and always reachable from settings. The promise here is
 * the product's contract with the user: everything is free now, it will not stay
 * free forever, and nobody is ever charged without warning. Deliberately states
 * a ceiling ("no more than one ride's fare") rather than an exact figure, so the
 * price can be set later without breaking the word given here.
 */
@Composable
fun PricingNoticeDialog(onDismiss: () -> Unit) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.pricingNoticeTitle,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(strings.pricingNoticeFree, style = MaterialTheme.typography.bodyMedium)
                Text(strings.pricingNoticeFuture, style = MaterialTheme.typography.bodyMedium)
                Text(strings.pricingNoticeWarning, style = MaterialTheme.typography.bodyMedium)
                Text(
                    strings.pricingNoticeFounder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.pricingNoticeAcknowledge)
            }
        }
    )
}

/**
 * Same promise, rendered inline for the settings sheet, so the disclosure stays
 * findable after the first-launch dialog has been dismissed.
 */
@Composable
fun PricingNoticeSection(modifier: Modifier = Modifier) {
    val strings = LocalAppStrings.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(strings.pricingSectionTitle, style = MaterialTheme.typography.titleMedium)
        Text(strings.pricingSectionBody, style = MaterialTheme.typography.bodySmall)
        Text(
            strings.pricingNoticeFounder,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
