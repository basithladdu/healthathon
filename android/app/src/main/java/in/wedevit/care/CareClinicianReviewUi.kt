package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Doctor approval for the Sahara team. The caller shows it only to an account the owner gave the
 * server-side clinical_verifier role (see blockers.md; CareSharedWorkspace checks isClinicalVerifier). The server re-checks the role,
 * refuses self-approval and records who decided, what was checked and why.
 */
@Composable fun CareClinicianReviewPanel(cloud: CareCloud) { val t = careText()
    val scope = rememberCoroutineScope()
    val userId = cloud.session.value?.userId
    var requests by remember(userId) { mutableStateOf<List<CareClinicianRequest>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    suspend fun load() { requests = cloud.pendingClinicianRequests() }
    LaunchedEffect(userId) {
        try { load() } catch (cancel: CancellationException) { throw cancel } catch (e: Exception) { message = e.message ?: t("Couldn't load requests.", "अनुरोध नहीं खुल सके।") }
    }
    SurfaceCard(Gold) {
        Text(t("Approve doctors · Sahara team", "डॉक्टर मंज़ूर करें · सहारा टीम"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, modifier = Modifier.testTag("clinician-review-panel"))
        Text(t("Check each registration on the medical council's register before deciding. You cannot approve your own account.", "फ़ैसले से पहले हर रजिस्ट्रेशन मेडिकल काउंसिल के रजिस्टर में जाँचें। आप अपना खाता मंज़ूर नहीं कर सकते।"), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { if (!busy) { busy = true; message = ""; scope.launch { try { load() } catch (e: Exception) { if (e is CancellationException) throw e; message = e.message.orEmpty() } finally { busy = false } } } }, enabled = !busy) { Text(t("Refresh requests", "अनुरोध ताज़ा करें")) }
        if (requests.isEmpty()) Text(t("No doctor is waiting for approval.", "कोई डॉक्टर मंज़ूरी का इंतज़ार नहीं कर रहा।"))
        requests.forEach { request -> key(request.userId, request.revision) {
            var evidence by remember { mutableStateOf("") }
            var note by remember { mutableStateOf("") }
            fun decide(decision: String) {
                if (busy) return
                busy = true; message = ""
                scope.launch {
                    try {
                        cloud.reviewClinician(request, decision, evidence, note); load()
                        message = if (decision == "approved") t("${request.name} is approved. Their doctor invitation codes now work.", "${request.name} मंज़ूर हुए। अब उनके डॉक्टर निमंत्रण कोड काम करेंगे।")
                        else t("${request.name} was not approved. They see your reason and can correct their details.", "${request.name} मंज़ूर नहीं हुए। वे आपका कारण देखकर विवरण ठीक कर सकते हैं।")
                    } catch (e: Exception) { if (e is CancellationException) throw e; message = e.message ?: t("The decision was not saved.", "फ़ैसला सहेजा नहीं गया।") }
                    finally { busy = false }
                }
            }
            HorizontalDivider()
            Text(request.name, fontWeight = FontWeight.Bold)
            Text(t("Council: ", "काउंसिल: ") + request.council)
            Text(t("Registration number: ", "रजिस्ट्रेशन नंबर: ") + request.registration)
            CareField(evidence, { evidence = it.take(1200) }, t("Where you checked it (register name or link)", "कहाँ जाँचा (रजिस्टर का नाम या लिंक)"), singleLine = true)
            CareField(note, { note = it.take(2000) }, t("Reason, shown to the doctor", "कारण, डॉक्टर को दिखेगा"), minLines = 2)
            val valid = runCatching { ClinicianReviewRules.check("approved", evidence, note) }.isSuccess
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { decide("approved") }, enabled = valid && !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("clinician-approve-${request.userId}")) { Text(t("Approve", "मंज़ूर करें")) }
                OutlinedButton(onClick = { decide("rejected") }, enabled = valid && !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("clinician-reject-${request.userId}")) { Text(t("Don't approve", "मंज़ूर न करें")) }
            }
        } }
        if (message.isNotBlank()) Text(message, modifier = Modifier.announce())
    }
}
