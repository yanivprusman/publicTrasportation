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

    companion object {
        const val EXTRA_CLARIFIER_SESSION_ID = "clarifier_session_id"
    }

    @Inject lateinit var feedbackConfig: FeedbackConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PTTheme {
                val viewModel: FeedbackChatViewModel = hiltViewModel()

                val clarifierSessionId = intent.getStringExtra(EXTRA_CLARIFIER_SESSION_ID)

                LaunchedEffect(clarifierSessionId) {
                    ServerConfig.findReachableServer()
                    viewModel.setServerFound(true)
                    if (clarifierSessionId != null) {
                        viewModel.resumeClarifierSession(clarifierSessionId)
                    }
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
