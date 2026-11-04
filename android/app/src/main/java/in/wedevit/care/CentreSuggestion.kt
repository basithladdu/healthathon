package `in`.wedevit.care

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** What a signed-in person types for a centre the directory is missing. Nothing here is about a patient. */
data class CentreSuggestionDraft(val name: String, val city: String, val state: String, val address: String = "", val phone: String = "", val note: String = "")

/**
 * One of this account's suggestions. [status] is "waiting" or "kept" while it is still on the phone (send-later queue),
 * otherwise the server's "pending", "approved" or "rejected". Only the author and Sahara staff reviewers can read it.
 */
data class CentreSuggestion(val id: String, val name: String, val city: String, val state: String, val status: String, val createdAt: Long)

object CentreSuggestionRules {
    const val NAME_MAX = 120; const val PLACE_MAX = 80; const val ADDRESS_MAX = 300; const val NOTE_MAX = 500

    /** First problem with [d] as an English and Hindi pair, or null when it can be sent. Same limits as the server table. */
    fun error(d: CentreSuggestionDraft): Pair<String, String>? {
        val name = d.name.trim(); val city = d.city.trim(); val state = d.state.trim(); val phone = d.phone.trim()
        return when {
            name.length < 3 || !name.any(Char::isLetter) -> "Add the centre's name." to "केंद्र का नाम लिखें।"
            name.length > NAME_MAX -> "The name is too long (up to $NAME_MAX letters)." to "नाम बहुत लंबा है ($NAME_MAX अक्षर तक)।"
            city.length < 2 || !city.any(Char::isLetter) -> "Add the town or city." to "शहर या क़स्बा लिखें।"
            city.length > PLACE_MAX -> "The town name is too long." to "शहर का नाम बहुत लंबा है।"
            state.length < 2 || !state.any(Char::isLetter) -> "Add the state." to "राज्य लिखें।"
            state.length > PLACE_MAX -> "The state name is too long." to "राज्य का नाम बहुत लंबा है।"
            d.address.trim().length > ADDRESS_MAX -> "The address is too long (up to $ADDRESS_MAX letters)." to "पता बहुत लंबा है ($ADDRESS_MAX अक्षर तक)।"
            phone.isNotEmpty() && !validPhone(phone) -> "Check the phone number (6 to 15 digits)." to "फ़ोन नंबर जाँचें (6 से 15 अंक)।"
            d.note.trim().length > NOTE_MAX -> "The note is too long (up to $NOTE_MAX letters)." to "नोट बहुत लंबा है ($NOTE_MAX अक्षर तक)।"
            else -> null
        }
    }

    fun validPhone(phone: String): Boolean = phone.all { it.isDigit() || it in " +-()" } && phone.count(Char::isDigit) in 6..15

    /** The person-facing line for a suggestion's state. Nothing is shown to anyone else until the Sahara team approves it. */
    fun statusText(status: String): Pair<String, String> = when (status) {
        "waiting" -> "Saved on this phone. It will be sent when you're online. Not checked yet." to "फ़ोन पर सहेजा गया। इंटरनेट मिलने पर भेजा जाएगा। अभी जाँचा नहीं गया।"
        "kept" -> "Not sent yet. It is kept on this phone and will be tried again. Not checked yet." to "अभी नहीं भेजा गया। यह फ़ोन पर रखा है, फिर से भेजा जाएगा। अभी जाँचा नहीं गया।"
        "approved" -> "Approved by the Sahara team." to "Sahara टीम ने मंज़ूर किया।"
        "rejected" -> "Checked by the Sahara team and not added." to "Sahara टीम ने जाँचा, सूची में नहीं जोड़ा गया।"
        else -> "Sent for review, not checked yet." to "समीक्षा के लिए भेजा गया, अभी जाँचा नहीं गया।"
    }
}

/** Suggestions travel in the account's send-later queue (CareSyncQueue) as their own kind, so no signal never loses one. */
internal object CentreSuggestionOps {
    const val KIND = "centre.suggest"
    /** Queue rows need a person id; a suggestion belongs to no care profile, so it uses this fixed marker. */
    const val PERSON = "sahara-directory"
    const val TABLE = "/rest/v1/sahara_centre_suggestions"

    fun write(draft: CentreSuggestionDraft, id: String = newId(), now: Long = System.currentTimeMillis()): CarePendingWrite {
        require(CentreSuggestionRules.error(draft) == null)
        val row = JSONObject().put("id", id).put("name", draft.name.trim()).put("city", draft.city.trim()).put("state", draft.state.trim())
            .put("address", draft.address.trim()).put("phone", draft.phone.trim()).put("note", draft.note.trim()).put("saved_at", java.time.Instant.ofEpochMilli(now).toString())
        return CarePendingWrite(kind = KIND, personId = PERSON, recordKey = id, payload = row.toString(), isAppend = true, createdAt = now)
    }

    /** A queued suggestion as the person's list shows it. */
    fun fromPending(op: CarePendingWrite): CentreSuggestion = JSONObject(op.payload).let {
        CentreSuggestion(it.getString("id"), it.getString("name"), it.getString("city"), it.getString("state"), if (op.state == CareSyncQueue.FAILED) "kept" else "waiting", op.createdAt)
    }

    fun fromServer(rows: JSONArray): List<CentreSuggestion> = (0 until rows.length()).map { rows.getJSONObject(it) }.map {
        CentreSuggestion(it.getString("id"), it.getString("name"), it.getString("city"), it.getString("state"), it.optString("status", "pending"),
            runCatching { java.time.OffsetDateTime.parse(it.getString("created_at")).toInstant().toEpochMilli() }.getOrDefault(0L))
    }

    /** Unsent ones first, then the server's, newest first; a row the server already holds is not listed twice. */
    fun merge(server: List<CentreSuggestion>, pending: List<CarePendingWrite>): List<CentreSuggestion> {
        val local = pending.filter { it.kind == KIND }.map(::fromPending)
        return (local + server.filter { s -> local.none { it.id == s.id } }).sortedByDescending { it.createdAt }
    }
}

/** Insert that is safe to repeat: the same id twice means the first send already arrived (the author can read their own row). */
internal suspend fun CareCloud.sendCentreSuggestion(op: CarePendingWrite, account: String) {
    val row = JSONObject(op.payload)
    try { request("POST", CentreSuggestionOps.TABLE, row, expectedAccountId = account) } catch (e: CareHttpException) {
        if (e.code != "23505") throw e
        val same = JSONArray(request("GET", CentreSuggestionOps.TABLE + "?id=eq.${row.getString("id")}&select=id", expectedAccountId = account))
        if (same.length() != 1) throw CareSyncConflict("This suggestion could not be sent. It stays on this phone.")
    }
}

/** This account's own suggestions on the server (staff reviewers can read more rows, so the list is filtered to the author). */
internal suspend fun CareCloud.myCentreSuggestions(account: String): List<CentreSuggestion> =
    CentreSuggestionOps.fromServer(JSONArray(request("GET", CentreSuggestionOps.TABLE + "?user_id=eq.$account&select=id,name,city,state,status,created_at&order=created_at.desc&limit=20", expectedAccountId = account)))

/** "Suggest a centre that's missing": a short form, then the honest state of what was sent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CentreSuggestionSheet(vm: CareModel, initialState: String, onDismiss: () -> Unit) {
    val t = careText()
    var name by rememberSaveable { mutableStateOf("") }; var city by rememberSaveable { mutableStateOf("") }; var state by rememberSaveable { mutableStateOf(initialState) }
    var address by rememberSaveable { mutableStateOf("") }; var phone by rememberSaveable { mutableStateOf("") }; var note by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf<Pair<String, String>?>(null) }
    var result by rememberSaveable { mutableStateOf("") }
    var more by rememberSaveable { mutableStateOf(false) }
    val sending by vm.centreSuggestionSending.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Cream) {
        val fieldShape = RoundedCornerShape(14.dp)
        val fieldColors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper, focusedBorderColor = Rust, focusedLabelColor = RustText, cursorColor = Rust)
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()).testTag("centre-suggestion-sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Suggest a centre that's missing", "छूटा हुआ केंद्र सुझाएँ"), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 22.sp, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            when {
                vm.cloud == null -> {
                    Text(t("Sign in to send a suggestion. The Sahara team checks every suggestion before anyone else sees it.", "सुझाव भेजने के लिए साइन इन करें। हर सुझाव दूसरों को दिखने से पहले Sahara टीम जाँचती है।"), color = DoctorCardInk)
                    OutlinedButton(onClick = onDismiss, shape = fieldShape, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Close", "बंद करें")) }
                }
                result.isNotBlank() -> {
                    val (en, hi) = CentreSuggestionRules.statusText(result)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(24.dp), tint = DoctorStatusGood); Spacer(Modifier.width(8.dp))
                        Text(t(en, hi), fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.weight(1f).testTag("centre-suggestion-result").announce())
                    }
                    Text(t("It is not shown to anyone else until the Sahara team checks it.", "Sahara टीम की जाँच से पहले यह किसी और को नहीं दिखेगा।"), color = DoctorCardSub)
                    Button(onClick = onDismiss, shape = fieldShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(t("Done", "हो गया"), fontWeight = FontWeight.SemiBold) }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = DoctorCardSub); Spacer(Modifier.width(8.dp))
                        Text(t("Please don't add patient details.", "मरीज़ की जानकारी न लिखें।"), color = DoctorCardSub, fontSize = 14.sp)
                    }
                    OutlinedTextField(name, { name = it.take(CentreSuggestionRules.NAME_MAX) }, label = { Text(t("Centre name", "केंद्र का नाम")) }, singleLine = true, shape = fieldShape, colors = fieldColors, modifier = Modifier.fillMaxWidth().testTag("suggest-name"))
                    OutlinedTextField(city, { city = it.take(CentreSuggestionRules.PLACE_MAX) }, label = { Text(t("Town or city", "शहर या क़स्बा")) }, singleLine = true, shape = fieldShape, colors = fieldColors, modifier = Modifier.fillMaxWidth().testTag("suggest-city"))
                    OutlinedTextField(state, { state = it.take(CentreSuggestionRules.PLACE_MAX) }, label = { Text(t("State", "राज्य")) }, singleLine = true, shape = fieldShape, colors = fieldColors, modifier = Modifier.fillMaxWidth().testTag("suggest-state"))
                    // Optional fields stay folded so the form is three boxes and a button; anything already typed keeps them open.
                    if (more || address.isNotBlank() || phone.isNotBlank() || note.isNotBlank()) {
                        OutlinedTextField(address, { address = it.take(CentreSuggestionRules.ADDRESS_MAX) }, label = { Text(t("Address (optional)", "पता (ज़रूरी नहीं)")) }, shape = fieldShape, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(phone, { phone = it.take(20) }, label = { Text(t("Centre phone (optional)", "केंद्र का फ़ोन (ज़रूरी नहीं)")) }, singleLine = true, shape = fieldShape, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
                        OutlinedTextField(note, { note = it.take(CentreSuggestionRules.NOTE_MAX) }, label = { Text(t("What care do they offer? (optional)", "वहाँ कौन-सी देखभाल मिलती है? (ज़रूरी नहीं)")) }, shape = fieldShape, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                    } else TextButton(onClick = { more = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("suggest-more")) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = RustText); Spacer(Modifier.width(8.dp)); Text(t("Add address, phone or services", "पता, फ़ोन या सेवाएँ जोड़ें"), color = RustText)
                    }
                    problem?.let { (en, hi) -> Text(t(en, hi), color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.announce()) }
                    Button(onClick = {
                        val draft = CentreSuggestionDraft(name, city, state, address, phone, note)
                        problem = CentreSuggestionRules.error(draft)
                        if (problem == null) vm.suggestCentre(draft) { status -> result = status }
                    }, enabled = !sending, shape = fieldShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("suggest-send")) {
                        if (sending) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)) }
                        Text(t("Send for review", "जाँच के लिए भेजें"), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** The person's own suggestions with their review state; shown on Nearby only when there are some, folded to one line until opened. */
@Composable fun CentreSuggestionList(items: List<CentreSuggestion>) {
    val t = careText()
    var open by rememberSaveable { mutableStateOf(false) }
    SurfaceCard {
        TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("centre-suggestion-toggle")) {
            Text(t("Your suggestions (${items.size})", "आपके सुझाव (${items.size})"), fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
            Text(if (open) t("Hide", "छिपाएँ") else t("Show", "दिखाएँ"), color = RustText)
        }
        if (open) items.forEachIndexed { index, s ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = DoctorHairline) else Spacer(Modifier.height(8.dp))
            Text(s.name + listOf(s.city, s.state).filter(String::isNotBlank).joinToString(", ").let { if (it.isBlank()) "" else " · $it" }, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            val (en, hi) = CentreSuggestionRules.statusText(s.status)
            // A colour dot carries the state at a glance: green approved, rust not added, amber still waiting.
            val dot = when (s.status) { "approved" -> DoctorStatusGood; "rejected" -> DoctorStatusRest; else -> DoctorStatusWarn }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(8.dp))
                Text(t(en, hi), fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.weight(1f).testTag("centre-suggestion-status-${s.id}"))
            }
        }
    }
}
