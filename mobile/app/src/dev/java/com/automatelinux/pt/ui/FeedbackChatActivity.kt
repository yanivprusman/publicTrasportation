package com.automatelinux.pt.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.automatelinux.feedbacklib.FeedbackConfig
import com.automatelinux.feedbacklib.data.model.Issue
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
        const val EXTRA_ISSUE_NUMBER = "issue_number"
        const val EXTRA_ISSUE_TITLE = "issue_title"
        const val EXTRA_ISSUE_DESCRIPTION = "issue_description"
        const val EXTRA_ISSUE_STATUS = "issue_status"
        const val EXTRA_ISSUE_INSIGHTS = "issue_insights"
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
                        val issue = Issue(
                            issueNumber = intent.getIntExtra(EXTRA_ISSUE_NUMBER, -1),
                            title = intent.getStringExtra(EXTRA_ISSUE_TITLE) ?: "",
                            description = intent.getStringExtra(EXTRA_ISSUE_DESCRIPTION) ?: "",
                            status = intent.getStringExtra(EXTRA_ISSUE_STATUS) ?: "",
                            labels = emptyList(),
                            createdAt = "",
                            updatedAt = "",
                            insights = intent.getStringExtra(EXTRA_ISSUE_INSIGHTS),
                        )
                        viewModel.resumeClarifierSession(clarifierSessionId, issue)
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
