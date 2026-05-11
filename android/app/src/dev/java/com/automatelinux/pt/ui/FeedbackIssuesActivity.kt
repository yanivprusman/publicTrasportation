package com.automatelinux.pt.ui

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

                LaunchedEffect(Unit) {
                    val result = checkVersions()
                    hasUpdate = result.first
                    needsBuild = result.second
                }

                FeedbackIssuesScreen(
                    onNavigateBack = { finish() },
                    versionName = BuildConfig.VERSION_NAME,
                    hasUpdate = hasUpdate,
                    needsBuild = needsBuild,
                )
            }
        }
    }

    private data class VersionCheck(val hasUpdate: Boolean, val needsBuild: Boolean)

    private suspend fun checkVersions(): Pair<Boolean, Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = URL("${ServerConfig.activeServer}/api/health").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()
            val gitCommit = json.optString("gitCommit", "")
            val apkCommit = json.optString("apkCommit", "")
            val installedCommit = Regex("\\(([^)]+)\\)").find(BuildConfig.VERSION_NAME)?.groupValues?.get(1) ?: ""
            val hasUpdate = apkCommit.isNotBlank() && installedCommit.isNotBlank() && apkCommit != installedCommit
            val needsBuild = gitCommit.isNotBlank() && apkCommit.isNotBlank() && gitCommit != apkCommit
            Pair(hasUpdate, needsBuild)
        } catch (_: Exception) {
            Pair(false, false)
        }
    }
}
