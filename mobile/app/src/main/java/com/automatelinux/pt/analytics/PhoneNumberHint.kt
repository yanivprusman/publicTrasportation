package com.automatelinux.pt.analytics

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity

/**
 * One-tap phone number picker.
 *
 * Shows the numbers already present on the device (SIM / Google account) so the
 * user taps instead of typing. A typed number can be mistyped, and a mistyped
 * number is a user we can never reach — which would quietly defeat the reason
 * the number is collected at all.
 *
 * Requires no runtime permission and no OAuth configuration. It is unavailable
 * on devices without Play Services or with no number on the SIM, in which case
 * the caller keeps the manual field — the picker is a shortcut, not the only
 * way in.
 */
private const val TAG = "PhoneHint"

@Composable
fun rememberPhoneNumberHint(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = context as? Activity

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val data: Intent? = result.data
        if (data == null) return@rememberLauncherForActivityResult
        try {
            val number = Identity.getSignInClient(context).getPhoneNumberFromIntent(data)
            if (!number.isNullOrBlank()) onPicked(number)
        } catch (e: Exception) {
            // User dismissed the sheet, or no number was resolvable.
            Log.d(TAG, "no phone number returned", e)
        }
    }

    return remember(activity) {
        {
            if (activity == null) {
                Log.d(TAG, "no activity context; manual entry only")
            } else {
                Identity.getSignInClient(activity)
                    .getPhoneNumberHintIntent(GetPhoneNumberHintIntentRequest.builder().build())
                    .addOnSuccessListener { pendingIntent ->
                        try {
                            launcher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                            )
                        } catch (e: IntentSender.SendIntentException) {
                            Log.d(TAG, "could not launch phone hint", e)
                        }
                    }
                    .addOnFailureListener { e ->
                        // No Play Services, or no number on the device.
                        Log.d(TAG, "phone hint unavailable", e)
                    }
            }
        }
    }
}
