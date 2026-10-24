package `in`.wedevit.care

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class DoctorTranscriptKind { NONE, MANUAL, FICTIONAL_SAMPLE, ONLINE }
enum class DoctorCoverageStatus { NOT_ASSESSED, REVIEWED_DISCUSSED, NOT_FOUND_IN_TRANSCRIPT, NEEDS_REVIEW }
data class DoctorCoverageItem(val question: String, val status: DoctorCoverageStatus = DoctorCoverageStatus.NOT_ASSESSED, val quote: String = "", val manuallyReviewed: Boolean = false)
data class DoctorRecordingReviewState(
    val recording: Boolean = false, val paused: Boolean = false, val seconds: Int = 0,
    val levels: List<Float> = emptyList(), val clipLabel: String? = null,
    val playing: Boolean = false, val playbackPositionMs: Int = 0, val durationMs: Int = 0,
    val sourceText: String = "", val sourceKind: DoctorTranscriptKind = DoctorTranscriptKind.NONE,
    val coverage: List<DoctorCoverageItem> = defaultDoctorCoverage(),
    val canRecord: Boolean = true, val canTranscribe: Boolean = false, val busy: Boolean = false,
    val error: String? = null,
)
fun defaultDoctorCoverage() = listOf("What matters most to the patient?", "Who would the patient like involved?", "What questions or concerns remain?", "What is still undecided?", "What are the agreed next steps?").map { DoctorCoverageItem(it) }
fun doctorAudioClock(milliseconds: Int): String { val seconds = milliseconds.coerceAtLeast(0) / 1000; return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" }
fun isDoctorSourceQuote(source: String, quote: String): Boolean = quote.isNotBlank() && source.lineSequence().any { it == quote }
fun effectiveDoctorCoverage(item: DoctorCoverageItem, sourceText: String): DoctorCoverageStatus = when {
    item.status == DoctorCoverageStatus.NOT_FOUND_IN_TRANSCRIPT && sourceText.isBlank() -> DoctorCoverageStatus.NEEDS_REVIEW
    item.status == DoctorCoverageStatus.NOT_FOUND_IN_TRANSCRIPT && !item.manuallyReviewed -> DoctorCoverageStatus.NEEDS_REVIEW
    item.status != DoctorCoverageStatus.REVIEWED_DISCUSSED -> item.status
    item.quote.isNotBlank() && !isDoctorSourceQuote(sourceText, item.quote) -> DoctorCoverageStatus.NEEDS_REVIEW
    item.manuallyReviewed -> item.status
    isDoctorSourceQuote(sourceText, item.quote) -> item.status
    else -> DoctorCoverageStatus.NEEDS_REVIEW
}

/** Presentation only. No microphone, player, file, service, consent or medical decision lives here. */
@Composable fun DoctorRecordingReview(
    state: DoctorRecordingReviewState,
    onRecord: () -> Unit,
    onPauseResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onTranscribe: () -> Unit,
    onSourceEdit: (String) -> Unit,
    onCoverageReview: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("record-review"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(color = Rust, contentColor = Color.White, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth().animateContentSize(tween(260)).testTag("record-hero")) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, null, Modifier.size(25.dp)); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (state.recording) if (state.paused) "Recording paused" else "Recording audio" else if (state.clipLabel != null) "Conversation audio" else "Record audio", fontWeight = FontWeight.Medium, fontSize = 20.sp, fontFamily = SaharaTitleFont)
                        if (state.clipLabel != null && !state.recording) Text(state.clipLabel, fontSize = 12.sp, color = Color.White.copy(alpha = .84f))
                    }
                    if (!state.recording && state.clipLabel != null) Icon(Icons.Default.CheckCircle, "Saved locally", Modifier.size(21.dp))
                }
                if (state.recording || state.clipLabel == null) {
                    Text(if (state.recording) doctorAudioClock(state.seconds.coerceAtLeast(0) * 1000) else "Ready when everyone agrees", fontSize = if (state.recording) 38.sp else 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("record-timer"))
                    if (state.recording) {
                        DoctorMeasuredWaveform(if (state.paused) emptyList() else state.levels, Modifier.fillMaxWidth().height(58.dp))
                        Text(if (state.paused) "Audio capture is paused." else if (state.levels.isEmpty()) "Waiting for a measured microphone level." else "Live microphone levels", fontSize = 12.sp, color = Color.White.copy(alpha = .84f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onPauseResumeRecording, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("record-pause"), border = BorderStroke(1.dp, Color.White.copy(alpha = .65f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Icon(if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause, null); Spacer(Modifier.width(6.dp)); Text(if (state.paused) "Resume" else "Pause") }
                            Button(onClick = onStopRecording, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("record-stop"), colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Rust)) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("Stop & save") }
                        }
                    } else {
                        Text("Capture a conversation on this device. Recording starts only after consent and microphone permission.", fontSize = 14.sp, lineHeight = 21.sp, color = Color.White.copy(alpha = .9f))
                        Button(onClick = onRecord, enabled = state.canRecord && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("record-start"), colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Rust, disabledContainerColor = Paper.copy(alpha = .65f), disabledContentColor = Rust.copy(alpha = .75f))) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text("Record conversation") }
                    }
                } else {
                    Column(Modifier.testTag("record-player")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledIconButton(onClick = onPlayPause, enabled = !state.busy, modifier = Modifier.size(52.dp).testTag("record-play"), shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Paper, contentColor = Rust)) { Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.playing) "Pause recording playback" else "Play recording") }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${doctorAudioClock(state.playbackPositionMs)} / ${doctorAudioClock(state.durationMs)}", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text("Saved on this device", fontSize = 12.sp, color = Color.White.copy(alpha = .84f))
                            }
                            IconButton(onClick = onRecord, enabled = state.canRecord && !state.busy, modifier = Modifier.size(48.dp).testTag("record-start")) { Icon(Icons.Default.Mic, "Record another conversation", tint = Color.White) }
                        }
                        Slider(value = state.playbackPositionMs.coerceIn(0, state.durationMs.coerceAtLeast(1)).toFloat(), onValueChange = { onSeek(it.toInt()) }, valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(), enabled = state.durationMs > 0 && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("record-seek").semantics { contentDescription = "Recording playback position" }, colors = SliderDefaults.colors(thumbColor = Paper, activeTrackColor = Paper, inactiveTrackColor = Color.White.copy(alpha = .3f)))
                    }
                    if (state.canTranscribe) OutlinedButton(onClick = onTranscribe, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("record-transcribe"), border = BorderStroke(1.dp, Color.White.copy(alpha = .6f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (state.busy) "Preparing transcript…" else "Transcribe & draft Care Note") }
                }
            }
        }
        state.error?.let { Text(it, color = Rust, modifier = Modifier.testTag("record-review-error"), style = MaterialTheme.typography.bodyMedium) }
        AnimatedVisibility(visible = !state.recording, enter = expandVertically(tween(260)) + fadeIn(tween(180)), exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(color = DoctorCardReport, contentColor = DoctorCardInk, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Notes, null, tint = DoctorAccent); Spacer(Modifier.width(10.dp)); Text("Conversation transcript", fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, color = DoctorCardInk) }
                Text(if (state.clipLabel != null) "Written from the conversation audio above. Play it back while you read." else "Not linked to a recording yet.", fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
                Text(when (state.sourceKind) { DoctorTranscriptKind.FICTIONAL_SAMPLE -> "Prepared transcript · not taken from this recording"; DoctorTranscriptKind.ONLINE -> "Online transcript · check every word against the audio"; DoctorTranscriptKind.MANUAL -> "Manually entered · check wording and speakers"; DoctorTranscriptKind.NONE -> "No transcript yet" }, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = DoctorAccent, modifier = Modifier.testTag("record-transcript-origin"))
                OutlinedTextField(value = state.sourceText, onValueChange = { if (it.length <= 12000) onSourceEdit(it) }, enabled = !state.busy && !state.recording, label = { Text("Transcript · tap to edit") }, placeholder = { Text("Type or paste what was said, then correct any words that are wrong.") }, supportingText = { Text("${state.sourceText.length} / 12000", fontSize = 12.sp, color = DoctorCardFaint) }, textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp, color = DoctorCardInk), minLines = 6, maxLines = 14, modifier = Modifier.fillMaxWidth().testTag("record-transcript"), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DoctorAccent, focusedLabelColor = DoctorAccent, cursorColor = DoctorAccent, unfocusedBorderColor = DoctorCardSub, unfocusedLabelColor = DoctorCardSub, disabledTextColor = DoctorCardSub))
            }
        }
        Surface(color = DoctorCardReport, contentColor = DoctorCardInk, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Goals of Care review", fontSize = 20.sp, lineHeight = 26.sp, color = DoctorCardInk, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                Text("Check what was covered. Missing text does not mean a topic was not discussed or that anyone agreed.", fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
                state.coverage.forEachIndexed { index, item ->
                    val status = effectiveDoctorCoverage(item, state.sourceText)
                    val label = when (status) { DoctorCoverageStatus.NOT_ASSESSED -> "Not assessed"; DoctorCoverageStatus.REVIEWED_DISCUSSED -> if (item.manuallyReviewed) "Discussed · manually reviewed" else "Discussed · source linked"; DoctorCoverageStatus.NOT_FOUND_IN_TRANSCRIPT -> "Not found in transcript"; DoctorCoverageStatus.NEEDS_REVIEW -> "Needs review" }
                    Surface(onClick = { onCoverageReview(index) }, color = Cream, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("record-coverage-$index").semantics { stateDescription = label }) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.question, fontWeight = FontWeight.Medium, lineHeight = 23.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (status == DoctorCoverageStatus.REVIEWED_DISCUSSED) Icons.Default.CheckCircleOutline else Icons.Default.RadioButtonUnchecked, null, tint = Rust, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(label, color = Rust, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Rust, modifier = Modifier.size(18.dp)) }
                            if (isDoctorSourceQuote(state.sourceText, item.quote)) Text("Source: ${item.quote}", fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub)
                        }
                    }
                }
            }
        }
        DoctorFootnote("Audio is not automatically transcribed, so the words must be checked by a person. Audio stays on this device and is not included when a written note is shared.")
        }
        }
    }
}

@Composable private fun DoctorMeasuredWaveform(levels: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val measured = levels.takeLast(32).filter { it.isFinite() }
        if (measured.isEmpty()) drawLine(Color.White.copy(alpha = .55f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        else measured.forEachIndexed { index, level ->
            val x = size.width * (index + .5f) / measured.size
            val extent = (size.height * level.coerceIn(0f, 1f) / 2f).coerceAtLeast(1.dp.toPx())
            drawLine(Color.White.copy(alpha = .9f), Offset(x, size.height / 2f - extent), Offset(x, size.height / 2f + extent), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}
