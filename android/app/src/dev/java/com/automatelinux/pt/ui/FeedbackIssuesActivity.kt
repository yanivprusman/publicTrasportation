package com.automatelinux.pt.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.automatelinux.feedbacklib.ui.issues.FeedbackIssuesScreen
import com.automatelinux.pt.BuildConfig
import com.automatelinux.pt.ui.theme.PTTheme
import com.automatelinux.pt.util.ServerConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@AndroidEntryPoint
class FeedbackIssuesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PTTheme {
                var hasUpdate by remember { mutableStateOf(false) }
                var needsBuild by remember { mutableStateOf(false) }
                var newVersion by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val result = checkVersions()
                    hasUpdate = result.hasUpdate
                    needsBuild = result.needsBuild
                    newVersion = result.newVersion
                }

                FeedbackIssuesScreen(
                    onNavigateBack = { finish() },
                    onResumeClarifier = { sessionId ->
                        startActivity(
                            Intent(this@FeedbackIssuesActivity, FeedbackChatActivity::class.java)
                                .putExtra(FeedbackChatActivity.EXTRA_CLARIFIER_SESSION_ID, sessionId),
                        )
                    },
                    versionName = BuildConfig.VERSION_NAME,
                    hasUpdate = hasUpdate,
                    needsBuild = needsBuild,
                    newVersion = newVersion,
                    onBuildComplete = {
                        needsBuild = false
                        hasUpdate = true
                    },
                )
            }
        }
    }

    private data class VersionCheck(val hasUpdate: Boolean, val needsBuild: Boolean, val newVersion: String?)

    private suspend fun checkVersions(): VersionCheck = withContext(Dispatchers.IO) {
        try {
            val conn = URL("${ServerConfig.activeServer}/api/health").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()
            val gitCommit = json.optString("gitCommit", "")
            val apkCommit = json.optString("apkCommit", "")
            val gitVersion = if (json.has("gitVersion")) json.optInt("gitVersion", 0) else 0
            val apkVersion = if (json.has("apkVersion")) json.optInt("apkVersion", 0) else 0
            val installedCommit = Regex("\\(([^)]+)\\)").find(BuildConfig.VERSION_NAME)?.groupValues?.get(1) ?: ""
            val hasUpdate = apkCommit.isNotBlank() && installedCommit.isNotBlank() && apkCommit != installedCommit
            val needsBuild = gitCommit.isNotBlank() && apkCommit.isNotBlank() && gitCommit != apkCommit
            val newVersion = when {
                hasUpdate && apkVersion > 0 -> apkVersion.toString()
                needsBuild && gitVersion > 0 -> gitVersion.toString()
                else -> null
            }
            VersionCheck(hasUpdate, needsBuild, newVersion)
        } catch (_: Exception) {
            VersionCheck(false, false, null)
        }
    }
}
