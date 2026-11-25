package `in`.wedevit.care

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Messages for Simple Mode's "Read to me": the doctor, the nurse (or the family passing on what the team said) writes a
 * short message on the patient's record, and the patient's phone reads the newest one aloud in the app's language. Saved
 * as an ordinary journal entry ([SimpleModeRules.MESSAGE_TITLE]), so it works offline and syncs like any other entry.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable internal fun SimpleMessageWriter(vm: CareModel, person: Person, defaultFrom: SimpleModeRules.MessageFrom, onDismiss: () -> Unit) {
    val t = careText(); val lang = careLanguage()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var from by rememberSaveable { mutableStateOf(defaultFrom) }
    var name by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf("") }
    val tick = rememberSaharaTick(); val haptic = rememberSaharaHaptic()
    // A microphone beside the message box: the phone's own speech typing, in the app's language.
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty().trim() else ""
        if (heard.isNotEmpty()) text = (if (text.isBlank()) heard else "${text.trimEnd()} $heard").take(SimpleModeRules.MESSAGE_MAX)
    }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Paper, modifier = Modifier.saharaDialogEnter().testTag("simple-message-writer"),
        title = { Text(t("Message to read aloud", "पढ़कर सुनाने वाला संदेश"), fontSize = 22.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(person.name + ": " + t("Read to me reads the newest message aloud.", "'मुझे पढ़कर सुनाएँ' सबसे नया संदेश पढ़कर सुनाता है।"), fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardSub)
                Text(t("From", "किसकी ओर से"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SimpleModeRules.MessageFrom.entries.forEach { option ->
                        FilterChip(selected = from == option, onClick = { if (from != option) tick(); from = option }, label = { Text(t(option.english, option.hindi), fontSize = 15.sp) },
                            shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp).saharaSelectBounce(from == option, .08f).testTag("simple-message-from-${option.key}"))
                    }
                }
                CareField(name, { name = it.take(60) }, t("Name (optional)", "नाम (ज़रूरी नहीं)"), singleLine = true, placeholder = t("e.g. Sister Mary", "जैसे सिस्टर मैरी"))
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f)) {
                        CareField(text, { text = it.take(SimpleModeRules.MESSAGE_MAX) }, t("Message", "संदेश"), minLines = 3,
                            placeholder = t("e.g. Take the night medicine after food. I will visit on Friday.", "जैसे रात की दवा खाने के बाद लें। मैं शुक्रवार को आऊँगी।"))
                    }
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == CareLang.EN) "en-IN" else lang.locale.toLanguageTag())
                        try { listen.launch(intent) } catch (_: android.content.ActivityNotFoundException) { problem = t("Speech typing is not available on this phone. Type the message.", "इस फ़ोन पर बोलकर लिखना उपलब्ध नहीं है। संदेश लिखें।") }
                    }, modifier = Modifier.padding(start = 4.dp, top = 8.dp).size(56.dp).testTag("simple-message-mic").semantics { contentDescription = t("Speak the message", "संदेश बोलें") }) {
                        Icon(Icons.Default.Mic, null, Modifier.size(28.dp), tint = SaharaTone.Rose.ink)
                    }
                }
                Text(t("Please avoid news about illness stage or outlook here; the doctor talks about that in person.", "कृपया यहाँ बीमारी की अवस्था या आगे की बातें न लिखें; डॉक्टर उनके बारे में आमने-सामने बात करते हैं।"), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
                if (problem.isNotBlank()) Text(problem, fontSize = 15.sp, color = SaharaTone.Error.ink, modifier = Modifier.announce())
            }
        },
        confirmButton = { TextButton(onClick = {
            val error = SimpleModeRules.messageError(text, lang)
            if (error != null) { problem = error; return@TextButton }
            if (!canEditCareContext(vm, person.id, vm.accountNamespace)) { problem = t("View only", "सिर्फ़ देखने के लिए"); return@TextButton }
            vm.saveItem("journal", SimpleModeRules.MESSAGE_TITLE, SimpleModeRules.messageDetail(from, name, text), done = { haptic(); onDismiss() })
        }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("simple-message-save")) { Text(t("Save message", "संदेश सहेजें"), fontSize = 16.sp, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें"), fontSize = 16.sp) } })
}

/**
 * A row for the family or the care team: the newest message that "Read to me" will read, and a button to write a new one.
 * [clinician] picks Doctor as the default sender instead of Family.
 */
@Composable fun SimpleReadAloudMessageRow(vm: CareModel, person: Person, clinician: Boolean = false) {
    val t = careText()
    val items by vm.items.collectAsStateWithLifecycle()
    val latest = remember(items, person.id) { SimpleModeRules.latestMessage(items, person.id) }
    var writing by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).testTag("more-simple-message"), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(10.dp), color = SaharaTone.Indigo.solid, contentColor = Color.White, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.RecordVoiceOver, null, Modifier.size(20.dp)) }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t("Message to read aloud", "पढ़कर सुनाने वाला संदेश"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk)
            val sub = latest?.let { m ->
                val day = Instant.ofEpochMilli(m.created).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
                "${t(m.from.english, m.from.hindi)} · $day · ${m.text.take(60)}"
            } ?: t("None yet. Read to me reads the newest message aloud.", "अभी कोई नहीं। 'मुझे पढ़कर सुनाएँ' सबसे नया संदेश पढ़कर सुनाता है।")
            Text(sub, fontSize = 14.sp, color = DoctorCardSub, maxLines = 2)
        }
        TextButton(onClick = { writing = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("more-simple-message-write")) {
            Text(if (latest == null) t("Write", "लिखें") else t("New", "नया"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SaharaTone.Indigo.ink)
        }
    }
    if (writing) SimpleMessageWriter(vm, person, if (clinician) SimpleModeRules.MessageFrom.DOCTOR else SimpleModeRules.MessageFrom.FAMILY) { writing = false }
}
