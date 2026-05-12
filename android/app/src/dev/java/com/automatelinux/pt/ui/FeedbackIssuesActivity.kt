package com.automatelinux.pt.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.automatelinux.feedbacklib.ui.issues.FeedbackIssuesScreen
import com.automatelinux.pt.BuildConfig
import com.automatelinux.pt.ui.theme.PTTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FeedbackIssuesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PTTheme {
                FeedbackIssuesScreen(
                    onNavigateBack = { finish() },
                    onResumeClarifier = { sessionId ->
                        startActivity(
                            Intent(this@FeedbackIssuesActivity, FeedbackChatActivity::class.java)
                                .putExtra(FeedbackChatActivity.EXTRA_CLARIFIER_SESSION_ID, sessionId),
                        )
                    },
                    versionName = BuildConfig.VERSION_NAME,
                )
            }
        }
    }
}
