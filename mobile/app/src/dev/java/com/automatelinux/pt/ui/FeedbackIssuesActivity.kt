package com.automatelinux.pt.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.automatelinux.feedbacklib.ui.issues.FeedbackIssuesScreen
import com.automatelinux.pt.BuildConfig
import com.automatelinux.pt.ui.theme.PTTheme
import com.automatelinux.pt.ui.theme.PtFontFamily
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FeedbackIssuesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PTTheme(fontFamily = PtFontFamily) {
                FeedbackIssuesScreen(
                    onNavigateBack = { finish() },
                    versionName = BuildConfig.VERSION_NAME,
                )
            }
        }
    }
}
