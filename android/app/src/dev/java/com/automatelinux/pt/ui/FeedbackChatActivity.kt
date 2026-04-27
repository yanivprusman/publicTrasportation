package com.automatelinux.pt.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.automatelinux.feedbacklib.FeedbackConfig
import com.automatelinux.feedbacklib.ui.chat.FeedbackChatScreen
import com.automatelinux.feedbacklib.ui.chat.FeedbackChatViewModel
import com.automatelinux.pt.ui.theme.PTTheme
import com.automatelinux.pt.util.ServerConfig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FeedbackChatActivity : ComponentActivity() {

    @Inject lateinit var feedbackConfig: FeedbackConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PTTheme {
                val viewModel: FeedbackChatViewModel = hiltViewModel()

                LaunchedEffect(Unit) {
                    ServerConfig.findReachableServer()
                    viewModel.setServerFound(true)
                }

                FeedbackChatScreen(
                    viewModel = viewModel,
                    config = feedbackConfig,
                    onNavigateBack = { finish() },
                    onNavigateToIssues = {
                        startActivity(Intent(this@FeedbackChatActivity, FeedbackIssuesActivity::class.java))
                    },
                )
            }
        }
    }
}
