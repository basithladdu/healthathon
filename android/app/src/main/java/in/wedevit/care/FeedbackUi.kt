package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A single, continuously morphing app-feedback screen, isolated from care records. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FeedbackScreen(accountScope: String, surface: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val store = remember(accountScope, surface) { FeedbackStore(context, accountScope, surface) }
    val initial = remember(store) { store.latest() }
    var score by rememberSaveable(accountScope, surface) { mutableFloatStateOf(initial?.score ?: .5f) }
    var note by rememberSaveable(accountScope, surface) { mutableStateOf(initial?.note.orEmpty()) }
    var savedScore by rememberSaveable(accountScope, surface) { mutableFloatStateOf(initial?.score ?: .5f) }
    var savedNote by rememberSaveable(accountScope, surface) { mutableStateOf(initial?.note.orEmpty()) }
    var saved by rememberSaveable(accountScope, surface) { mutableStateOf(false) }
    var error by remember { mutableStateOf(store.error()) }
    var help by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var noteExpanded by rememberSaveable(accountScope, surface) { mutableStateOf(initial?.note?.isNotBlank() == true) }
    val dirty = score != savedScore || note != savedNote
    val close = { focus.clearFocus(); if (dirty) discard = true else onClose() }
    BackHandler { close() }
    val expression by animateFloatAsState(score, tween(120), label = "feedback expression")
    val wash by animateColorAsState(if (score <= .5f) lerp(Peach, Cream, score * 2f) else lerp(Cream, SaharaSoftBlue, (score - .5f) * 2f), tween(160), label = "feedback warmth")
    val label = FeedbackRules.label(score)

    Column(Modifier.fillMaxSize().background(wash).safeDrawingPadding().imePadding().testTag("feedback-screen").verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("feedback-close")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close feedback", tint = Ink) }
            Spacer(Modifier.weight(1f))
            Text("Feedback", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { help = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Outlined.Info, "About app feedback", tint = Ink) }
        }
        Spacer(Modifier.height(16.dp))
        Text("How was your\nSahara experience?", color = Ink, fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 37.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Slide to rate your experience.", color = Ink.copy(alpha = .78f), fontSize = 15.sp, lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        FeedbackFace(expression, Modifier.sizeIn(maxWidth = 210.dp, maxHeight = 210.dp).fillMaxWidth().aspectRatio(1f).clearAndSetSemantics { })
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.testTag("feedback-state"))
        Spacer(Modifier.height(12.dp))
        Slider(value = score, onValueChange = { score = it; saved = false; error = null }, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("feedback-slider").semantics { contentDescription = "Rate your Sahara app experience"; stateDescription = label }, thumb = { Box(Modifier.size(30.dp).background(Rust, CircleShape).border(3.dp, Paper, CircleShape)) }, colors = SliderDefaults.colors(thumbColor = Rust, activeTrackColor = Rust, inactiveTrackColor = Ink.copy(alpha = .14f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Not great" to 0f, "Okay" to .5f, "Good" to 1f).forEach { (name, value) ->
                TextButton(onClick = { score = value; saved = false; error = null }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(name, color = if (label == name) Rust else Ink.copy(alpha = .75f), fontWeight = if (label == name) FontWeight.Bold else FontWeight.Normal) }
            }
        }
        TextButton(onClick = { noteExpanded = !noteExpanded; focus.clearFocus() }, modifier = Modifier.heightIn(min = 48.dp).testTag("feedback-add-note")) { Text(if (noteExpanded) "Hide note" else if (note.isBlank()) "Add a note (optional)" else "Edit your note", color = Rust) }
        if (noteExpanded) OutlinedTextField(value = note, onValueChange = { if (it.length <= FeedbackRules.noteLimit) { note = it; saved = false; error = null } }, label = { Text("Anything to add? (optional)") }, placeholder = { Text("What felt easy, or what could be clearer?") }, supportingText = { Text("App feedback only. Don't include private health information. ${note.length}/500", fontSize = 12.sp) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth().testTag("feedback-note"), shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Rust, focusedLabelColor = Rust, cursorColor = Rust, unfocusedBorderColor = Ink.copy(alpha = .25f), focusedContainerColor = Paper.copy(alpha = .7f), unfocusedContainerColor = Paper.copy(alpha = .55f)))
        Spacer(Modifier.height(8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }); Spacer(Modifier.height(8.dp)) }
        Button(onClick = { focus.clearFocus(); error = store.save(score, note); if (error == null) { saved = true; savedScore = score; savedNote = note } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("feedback-save"), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White)) {
            if (saved) { Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(8.dp)) }
            Text(if (saved) "Saved on this device" else "Save feedback", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Text(if (saved) "Your feedback is saved locally. It hasn't been sent to the Sahara team." else "Saved on this device only. Nothing is sent to the Sahara team.", color = Ink.copy(alpha = .72f), fontSize = 12.sp, lineHeight = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, containerColor = Paper, title = { Text("About this feedback") }, text = { Text("This is about using the Sahara app, not your symptoms or wellbeing. Feedback stays on this device and is separate from patient records. Please leave out personal or health details.") }, confirmButton = { TextButton(onClick = { help = false }) { Text("Got it", color = Rust) } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, containerColor = Paper, title = { Text("Leave without saving?") }, text = { Text("Your latest changes won't be saved. Previously saved feedback stays on this device.") }, confirmButton = { TextButton(onClick = { discard = false; onClose() }) { Text("Leave", color = Rust) } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing", color = Rust) } })
}

@Composable fun FeedbackFace(score: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val value = score.coerceIn(0f, 1f)
        val eyeH = h * FeedbackRules.eyeHeight(value)
        val eyeW = w * FeedbackRules.eyeWidth(value)
        for (x in listOf(.28f, .72f)) drawRoundRect(Ink, Offset(w * x - eyeW / 2f, h * .35f - eyeH / 2f), Size(eyeW, eyeH), CornerRadius(eyeW / 2f, eyeH / 2f))
        val mouth = Path().apply { moveTo(w * .34f, h * .72f); cubicTo(w * .44f, h * (.72f + FeedbackRules.mouthCurve(value)), w * .56f, h * (.72f + FeedbackRules.mouthCurve(value)), w * .66f, h * .72f) }
        drawPath(mouth, Ink, style = Stroke(width = w * .032f, cap = StrokeCap.Round))
    }
}
