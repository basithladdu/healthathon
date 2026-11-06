package `in`.wedevit.care

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * Small feedback pieces shared by every lane (Rajasthan patterns, 6 Nov): read aloud, the inline result line under a
 * pressed button, confirm and auto-closing success dialogs, the offline chip and a step tracker. Also the routing that
 * turns CareModel messages into a dialog (errors, things to act on) or a quiet toast ("Saved").
 */

// ---------------------------------------------------------------- messages: dialog or toast

/**
 * How a message is shown. CareModel and the screens post plain strings to vm.message; a lane can force the route
 * by wrapping the text: [SaharaNotice.dialog] for something the person must see, [SaharaNotice.quiet] for a toast.
 * Unmarked text: saved confirmations stay a toast; problems and everything else become a small dialog.
 */
object SaharaNotice {
    private const val DIALOG = "⁠"
    private const val QUIET = "‌"
    fun dialog(text: String) = DIALOG + text
    fun quiet(text: String) = QUIET + text
    enum class Route { Dialog, Toast }
    fun route(message: String): Route = when {
        message.startsWith(DIALOG) -> Route.Dialog
        message.startsWith(QUIET) -> Route.Toast
        SaharaMotion.isSavedMessage(message) -> Route.Toast
        else -> Route.Dialog
    }
    /** For the toast: drops only the routing mark, so CareToastHost still sees the saved mark and shows its tick. */
    fun quietText(message: String): String = message.removePrefix(DIALOG).removePrefix(QUIET)
    /** The words to show, without any routing or saved mark. */
    fun text(message: String): String = message.removePrefix(DIALOG).removePrefix(QUIET).removePrefix(SaharaMotion.SAVED_MARK).trim()
    fun tone(message: String): SaharaTone = when (CareToastRules.kind(text(message))) {
        CareToastKind.SAVED -> SaharaTone.Success; CareToastKind.PROBLEM -> SaharaTone.Error; CareToastKind.INFO -> SaharaTone.Info
    }
}

/** A message routed to a dialog: icon by tone, the message as the title, one OK. */
@Composable fun SaharaNoticeDialog(message: String, onDismiss: () -> Unit) {
    val t = careText(); val tone = SaharaNotice.tone(message)
    SaharaDialog(SaharaNotice.text(message), onDismiss = onDismiss, tone = tone, confirmLabel = t("OK", "ठीक है"), modifier = Modifier.testTag("sahara-notice-dialog"))
}

// ---------------------------------------------------------------- read aloud

/**
 * Speaker button: reads [text] aloud with the phone's voice (Hindi voice when the app is in Hindi), tap again to stop.
 * Uses CareReadAloud, so only installed voices are used and nothing goes online unless a translation pack is needed.
 * [autoSpeak] reads once when the screen opens, never while TalkBack is on.
 */
@Composable fun SpeakButton(text: String, modifier: Modifier = Modifier, big: Boolean = false, autoSpeak: Boolean = false, tone: SaharaTone = SaharaTone.Rust) {
    val context = LocalContext.current; val t = careText(); val hindi = careHindi()
    var tick by remember { mutableIntStateOf(0) }
    val reader = remember(context) { CareReadAloud(context) { tick++ } }
    DisposableEffect(reader) { onDispose { reader.close() } }
    val on = tick >= 0 && reader.busy // reading tick makes the button follow the reader's state
    val tag = if (hindi) "hi-IN" else "en-IN"
    if (autoSpeak) LaunchedEffect(text) {
        val talkBack = (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true
        if (!talkBack) { delay(600); reader.read(text, tag) }
    }
    FilledIconButton(onClick = { if (reader.busy) reader.stop() else reader.read(text, tag) }, modifier = modifier.size(if (big) 56.dp else 48.dp).testTag("speak-button")
        .semantics { contentDescription = if (on) t("Stop reading", "पढ़ना रोकें") else t("Read aloud", "पढ़कर सुनाएँ"); stateDescription = if (on) t("Reading", "पढ़ रहे हैं") else "" },
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (on) tone.solid else tone.wash, contentColor = if (on) tone.onSolid else tone.ink)) {
        Icon(if (on) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(if (big) 28.dp else 24.dp))
    }
}

// ---------------------------------------------------------------- inline result line

/** Holds the one-line result shown under the button that caused it; it clears itself after a few seconds. */
class SaharaSaid internal constructor() {
    var text by mutableStateOf<String?>(null); internal set
    var tone by mutableStateOf(SaharaTone.Success); internal set
    internal var serial by mutableIntStateOf(0)
    fun say(message: String, tone: SaharaTone = SaharaTone.Success) { text = message; this.tone = tone; serial++ }
    fun clear() { text = null }
}

@Composable fun rememberSaharaSaid(): SaharaSaid = remember { SaharaSaid() }

/** Put directly under the button: "Saved", "Sent to Dr. Rao", or a one-line problem. Read out politely by TalkBack. */
@Composable fun SaharaInlineResult(said: SaharaSaid, modifier: Modifier = Modifier, clearAfterMs: Long = 4000) {
    LaunchedEffect(said.serial) { if (said.text != null && said.tone != SaharaTone.Error) { delay(clearAfterMs); said.clear() } }
    AnimatedVisibility(said.text != null, modifier, enter = SaharaMotion.enter, exit = SaharaMotion.exit) {
        val tone = said.tone
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).announce().testTag("sahara-inline-result"), verticalAlignment = Alignment.CenterVertically) {
            Icon(when (tone) { SaharaTone.Error -> Icons.Default.ErrorOutline; SaharaTone.Warning -> Icons.Default.WarningAmber; SaharaTone.Success -> Icons.Default.CheckCircle; else -> Icons.Default.Info },
                null, Modifier.size(18.dp), tint = tone.ink)
            Spacer(Modifier.width(6.dp))
            Text(said.text.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = tone.ink)
        }
    }
}

// ---------------------------------------------------------------- confirm and success dialogs

/**
 * Confirm dialog for an action that cannot be undone. [danger] makes the confirm button red. Labels are verbs
 * ("Remove", "Keep"), never a bare OK / Cancel.
 */
@Composable fun SaharaConfirmDialog(title: String, confirmLabel: String, dismissLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    text: String? = null, danger: Boolean = false, icon: ImageVector? = null, busy: Boolean = false) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, modifier = Modifier.testTag("sahara-confirm-dialog"), containerColor = SaharaColors.Surface,
        shape = RoundedCornerShape(SaharaRadius.Hero),
        icon = { SaharaIconChip(icon ?: if (danger) Icons.Default.DeleteOutline else Icons.Default.HelpOutline, if (danger) SaharaTone.Error else SaharaTone.Rust, size = 48) },
        title = { Text(title, Modifier.a11yHeading(), style = MaterialTheme.typography.titleLarge, color = SaharaColors.Ink, textAlign = TextAlign.Center) },
        text = text?.let { { Text(it, style = MaterialTheme.typography.bodyLarge, color = SaharaColors.InkSub, textAlign = TextAlign.Center) } },
        confirmButton = { SaharaPrimaryButton(confirmLabel, onConfirm, tone = if (danger) SaharaTone.Error else SaharaTone.Rust, busy = busy, modifier = Modifier.testTag("sahara-confirm-yes")) },
        dismissButton = { SaharaSecondaryButton(dismissLabel, onDismiss, enabled = !busy, tone = SaharaTone.Neutral, modifier = Modifier.testTag("sahara-confirm-no")) })
}

/**
 * Success dialog that closes itself after [millis] (about 2.5 s), with a bar that shrinks to show the time left.
 * One tap anywhere also closes it. With motion off the bar stays still; it still closes on time.
 */
@Composable fun SaharaSuccessDialog(title: String, onClose: () -> Unit, text: String? = null, millis: Int = 2500) {
    val t = careText()
    val left = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (SaharaMotion.enabled) left.animateTo(0f, androidx.compose.animation.core.tween(millis, easing = LinearEasing)) else delay(millis.toLong())
        onClose()
    }
    AlertDialog(onDismissRequest = onClose, modifier = Modifier.testTag("sahara-success-dialog"), containerColor = SaharaColors.Surface, shape = RoundedCornerShape(SaharaRadius.Hero),
        icon = { SaharaSuccessTick(size = 56, description = t("Done", "हो गया")) },
        title = { Text(title, Modifier.announce(), style = MaterialTheme.typography.titleLarge, color = SaharaColors.Ink, textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (text != null) Text(text, style = MaterialTheme.typography.bodyLarge, color = SaharaColors.InkSub, textAlign = TextAlign.Center)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(SaharaTone.Success.wash)) {
                    Box(Modifier.fillMaxWidth(left.value).fillMaxHeight().clip(CircleShape).background(SaharaTone.Success.solid))
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Close", "बंद करें"), color = SaharaColors.Success, fontWeight = FontWeight.SemiBold) } })
}

// ---------------------------------------------------------------- offline chip

/** "No internet · 3 changes kept on this phone": shown while offline or while changes wait to be sent. */
@Composable fun SaharaOfflineChip(offline: Boolean, kept: Int, modifier: Modifier = Modifier) {
    val t = careText()
    if (!offline && kept <= 0) return
    val words = buildString {
        if (offline) append(t("No internet", "इंटरनेट नहीं"))
        if (kept > 0) {
            if (isNotEmpty()) append(" · ")
            append(if (kept == 1) t("1 change kept on this phone", "1 बदलाव इस फ़ोन पर रखा है") else t("$kept changes kept on this phone", "$kept बदलाव इस फ़ोन पर रखे हैं"))
        }
    }
    SaharaStatusPill(words, if (offline) SaharaTone.Warning else SaharaTone.Info, modifier.testTag("sahara-offline-chip").announce(), icon = if (offline) Icons.Default.CloudOff else Icons.Default.CloudUpload)
}

// ---------------------------------------------------------------- status tracker

/**
 * Steps of one item (a request, a form, an order): done steps show a tick, the current one is filled rust,
 * the next ones are outlined. Vertical, so long Hindi labels wrap at spaces.
 */
@Composable fun SaharaStatusTracker(steps: List<String>, current: Int, modifier: Modifier = Modifier) {
    val t = careText()
    Column(modifier.fillMaxWidth().testTag("sahara-status-tracker")) {
        steps.forEachIndexed { index, label ->
            val done = index < current; val now = index == current
            val state = when { done -> t("done", "पूरा"); now -> t("now", "अभी"); else -> t("next", "आगे") }
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label, $state" }, verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(when { done -> SaharaColors.Success; now -> SaharaColors.Primary; else -> SaharaColors.Surface })
                        .border(2.dp, when { done -> SaharaColors.Success; now -> SaharaColors.Primary; else -> SaharaColors.Edge }, CircleShape), contentAlignment = Alignment.Center) {
                        if (done) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = SaharaColors.OnPrimary)
                        else Text("${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (now) SaharaColors.OnPrimary else SaharaColors.InkSub)
                    }
                    if (index < steps.lastIndex) Box(Modifier.width(2.dp).height(20.dp).background(if (done) SaharaColors.Success else SaharaColors.Edge))
                }
                Spacer(Modifier.width(12.dp))
                Text(label, Modifier.padding(top = 3.dp).weight(1f), style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (now) FontWeight.SemiBold else FontWeight.Normal, color = if (done || now) SaharaColors.Ink else SaharaColors.InkSub)
            }
        }
    }
}
