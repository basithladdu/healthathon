package `in`.wedevit.care

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
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
        Text("हिन्दी / English", fontWeight = FontWeight.Bold)
        Text(t("Read alongside the original", "मूल के साथ पढ़ें"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("hi" to "हिन्दी", "en" to "English").forEach { (code, label) ->
                FilterChip(selected = target == code, onClick = { target = code; result = emptyList(); checked = false }, enabled = !busy, label = { Text(label) })
            }
        }
        if (available != true) {
            Text(t("Download the language pack once (about 30 MB). Reading then works offline.", "भाषा पैक एक बार डाउनलोड करें (लगभग 30 MB)। फिर बिना इंटरनेट पढ़ सकेंगे।"), style = MaterialTheme.typography.bodySmall)
            CheckLine(wifiOnly, { wifiOnly = it }, t("Download over Wi-Fi only", "सिर्फ़ Wi-Fi पर डाउनलोड करें"))
            OutlinedButton(onClick = {
                downloadBusy = true; error = ""
                job = scope.launch { try { service.download(wifiOnly); available = true } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    error = CareOfflineTranslationCopy.downloadFailed
                } finally { downloadBusy = false } }
            }, enabled = !downloadBusy, modifier = Modifier.testTag("offline-translation-download")) { Text(if (downloadBusy) t("Downloading language pack…", "भाषा पैक डाउनलोड हो रहा है…") else t("Download Hindi–English pack", "हिन्दी–अंग्रेज़ी पैक डाउनलोड करें")) }
            if (downloadBusy && wifiOnly) Text(CareOfflineTranslationCopy.waitingForWifi, style = MaterialTheme.typography.bodySmall)
            if (downloadBusy) TextButton(onClick = { job?.cancel(); downloadBusy = false }) { Text(t("Stop waiting", "इंतज़ार बंद करें")) }
        } else {
            Button(onClick = {
                busy = true; error = ""; checked = false; result = emptyList(); progress = 0 to 0
                job = scope.launch { try { result = service.translate(source, target) { done, total -> progress = done to total } }
                catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message ?: t("Could not translate. The original is unchanged.", "अनुवाद नहीं हो सका। मूल वैसा ही है।") }
                finally { busy = false } }
            }, enabled = !busy && source.isNotBlank(), modifier = Modifier.testTag("offline-translation-start")) { Text(if (busy) t("Reading line ${progress.first} / ${progress.second}", "पंक्ति ${progress.first} / ${progress.second} पढ़ रहे हैं") else if (target == "hi") "Translate with Google · हिन्दी" else "Translate with Google · English") }
            if (busy) TextButton(onClick = { job?.cancel(); busy = false }) { Text(t("Cancel", "रद्द करें")) }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce())
        if (result.isNotEmpty()) {
            Text("Original and machine translation · मूल और अनुवाद", fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
            result.forEach { line ->
                Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SelectionContainerText(line.original)
                    SelectionContainerText(line.translated)
                }
            }
            Text(t("Check names, medicines, numbers and words such as ‘not’. Mixed or Roman Hindi may need correction by a bilingual person. This reading aid does not change the care note.", "नाम, दवाएँ, अंक और ‘नहीं’ जैसे शब्द जाँचें। मिली-जुली या रोमन में लिखी हिन्दी को दोनों भाषाएँ जानने वाला व्यक्ति ठीक करे। यह पढ़ने की मदद केयर नोट नहीं बदलती।"), style = MaterialTheme.typography.bodySmall)
            CheckLine(checked, { checked = it }, "A Hindi/English reader checked this translation · अनुवाद जाँच लिया")
            if (checked) {
                val translated = result.joinToString("\n") { it.translated }
                CareReadAloudControl(translated, "$versionId-offline-$target", if (target == "hi") "hi-IN" else "en-IN")
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString("Machine translation · human checked\n${result.joinToString("\n\n") { "${it.original}\n${it.translated}" }}")) }) { Text(t("Copy both languages", "दोनों भाषाएँ कॉपी करें")) }
            }
            Image(painterResource(R.drawable.google_translate_attribution), contentDescription = "Powered by Google Translate", modifier = Modifier.width(150.dp))
            TextButton(onClick = { notices = true }) { Text(t("Translation notices", "अनुवाद की सूचनाएँ")) }
        }
    }
    if (notices) AlertDialog(onDismissRequest = { notices = false }, title = { Text(t("Translation notices", "अनुवाद की सूचनाएँ")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Google Translate powers this optional reading aid through ML Kit on-device models. Text stays on the device; language-pack downloads need a connection. Check translations with a bilingual person before relying on them.", "Google Translate यह वैकल्पिक पढ़ने की मदद ML Kit के फ़ोन वाले मॉडल से देता है। लिखाई फ़ोन पर ही रहती है; भाषा पैक डाउनलोड के लिए इंटरनेट चाहिए। भरोसा करने से पहले अनुवाद दोनों भाषाएँ जानने वाले व्यक्ति से जँचवाएँ।"))
            Text("THIS SERVICE MAY CONTAIN TRANSLATIONS POWERED BY GOOGLE. GOOGLE DISCLAIMS ALL WARRANTIES RELATED TO THE TRANSLATIONS, EXPRESS OR IMPLIED, INCLUDING ANY WARRANTIES OF ACCURACY, RELIABILITY, AND ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://cloud.google.com/translate"))) }) { Text(t("Google Translate information", "Google Translate की जानकारी")) }
        }
    }, confirmButton = { TextButton(onClick = { notices = false }) { Text(t("Close", "बंद करें")) } })
}

/** Plain statements for when the on-device translator cannot run. English · Hindi. */
internal object CareOfflineTranslationCopy {
    const val unavailable = "Translation is unavailable on this phone: the on-device translator (Google ML Kit) could not start. Read the original note. · इस फ़ोन पर अनुवाद उपलब्ध नहीं है: फ़ोन पर चलने वाला अनुवादक शुरू नहीं हो सका। मूल नोट पढ़ें।"
    const val downloadFailed = "Translation is unavailable until the Hindi–English pack downloads. Check the internet connection and try again; the original note is unchanged. · हिन्दी–English पैक डाउनलोड होने तक अनुवाद उपलब्ध नहीं है। इंटरनेट जाँचकर फिर कोशिश करें; मूल नोट नहीं बदला।"
    const val waitingForWifi = "Waiting for Wi-Fi. Untick “Download over Wi-Fi only” to use mobile data. · वाई-फ़ाई का इंतज़ार है। मोबाइल डेटा से डाउनलोड के लिए “Wi-Fi only” हटाएँ।"
}
