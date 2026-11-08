package com.automatelinux.pt.ui.ai

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.data.api.AiApiException
import com.automatelinux.pt.data.api.AiConversation
import com.automatelinux.pt.data.api.AiOption
import com.automatelinux.pt.data.api.PtAiApi
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.journey.JourneySession
import com.automatelinux.pt.journey.JourneyText
import com.automatelinux.pt.util.AppStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val POLL_MS = 1500L

/**
 * The AI navigator: say where you want to go, read your options, tap one (or say
 * "take B") and the app follows the trip — the same journey tracker the planner
 * starts, so the bus-is-early / board now / get off next alerts are the same ones.
 *
 * On purpose almost empty: no map, no cards, no chips — text, a field, a mic.
 * The conversation itself runs on the server (`/api/ai`), one Claude session per
 * conversation, limited to public transport by the automateLinux pt domain.
 */
@Composable
fun AiScreen(
    api: PtAiApi,
    strings: AppStrings,
    language: String,
    conversationId: String?,
    onConversationId: (String?) -> Unit,
    /** The rider's position now, or null when location is off. */
    getLocation: suspend () -> Pair<Double, Double>?,
    onStartTrip: (Itinerary) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var conversation by remember { mutableStateOf<AiConversation?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Only an answer that arrives while this screen waits for it may start a trip —
    // reopening a conversation must not restart the trip it once chose.
    var startsFrom by rememberSaveable { mutableStateOf(Int.MAX_VALUE) }
    var startedAt by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableLongStateOf(0L) }

    val journeyItinerary by JourneySession.itinerary.collectAsState()
    val journeyProgress by JourneySession.progress.collectAsState()
    val journeyLive by JourneySession.live.collectAsState()

    BackHandler(onBack = onClose)

    // Load / poll the conversation while an answer is on its way.
    LaunchedEffect(conversationId, refresh) {
        val id = conversationId ?: run { conversation = null; return@LaunchedEffect }
        while (true) {
            val c = try {
                api.conversation(id)
            } catch (e: AiApiException) {
                error = if (e.error == "not found") null else strings.aiError
                if (e.error == "not found") onConversationId(null)
                return@LaunchedEffect
            } catch (e: Exception) {
                error = strings.aiUnreachable
                return@LaunchedEffect
            }
            conversation = c
            c.messages.withIndex()
                .lastOrNull { (i, m) -> i >= startsFrom && m.role == "ai" && m.start != null }
                ?.let { (_, m) ->
                    if (m.at != startedAt) {
                        startedAt = m.at
                        m.options.firstOrNull { it.id == m.start }?.let { onStartTrip(it.itinerary) }
                    }
                }
            if (!c.busy) break
            delay(POLL_MS)
        }
    }

    fun send(body: String) {
        val msg = body.trim()
        if (msg.isEmpty() || sending) return
        sending = true
        error = null
        scope.launch {
            try {
                val loc = getLocation()
                val id = api.send(conversationId, msg, loc?.first, loc?.second)
                text = ""
                startsFrom = conversation?.takeIf { it.id == id }?.messages?.size ?: 0
                onConversationId(id)
                refresh++
            } catch (e: AiApiException) {
                error = if (e.error == "ended") strings.aiEnded else strings.aiError
            } catch (e: Exception) {
                error = strings.aiUnreachable
            } finally {
                sending = false
            }
        }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { send(it) }
        }
    }

    val busy = sending || conversation?.busy == true
    val ended = conversation?.ended == true
    val listState = rememberLazyListState()
    val count = conversation?.messages?.size ?: 0
    LaunchedEffect(count, busy) { if (count > 0) listState.animateScrollToItem(count) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("ai-screen")
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onConversationId(null); conversation = null; error = null; text = "" },
                enabled = conversationId != null,
                modifier = Modifier.testTag("ai-new")
            ) { Text(strings.aiNew) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.testTag("ai-close")) {
                Icon(Icons.Default.Close, contentDescription = strings.aiClose)
            }
        }

        // The trip being followed, in words.
        journeyItinerary?.let { trip ->
            val now = remember(journeyProgress) { System.currentTimeMillis() }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(strings.aiFollowing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(JourneyText.headline(journeyProgress, strings), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp)
                JourneyText.detail(journeyProgress, strings)?.let { Text(it, fontSize = 16.sp, color = muted()) }
                JourneyText.liveBanner(journeyLive, trip, now, strings)?.let { Text(it, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary) }
                TextButton(
                    onClick = { JourneySession.stop(context) },
                    modifier = Modifier.testTag("ai-stop-following")
                ) { Text(strings.aiStopFollowing) }
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = muted().copy(alpha = 0.2f))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val messages = conversation?.messages.orEmpty()
            if (messages.isEmpty() && !busy) {
                Text(
                    strings.aiEmptyHint,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    color = muted(),
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(messages) { m ->
                    if (m.role == "user") {
                        Text(m.text, fontSize = 16.sp, color = muted())
                    } else if (m.error == true) {
                        Text(strings.aiError, fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (m.text.isNotBlank()) Text(m.text, fontSize = 20.sp, lineHeight = 28.sp)
                            m.options.forEach { o -> OptionLine(o, chosen = m.start == o.id) { onStartTrip(o.itinerary) } }
                        }
                    }
                }
                if (busy) item { Thinking(strings.aiThinking) }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
        if (ended) {
            Text(strings.aiEnded, color = muted(), modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(strings.aiPlaceholder, fontSize = 20.sp) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(text) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f).testTag("ai-input")
                )
                if (text.isBlank()) {
                    IconButton(
                        onClick = {
                            speech.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "he") "he-IL" else "en-US")
                            )
                        },
                        enabled = !busy,
                        modifier = Modifier.testTag("ai-speak")
                    ) { Icon(Icons.Default.Mic, contentDescription = strings.aiSpeak) }
                } else {
                    IconButton(onClick = { send(text) }, enabled = !busy, modifier = Modifier.testTag("ai-send")) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.aiSend)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionLine(o: AiOption, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag("ai-option-${o.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            o.id,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            o.line,
            fontSize = 18.sp,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun Thinking(label: String) {
    val t = rememberInfiniteTransition(label = "thinking")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha")
    Text(label, fontSize = 18.sp, color = muted(), modifier = Modifier.alpha(a))
}

@Composable
private fun muted(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
