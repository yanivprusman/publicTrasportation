package com.automatelinux.pt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.util.LocalAppStrings

/**
 * Registration gate.
 *
 * Collects the minimum needed to keep the pricing promise: a way to reach the
 * user before the app becomes paid, and an identity that carries their
 * early-user standing to a new phone. Nothing else is asked for.
 *
 * Client-side checks here are only to catch typos before a round trip — the
 * daemon re-validates and normalises, and is the authority.
 */
@Composable
fun RegistrationScreen(
    onSubmit: (email: String, phone: String, onError: (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    fun looksLikeEmail(value: String): Boolean {
        val at = value.indexOf('@')
        return at > 0 && value.indexOf('.', at) > at + 1 && !value.contains(' ')
    }

    fun looksLikePhone(value: String): Boolean =
        value.count { it.isDigit() } in 9..13

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(strings.registerTitle, style = MaterialTheme.typography.headlineSmall)
        Text(strings.registerSubtitle, style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text(strings.registerEmail) },
            singleLine = true,
            enabled = !submitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it; error = null },
            label = { Text(strings.registerPhone) },
            singleLine = true,
            enabled = !submitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {
            Text(
                error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = {
                when {
                    !looksLikeEmail(email.trim()) -> error = strings.registerInvalidEmail
                    !looksLikePhone(phone) -> error = strings.registerInvalidPhone
                    else -> {
                        submitting = true
                        onSubmit(email.trim(), phone.trim()) { message ->
                            // Re-enable on failure, otherwise a dropped connection
                            // would leave the user staring at a dead button.
                            submitting = false
                            error = message
                        }
                    }
                }
            },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (submitting) strings.registerSubmitting else strings.registerSubmit)
        }

        Text(
            strings.registerPrivacy,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
