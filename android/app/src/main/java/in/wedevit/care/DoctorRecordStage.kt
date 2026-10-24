@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/** Record-step stage colours. Terracotta is the stage; deep terracotta is the icon on the cream button. */
internal val DoctorStageTerracotta = Color(0xFFC9785A)
internal val DoctorStageDeep = Color(0xFF7A3B24)
internal val DoctorStageCream = Color(0xFFFFF8EE)

fun doctorStageStatus(recording: Boolean, saving: Boolean, seconds: Long): String = if (recording) "Recording ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else if (saving) "Saving…" else "Ready to record"

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

/** Visible capture modes and separate Record / Saved audio pages; consent and recording stay caller-owned. */
@Composable fun DoctorRecordStage(mode: String, onMode: (String) -> Unit, recording: Boolean, saving: Boolean, saveError: Boolean, onRetry: () -> Unit, level: Float, seconds: Long, recordEnabled: Boolean, onRecord: () -> Unit, onStop: () -> Unit,
    onBack: (() -> Unit)?, backEnabled: Boolean, demo: Boolean, localRecordingAllowed: Boolean, testRecord: Boolean, menuEnabled: Boolean, onStartNew: (() -> Unit)?, onBackToWho: () -> Unit, backToWhoEnabled: Boolean, audioCount: Int, sheet: @Composable () -> Unit) {
    var savedAudioPage by rememberSaveable { mutableStateOf(false) }
    val white = Color.White
    val navigationEnabled = menuEnabled && !recording && !saving && !saveError
    val audioPageEnabled = !recording && !saving && !saveError
    LaunchedEffect(recording, saving, saveError) { if (!audioPageEnabled) savedAudioPage = false }
    BackHandler(savedAudioPage) { savedAudioPage = false }
    Column(Modifier.fillMaxSize().background(DoctorStageTerracotta).testTag("record-stage")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) TextButton(onClick = onBack, enabled = backEnabled, colors = ButtonDefaults.textButtonColors(contentColor = white, disabledContentColor = white.copy(alpha = .5f)), contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-capture-back")) { Text("Back to conversations", fontSize = 14.sp) }
                TextButton(onClick = onBackToWho, enabled = navigationEnabled && backToWhoEnabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = white, disabledContentColor = white.copy(alpha = .5f)),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("record-stage-who")) { Text("Who is here", fontSize = 14.sp) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("doctor-capture-modes")) {
                listOf("Record", "Dictate", "Type").forEach { value ->
                    val available = when (value) { "Record" -> localRecordingAllowed; "Dictate" -> !demo; else -> true }
                    FilterChip(selected = value == mode, onClick = { if (navigationEnabled && available) onMode(value) }, enabled = navigationEnabled && available,
                        label = { Text(value) },
                        leadingIcon = { Icon(when (value) { "Record" -> Icons.Default.Mic; "Dictate" -> Icons.Default.KeyboardVoice; else -> Icons.Default.Keyboard }, null, Modifier.size(18.dp), tint = if (!navigationEnabled || !available) white.copy(alpha = .5f) else if (value == mode) DoctorStageDeep else white) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color.Transparent, labelColor = white,
                            selectedContainerColor = DoctorStageCream, selectedLabelColor = DoctorStageDeep, selectedLeadingIconColor = DoctorStageDeep,
                            disabledContainerColor = Color.Transparent, disabledLabelColor = white.copy(alpha = .5f), disabledLeadingIconColor = white.copy(alpha = .5f), disabledSelectedContainerColor = white.copy(alpha = .2f)),
                        border = FilterChipDefaults.filterChipBorder(enabled = navigationEnabled && available, selected = value == mode, borderColor = white.copy(alpha = .65f), disabledBorderColor = white.copy(alpha = .25f)),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("record-stage-mode-$value"))
                }
            }
            if (demo) Text(if (testRecord && localRecordingAllowed) "Dictation is unavailable in this demo." else "Recording and dictation are unavailable in this demo.", color = white, fontSize = 12.sp)
            if (onStartNew != null) OutlinedButton(onClick = onStartNew, enabled = navigationEnabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = white, disabledContentColor = white.copy(alpha = .5f)), border = BorderStroke(1.dp, white.copy(alpha = .45f)),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("record-stage-start-new")) { Text("Start new · archive draft", fontSize = 13.sp) }
            Text("Conversation", fontFamily = SaharaTitleFont, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, color = white)
            Text(doctorStageStatus(recording, saving, seconds), fontSize = 16.sp, lineHeight = 22.sp, color = white, modifier = Modifier.testTag("record-stage-status"))
            Text("Audio stays on this device and is not included when a written note is shared. It is not automatically transcribed.", fontSize = 12.sp, lineHeight = 16.sp, color = white, modifier = Modifier.testTag("record-stage-privacy"))
        }
        PrimaryTabRow(selectedTabIndex = if (savedAudioPage) 1 else 0, containerColor = DoctorStageTerracotta, contentColor = white, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Tab(selected = !savedAudioPage, onClick = { savedAudioPage = false }, selectedContentColor = white, unselectedContentColor = white.copy(alpha = .8f),
                text = { Text("Record") }, modifier = Modifier.heightIn(min = 48.dp).testTag("record-stage-page-record"))
            Tab(selected = savedAudioPage, onClick = { if (audioPageEnabled) savedAudioPage = true }, enabled = audioPageEnabled,
                selectedContentColor = white, unselectedContentColor = white.copy(alpha = if (audioPageEnabled) .8f else .45f),
                text = { Text(if (audioCount > 0) "Saved audio · $audioCount" else "Saved audio", modifier = Modifier.testTag("saved_audio_title")) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("record-stage-page-saved"))
        }
        if (savedAudioPage) Surface(Modifier.weight(1f).fillMaxWidth(), color = Color.White) {
            Box(Modifier.fillMaxSize().padding(top = 12.dp)) { sheet() }
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                DoctorWaveform(level, recording)
                Spacer(Modifier.height(20.dp))
                Surface(onClick = { if (recording) onStop() else onRecord() }, enabled = recordEnabled, shape = CircleShape, color = DoctorStageCream, contentColor = DoctorStageDeep,
                    modifier = Modifier.size(88.dp).testTag("record-stage-button")) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, if (recording) "Stop and save" else "Record conversation", Modifier.size(40.dp), tint = DoctorStageDeep.copy(alpha = if (recordEnabled) 1f else .4f)) }
                }
                Text(if (recording) "Stop and save" else "Record conversation", color = white, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                if (saving || saveError) Column(Modifier.padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (saving) "Saving local audio and its consent record…" else "The audio file was retained, but its recording details could not be saved. Retry before continuing.", fontSize = 13.sp, lineHeight = 18.sp, color = white, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (saveError) OutlinedButton(onClick = onRetry, enabled = !saving, colors = ButtonDefaults.outlinedButtonColors(contentColor = white), modifier = Modifier.heightIn(min = 48.dp).testTag("retry-saving-audio")) { Text("Retry saving audio") }
                }
            }
        }
    }
}
