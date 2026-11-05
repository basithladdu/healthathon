package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Record-step stage colours. Terracotta is the stage; deep terracotta is the icon on the cream button. */
internal val DoctorStageTerracotta = Color(0xFFC9785A)
internal val DoctorStageDeep = Color(0xFF7A3B24)
internal val DoctorStageCream = Color(0xFFFFF8EE)

fun doctorStageStatus(recording: Boolean, saving: Boolean, seconds: Long, hindi: Boolean = false): String { val clock = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    return if (recording) (careTr(hindi, "Recording $clock", "रिकॉर्ड हो रहा है $clock")) else if (saving) (careTr(hindi, "Saving…", "सहेज रहे हैं…")) else careTr(hindi, "Ready to record", "रिकॉर्ड के लिए तैयार") }

/** Record / Dictate / Type are saved mode keys; only the label is shown in Hindi. */
internal fun doctorCaptureModeLabel(mode: String, hindi: Boolean): String = careTr(hindi, mode, when (mode) { "Record" -> "रिकॉर्ड"; "Dictate" -> "बोलकर लिखें"; "Type" -> "टाइप करें"; else -> mode })

/** Horizontal waveform of white rounded bars with a dot at each end. Moves with the microphone [level] while [active]; low and still otherwise. */
@Composable fun DoctorWaveform(level: Float, active: Boolean, modifier: Modifier = Modifier) {
    val meter by animateFloatAsState(level.coerceIn(0f, 1f), tween(120), label = "wave level")
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "wave phase")
    Canvas(modifier.fillMaxWidth().height(96.dp).testTag("record-stage-waveform")) {
        val n = 33; val dot = 3.dp.toPx(); val left = dot * 5; val step = (size.width - left * 2) / (n - 1); val bw = (step * 0.5f).coerceAtMost(5.dp.toPx()); val cy = size.height / 2
        drawCircle(Color.White, dot, Offset(dot, cy)); drawCircle(Color.White, dot, Offset(size.width - dot, cy))
        for (i in 0 until n) {
            val env = sin(PI * i / (n - 1)).toFloat(); val wob = 0.5f + 0.5f * sin(phase + i * 0.55f)
            val h = if (active) 6.dp.toPx() + (size.height - 6.dp.toPx()) * env * (0.12f + 0.88f * meter) * (0.35f + 0.65f * wob) else 4.dp.toPx() + 12.dp.toPx() * env
            drawRoundRect(Color.White, Offset(left + i * step - bw / 2, cy - h / 2), Size(bw, h), CornerRadius(bw / 2))
        }
    }
}

/**
 * Walkthrough record step: terracotta stage + persistent draggable "Saved Audio" sheet peeking at the bottom (tap or drag up to expand, drag down / back to collapse).
 * The caller owns recording, consent and playback; [sheet] is the expanded page body.
 */
@Composable fun DoctorRecordStage(mode: String, onMode: (String) -> Unit, recording: Boolean, saving: Boolean, saveError: Boolean, onRetry: () -> Unit, level: Float, seconds: Long, recordEnabled: Boolean, onRecord: () -> Unit, onStop: () -> Unit,
    onBack: (() -> Unit)?, backEnabled: Boolean, demo: Boolean, localRecordingAllowed: Boolean, testRecord: Boolean, menuEnabled: Boolean, onStartNew: (() -> Unit)?, onBackToWho: () -> Unit, backToWhoEnabled: Boolean, audioCount: Int, checklist: @Composable () -> Unit = {}, sheet: @Composable () -> Unit) { val t = careText();
    var menu by remember { mutableStateOf(false) }
    val white = Color.White
    BoxWithConstraints(Modifier.fillMaxSize().background(DoctorStageTerracotta).testTag("record-stage")) {
        val peek = 80.dp; val density = LocalDensity.current; val rangePx = with(density) { (maxHeight - peek).toPx() }.coerceAtLeast(1f)
        // The Saved Audio sheet stays open or closed across rotation.
        var keptOpen by rememberSaveable { mutableStateOf(false) }
        val frac = remember { Animatable(if (keptOpen) 1f else 0f) }; val scope = rememberCoroutineScope()
        val expanded = frac.targetValue > 0.5f
        SideEffect { keptOpen = expanded }
        BackHandler(expanded) { scope.launch { frac.animateTo(0f) } }
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = peek), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) TextButton(onClick = onBack, enabled = backEnabled, colors = ButtonDefaults.textButtonColors(contentColor = white, disabledContentColor = white.copy(alpha = .5f)), contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-capture-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(t("Back", "वापस"), fontSize = 16.sp) }
                Spacer(Modifier.weight(1f))
                Box { IconButton(onClick = { menu = true }, enabled = menuEnabled, modifier = Modifier.size(48.dp).testTag("doctor-capture-mode")) { Icon(Icons.Default.MoreVert, t("More options", "और विकल्प"), tint = white.copy(alpha = if (menuEnabled) 1f else .5f)) }
                    DropdownMenu(menu, { menu = false }) {
                        listOf("Record", "Dictate", "Type").forEach { value -> val available = when (value) { "Record" -> localRecordingAllowed; "Dictate" -> !demo; else -> true }; DropdownMenuItem(text = { Text(doctorCaptureModeLabel(value, careHindi()) + if (!available) t(" · unavailable", " · उपलब्ध नहीं") else "") }, enabled = available, onClick = { onMode(value); menu = false }) }
                        HorizontalDivider()
                        if (onStartNew != null) DropdownMenuItem(text = { Text(t("Start new", "नया शुरू करें")) }, enabled = menuEnabled, onClick = { menu = false; onStartNew() }, modifier = Modifier.testTag("record-stage-start-new"))
                        DropdownMenuItem(text = { Text(t("Who's here", "कौन मौजूद है")) }, enabled = backToWhoEnabled, onClick = { menu = false; onBackToWho() }, modifier = Modifier.testTag("record-stage-who"))
                    } }
            }
            Column(Modifier.fillMaxWidth().padding(end = 12.dp)) {
                Text(t("Conversation", "बातचीत"), fontFamily = SaharaTitleFont, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Medium, color = white)
                if (recording || saving) Text(doctorStageStatus(recording, saving, seconds, careHindi()), fontSize = 16.sp, lineHeight = 22.sp, color = white, modifier = Modifier.testTag("record-stage-status"))
            }
            // The goals-of-care checklist sits between the title and the waveform and scrolls if it is long.
            Column(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp, end = 12.dp).verticalScroll(rememberScrollState())) { checklist() }
            DoctorWaveform(level, recording, Modifier.padding(end = 12.dp))
            Spacer(Modifier.height(28.dp))
            Surface(onClick = { if (recording) onStop() else onRecord() }, enabled = recordEnabled, shape = CircleShape, color = DoctorStageCream, contentColor = DoctorStageDeep, modifier = Modifier.size(88.dp).testTag("record-stage-button")) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, if (recording) t("Stop and save", "रोकें और सहेजें") else t("Record conversation", "बातचीत रिकॉर्ड करें"), Modifier.size(40.dp), tint = DoctorStageDeep.copy(alpha = if (recordEnabled) 1f else .4f)) } }
            if (saveError && !saving) Column(Modifier.padding(top = 12.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (saving) t("Saving…", "सहेज रहे हैं…") else t("Couldn't save. Try again.", "सहेजा नहीं गया। फिर कोशिश करें।"), fontSize = 15.sp, lineHeight = 21.sp, color = white, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (saveError) OutlinedButton(onClick = onRetry, enabled = !saving, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, white), colors = ButtonDefaults.outlinedButtonColors(contentColor = white), modifier = Modifier.heightIn(min = 48.dp).testTag("retry-saving-audio")) { Text(t("Retry", "फिर कोशिश करें")) }
            }
            Spacer(Modifier.weight(1f))
        }
        val heightDp = with(density) { (peek.toPx() + rangePx * frac.value).toDp() }
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(heightDp), color = Color.White, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), shadowElevation = 8.dp) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().height(peek)
                    .draggable(rememberDraggableState { d -> scope.launch { frac.snapTo((frac.value - d / rangePx).coerceIn(0f, 1f)) } }, Orientation.Vertical, onDragStopped = { v -> scope.launch { frac.animateTo(if (abs(v) > 900f) (if (v < 0) 1f else 0f) else if (frac.value > 0.5f) 1f else 0f) } })
                    .clickable(role = Role.Button, onClickLabel = if (expanded) t("Collapse saved audio", "सहेजा ऑडियो बंद करें") else t("Expand saved audio", "सहेजा ऑडियो खोलें")) { scope.launch { frac.animateTo(if (expanded) 0f else 1f) } }.testTag("saved-audio-peek"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.width(40.dp).height(5.dp).background(DoctorStageTerracotta, RoundedCornerShape(3.dp)))
                    Text(if (audioCount > 0) t("Saved Audio · $audioCount", "सहेजा ऑडियो · $audioCount") else t("Saved Audio", "सहेजा ऑडियो"), fontFamily = SaharaTitleFont, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.padding(top = 8.dp).testTag("saved_audio_title"))
                }
                if (frac.value > 0.02f) sheet()
            }
        }
    }
}
