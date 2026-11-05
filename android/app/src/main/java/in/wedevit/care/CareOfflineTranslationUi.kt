package `in`.wedevit.care

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A separate reading aid: never modifies or publishes the reviewed care-note version. */
@Composable fun CareOfflineTranslationPanel(source: String, versionId: String) { val t = careText();
    val service = remember { CareOfflineTranslation() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var available by remember { mutableStateOf<Boolean?>(null) }
    var wifiOnly by rememberSaveable { mutableStateOf(true) }
    var target by rememberSaveable(versionId) { mutableStateOf("hi") }
    var result by remember(versionId, source) { mutableStateOf<List<CareTranslationLine>>(emptyList()) }
    var checked by remember(versionId, source) { mutableStateOf(false) }
    var busy by remember(versionId, source) { mutableStateOf(false) }
    var downloadBusy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var error by remember(versionId, source) { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var notices by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { try { available = service.installed() } catch (failure: Exception) { if (failure is CancellationException) throw failure; error = CareOfflineTranslationCopy.unavailable } }
    DisposableEffect(versionId, source) { onDispose { job?.cancel() } }
    SurfaceCard(Mint) {
        Text("हिन्दी / English", Modifier.a11yHeading(), fontWeight = FontWeight.SemiBold, fontFamily = SaharaTitleFont, fontSize = 18.sp, color = DoctorCardInk)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("hi" to "हिन्दी", "en" to "English").forEach { (code, label) ->
                FilterChip(selected = target == code, onClick = { target = code; result = emptyList(); checked = false }, enabled = !busy, label = { Text(label) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        if (available != true) {
            CheckLine(wifiOnly, { wifiOnly = it }, t("Download over Wi-Fi only", "सिर्फ़ Wi-Fi पर डाउनलोड करें"))
            OutlinedButton(onClick = {
                downloadBusy = true; error = ""
                job = scope.launch { try { service.download(wifiOnly); available = true } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    error = CareOfflineTranslationCopy.downloadFailed
                } finally { downloadBusy = false } }
            }, enabled = !downloadBusy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("offline-translation-download")) { Text(if (downloadBusy) t("Downloading…", "डाउनलोड हो रहा है…") else t("Download", "डाउनलोड करें")) }
            if (downloadBusy) TextButton(onClick = { job?.cancel(); downloadBusy = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें")) }
        } else {
            Button(onClick = {
                busy = true; error = ""; checked = false; result = emptyList(); progress = 0 to 0
                job = scope.launch { try { result = service.translate(source, target) { done, total -> progress = done to total } }
                catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message ?: t("Couldn't translate.", "अनुवाद नहीं हो सका।") }
                finally { busy = false } }
            }, enabled = !busy && source.isNotBlank(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("offline-translation-start")) { Text(if (busy) t("Reading line ${progress.first} / ${progress.second}", "पंक्ति ${progress.first} / ${progress.second} पढ़ रहे हैं") else t("Translate", "अनुवाद करें")) }
            if (busy) TextButton(onClick = { job?.cancel(); busy = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें")) }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.announce())
        if (result.isNotEmpty()) {
            result.forEachIndexed { index, line ->
                if (index > 0) HorizontalDivider(color = DoctorHairline)
                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SelectionContainerText(line.original)
                    SelectionContainerText(line.translated)
                }
            }
            CheckLine(checked, { checked = it }, careBilingual("Checked by a reader", "पाठक ने जाँचा"))
            if (checked) {
                val translated = result.joinToString("\n") { it.translated }
                CareReadAloudControl(translated, "$versionId-offline-$target", if (target == "hi") "hi-IN" else "en-IN")
                OutlinedButton(shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp), onClick = { clipboard.setText(AnnotatedString("Machine translation · human checked\n${result.joinToString("\n\n") { "${it.original}\n${it.translated}" }}")) }) { Text(t("Copy both languages", "दोनों भाषाएँ कॉपी करें")) }
            }
            Image(painterResource(R.drawable.google_translate_attribution), contentDescription = "Powered by Google Translate", modifier = Modifier.width(150.dp))
            TextButton(onClick = { notices = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Notices", "सूचनाएँ")) }
        }
    }
    if (notices) AlertDialog(onDismissRequest = { notices = false }, title = { Text(t("Notices", "सूचनाएँ")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("THIS SERVICE MAY CONTAIN TRANSLATIONS POWERED BY GOOGLE. GOOGLE DISCLAIMS ALL WARRANTIES RELATED TO THE TRANSLATIONS, EXPRESS OR IMPLIED, INCLUDING ANY WARRANTIES OF ACCURACY, RELIABILITY, AND ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://cloud.google.com/translate"))) }) { Text(t("Google Translate information", "Google Translate की जानकारी")) }
        }
    }, confirmButton = { TextButton(onClick = { notices = false }) { Text(t("Close", "बंद करें")) } })
}

/** Plain statements for when the on-device translator cannot run. English · Hindi. */
internal object CareOfflineTranslationCopy {
    val unavailable: String get() = careBilingual("Translation is unavailable on this phone right now. Please read the original note.", "इस फ़ोन पर अभी अनुवाद नहीं हो सकता। मूल नोट पढ़ें।")
    val downloadFailed: String get() = careBilingual("Couldn't download. Check the internet.", "डाउनलोड नहीं हो सका। इंटरनेट जाँचें।")
    val waitingForWifi: String get() = careBilingual("Waiting for Wi-Fi. Untick “Download over Wi-Fi only” to use mobile data.", "वाई-फ़ाई का इंतज़ार है। मोबाइल डेटा से डाउनलोड के लिए “Wi-Fi only” हटाएँ।")
}
