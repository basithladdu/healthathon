package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var loading by remember(userId) { mutableStateOf(true) }
    suspend fun load() { requests = cloud.pendingClinicianRequests() }
    LaunchedEffect(userId) {
        try { load() } catch (cancel: CancellationException) { throw cancel } catch (e: Exception) { message = e.message ?: t("Couldn't load requests.", "अनुरोध नहीं खुल सके।") } finally { loading = false }
    }
    val refresh = { if (!busy) { busy = true; message = ""; scope.launch { try { load() } catch (e: Exception) { if (e is CancellationException) throw e; message = e.message.orEmpty() } finally { busy = false } } } }
    SurfaceCard(SaharaSoftGold) {
        // Title row: icon, title, count badge, the rule behind "?", and refresh.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VerifiedUser, null, Modifier.size(20.dp), tint = RustText); Spacer(Modifier.width(8.dp))
            Text(t("Approve doctors", "डॉक्टर मंज़ूर करें"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, fontSize = 17.sp, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading().testTag("clinician-review-panel"))
            if (requests.isNotEmpty()) Surface(shape = RoundedCornerShape(50), color = Paper, contentColor = RustText) { Text("${requests.size}", Modifier.padding(horizontal = 10.dp, vertical = 2.dp), fontWeight = FontWeight.Bold) }
            IconButton(onClick = refresh, enabled = !busy, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Refresh, t("Refresh requests", "अनुरोध ताज़ा करें"), tint = DoctorCardInk) }
        }
        if (loading || busy && requests.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Rust)
        else if (requests.isEmpty()) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = DoctorStatusGood); Spacer(Modifier.width(8.dp))
            Text(t("No requests", "कोई अनुरोध नहीं"), color = DoctorCardInk)
        }
        requests.forEach { request -> key(request.userId, request.revision) {
            var evidence by remember { mutableStateOf("") }
            var note by remember { mutableStateOf("") }
            fun decide(decision: String) {
                if (busy) return
                busy = true; message = ""
                scope.launch {
                    try {
                        cloud.reviewClinician(request, decision, evidence, note); load()
                        message = if (decision == "approved") t("${request.name} approved", "${request.name} मंज़ूर हुए")
                        else t("${request.name} not approved", "${request.name} मंज़ूर नहीं हुए")
                    } catch (e: Exception) { if (e is CancellationException) throw e; message = e.message ?: t("The decision was not saved.", "फ़ैसला सहेजा नहीं गया।") }
                    finally { busy = false }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = DoctorHairline)
            Text(request.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = DoctorCardInk)
            Text(t("Council: ", "काउंसिल: ") + request.council, fontSize = 14.sp, color = DoctorCardSub)
            Text(t("Registration number: ", "रजिस्ट्रेशन नंबर: ") + request.registration, fontSize = 14.sp, color = DoctorCardSub)
            Spacer(Modifier.height(4.dp))
            CareField(evidence, { evidence = it.take(1200) }, t("Register name or link", "रजिस्टर का नाम या लिंक"), singleLine = true)
            CareField(note, { note = it.take(2000) }, t("Reason", "कारण"), minLines = 2)
            val valid = runCatching { ClinicianReviewRules.check("approved", evidence, note) }.isSuccess
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { decide("rejected") }, enabled = valid && !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("clinician-reject-${request.userId}")) { Text(t("Don't approve", "मंज़ूर न करें")) }
                Button(onClick = { decide("approved") }, enabled = valid && !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("clinician-approve-${request.userId}")) { Text(t("Approve", "मंज़ूर करें"), fontWeight = FontWeight.SemiBold) }
            }
        } }
        if (message.isNotBlank()) Text(message, color = DoctorCardInk, modifier = Modifier.padding(top = 8.dp).announce())
    }
}
