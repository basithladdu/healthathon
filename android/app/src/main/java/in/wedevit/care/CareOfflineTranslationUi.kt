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
@Composable fun CareOfflineTranslationPanel(source: String, versionId: String) {
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
    LaunchedEffect(Unit) { try { available = service.installed() } catch (failure: Exception) { if (failure is CancellationException) throw failure; error = "Could not check the downloaded language pack." } }
    DisposableEffect(versionId, source) { onDispose { job?.cancel() } }
    SurfaceCard(Mint) {
        Text("हिन्दी / English", fontWeight = FontWeight.Bold)
        Text("Read alongside the original", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("hi" to "हिन्दी", "en" to "English").forEach { (code, label) ->
                FilterChip(selected = target == code, onClick = { target = code; result = emptyList(); checked = false }, enabled = !busy, label = { Text(label) })
            }
        }
        if (available != true) {
            Text("Download the language pack once (about 30 MB). Reading then works offline.", style = MaterialTheme.typography.bodySmall)
            CheckLine(wifiOnly, { wifiOnly = it }, "Download over Wi-Fi only")
            OutlinedButton(onClick = {
                downloadBusy = true; error = ""
                job = scope.launch { try { service.download(wifiOnly); available = true } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    error = "Language pack download failed. Check the connection and try again."
                } finally { downloadBusy = false } }
            }, enabled = !downloadBusy, modifier = Modifier.testTag("offline-translation-download")) { Text(if (downloadBusy) "Downloading language pack…" else "Download Hindi–English pack") }
            if (downloadBusy) TextButton(onClick = { job?.cancel(); downloadBusy = false }) { Text("Stop waiting") }
        } else {
            Button(onClick = {
                busy = true; error = ""; checked = false; result = emptyList(); progress = 0 to 0
                job = scope.launch { try { result = service.translate(source, target) { done, total -> progress = done to total } }
                catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message ?: "Could not translate. The original is unchanged." }
                finally { busy = false } }
            }, enabled = !busy && source.isNotBlank(), modifier = Modifier.testTag("offline-translation-start")) { Text(if (busy) "Reading line ${progress.first} / ${progress.second}" else if (target == "hi") "Translate with Google · हिन्दी" else "Translate with Google · English") }
            if (busy) TextButton(onClick = { job?.cancel(); busy = false }) { Text("Cancel") }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (result.isNotEmpty()) {
            Text("Original and machine translation · मूल और अनुवाद", fontWeight = FontWeight.Bold)
            result.forEach { line ->
                Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SelectionContainerText(line.original)
                    SelectionContainerText(line.translated)
                }
            }
            Text("Check names, medicines, numbers and words such as ‘not’. Mixed or Roman Hindi may need correction by a bilingual person. This reading aid does not change the care note.", style = MaterialTheme.typography.bodySmall)
            CheckLine(checked, { checked = it }, "A Hindi/English reader checked this translation · अनुवाद जाँच लिया")
            if (checked) {
                val translated = result.joinToString("\n") { it.translated }
                CareReadAloudControl(translated, "$versionId-offline-$target", if (target == "hi") "hi-IN" else "en-IN")
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString("Machine translation · human checked\n${result.joinToString("\n\n") { "${it.original}\n${it.translated}" }}")) }) { Text("Copy both languages") }
            }
            Image(painterResource(R.drawable.google_translate_attribution), contentDescription = "Powered by Google Translate", modifier = Modifier.width(150.dp))
            TextButton(onClick = { notices = true }) { Text("Translation notices") }
        }
    }
    if (notices) AlertDialog(onDismissRequest = { notices = false }, title = { Text("Translation notices") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Google Translate powers this optional reading aid through ML Kit on-device models. Text stays on the device; language-pack downloads need a connection. Check translations with a bilingual person before relying on them.")
            Text("THIS SERVICE MAY CONTAIN TRANSLATIONS POWERED BY GOOGLE. GOOGLE DISCLAIMS ALL WARRANTIES RELATED TO THE TRANSLATIONS, EXPRESS OR IMPLIED, INCLUDING ANY WARRANTIES OF ACCURACY, RELIABILITY, AND ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://cloud.google.com/translate"))) }) { Text("Google Translate information") }
        }
    }, confirmButton = { TextButton(onClick = { notices = false }) { Text("Close") } })
}
