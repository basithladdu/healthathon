package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** One person physically in the room, written down by the clinician. Names are self-entered and verify no identity. [consented] must be ticked explicitly and never defaults to true. */
internal data class DoctorAttendee(val id: String, val name: String, val role: String, val consented: Boolean = false)

/** Private on-device list of who is present. Nobody listed here has been contacted, invited or notified. */
internal class DoctorAttendeeStore(private val read: (String) -> String?, private val write: (String, String) -> Boolean) {
    constructor(context: Context, namespace: String) : this(
        { key -> context.getSharedPreferences("doctor-attendees-${DoctorConversationContext.sha256(namespace.toByteArray())}", Context.MODE_PRIVATE).getString(key, null) },
        { key, value -> context.getSharedPreferences("doctor-attendees-${DoctorConversationContext.sha256(namespace.toByteArray())}", Context.MODE_PRIVATE).edit().putString(key, value).commit() })
    fun load(scope: String): List<DoctorAttendee> {
        val raw = read(scope) ?: return emptyList()
        val rows = JSONArray(raw)
        return (0 until rows.length()).map { rows.getJSONObject(it).let { row -> DoctorAttendee(row.getString("id"), row.getString("name"), row.getString("role"), row.optBoolean("consented", false)) } }.also(::validate)
    }
    fun save(scope: String, rows: List<DoctorAttendee>) {
        validate(rows)
        check(write(scope, JSONArray(rows.map { JSONObject().put("id", it.id).put("name", it.name).put("role", it.role).put("consented", it.consented) }).toString())) { "People present were not saved. Keep this list open and try again." }
    }
    companion object {
        fun scope(account: String, person: String, discussion: String): String {
            require(person.isNotBlank())
            return DoctorConversationContext.sha256(listOf(account, person, discussion.ifBlank { "legacy-current-draft" }).joinToString("") { "${it.toByteArray(Charsets.UTF_8).size}:$it" }.toByteArray(Charsets.UTF_8))
        }
        fun validate(rows: List<DoctorAttendee>) {
            require(rows.size <= 12 && rows.map { it.id }.distinct().size == rows.size) { "Keep up to 12 people." }
            require(rows.all { it.id.isNotBlank() && it.name.trim().length in 1..100 }) { "Enter a name of 1–100 characters." }
            require(rows.all { it.role.trim().length in 1..40 }) { "Enter a role of 1–40 characters, such as Daughter or Nurse." }
        }
    }
}

/** Hindi for the store's checks; the English stays in the store, which tests rely on. */
internal val DoctorAttendeeErrorsHindi = mapOf(
    "People present were not saved. Keep this list open and try again." to "मौजूद लोगों की सूची सहेजी नहीं गई। यह सूची खुली रखें और फिर कोशिश करें।",
    "Keep up to 12 people." to "ज़्यादा से ज़्यादा 12 लोग रखें।",
    "Enter a name of 1–100 characters." to "1–100 अक्षरों का नाम लिखें।",
    "Enter a role of 1–40 characters, such as Daughter or Nurse." to "1–40 अक्षरों में भूमिका लिखें, जैसे बेटी या नर्स।",
)

/** Starting list: the doctor and the patient, neither pre-ticked. Anyone else is added by hand. */
internal fun doctorAttendeeStartingList(doctorName: String, patientName: String): List<DoctorAttendee> =
    listOf(DoctorAttendee(newId(), doctorName.trim(), "Doctor", false), DoctorAttendee(newId(), patientName.trim(), "Patient", false))

/** Text for the "Who was there" note section, for example "Dr Meera Nair (Doctor), Rahul (Patient)". */
internal fun doctorAttendeesWhoWasThere(rows: List<DoctorAttendee>): String = rows.joinToString(", ") { "${it.name.trim()} (${it.role.trim()})" }

/** True only when the list is non-empty and every listed person has explicitly consented. Recording must not start otherwise. */
internal fun doctorAttendeesAllConsented(rows: List<DoctorAttendee>): Boolean = rows.isNotEmpty() && rows.all { it.consented }

/** Shows the saved list for [scope], or [initial] when nothing is saved yet. [onChange] receives the current list on load and after every change. */
@Composable internal fun DoctorAttendeesList(store: DoctorAttendeeStore, scope: String, initial: List<DoctorAttendee>, editable: Boolean, onChange: (List<DoctorAttendee>) -> Unit) { val t = careText();
    var rows by remember(store, scope) { mutableStateOf(emptyList<DoctorAttendee>()) }
    var loaded by remember(store, scope) { mutableStateOf(false) }
    var error by remember(store, scope) { mutableStateOf<String?>(null) }
    // A half-typed person survives scrolling this card away and rotation.
    var name by rememberSaveable(scope) { mutableStateOf("") }
    var adding by rememberSaveable(scope) { mutableStateOf(false) }
    var role by rememberSaveable(scope) { mutableStateOf("") }
    val callback by rememberUpdatedState(onChange)
    fun persist(next: List<DoctorAttendee>): Boolean = try { check(editable) { t("This list can no longer be changed.", "यह सूची अब बदली नहीं जा सकती।") }; store.save(scope, next); rows = next; error = null; callback(next); true } catch (e: Exception) { error = e.message?.let { m -> t(m, DoctorAttendeeErrorsHindi[m] ?: m) } ?: t("Couldn't save. Try again.", "सहेजा नहीं गया। फिर कोशिश करें।"); false }
    fun load() { try { val saved = store.load(scope); rows = if (saved.isEmpty()) initial else saved; loaded = true; error = null; callback(rows) } catch (_: Exception) { error = t("Couldn't load. Try again.", "नहीं खुला। फिर कोशिश करें।"); loaded = false } }
    LaunchedEffect(store, scope) { load() }
    val all = doctorAttendeesAllConsented(rows)
    Card(Modifier.fillMaxWidth().testTag("doctor-attendees"), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = DoctorCardReport), border = BorderStroke(1.dp, DoctorHairline)) {
        Column(Modifier.saharaContentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Who's here", "कौन मौजूद है"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            error?.let { Text(it, fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("doctor-attendees-error").announce()) }
            if (!loaded) TextButton(onClick = { load() }, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-attendees-retry")) { Text(t("Retry", "फिर कोशिश करें"), color = RustText, fontSize = 16.sp) }
            rows.forEachIndexed { i, row -> if (i > 0) HorizontalDivider(color = DoctorHairline); Column(Modifier.fillMaxWidth().testTag("doctor-attendee-${row.id}")) {
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(row.consented, { checked -> persist(rows.map { if (it.id == row.id) it.copy(consented = checked) else it }) }, enabled = loaded && editable, modifier = Modifier.minimumInteractiveComponentSize().testTag("doctor-attendee-consent-${row.id}"))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(row.name, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                        Text(row.role, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
                        Text(if (row.consented) t("Agreed", "सहमत") else t("Not agreed", "सहमति नहीं"), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = if (row.consented) DoctorStatusGood else DoctorStatusWarn)
                    }
                    if (editable) TextButton(onClick = { persist(rows.filterNot { it.id == row.id }) }, enabled = loaded, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-attendee-remove-${row.id}")) { Text(t("Remove", "हटाएँ"), color = RustText) }
                }
            } }
            if (rows.isNotEmpty()) HorizontalDivider(color = DoctorHairline)
            if (editable && !adding) OutlinedButton(onClick = { adding = true }, enabled = loaded && rows.size < 12, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = RustText), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-add-attendee-open")) { Icon(Icons.Default.PersonAdd, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(t("Add person", "व्यक्ति जोड़ें"), fontSize = 16.sp) }
            // Name and role open only when the doctor adds someone.
            if (editable && adding) {
                val canAdd = loaded && rows.size < 12 && name.isNotBlank() && role.isNotBlank()
                val add = { if (persist(rows + DoctorAttendee(newId(), name.trim(), role.trim(), false))) { name = ""; role = ""; adding = false } }
                OutlinedTextField(name, { name = it.take(100) }, label = { Text(t("Name", "नाम")) }, singleLine = true, enabled = loaded, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doctor-attendee-name-input"))
                OutlinedTextField(role, { role = it.take(40) }, label = { Text(t("Role", "भूमिका")) }, placeholder = { Text(t("Daughter, nurse…", "बेटी, नर्स…")) }, singleLine = true, enabled = loaded, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { if (canAdd) add() }), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doctor-attendee-role-input"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = add, enabled = canAdd, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = RustText), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-add-attendee")) { Icon(Icons.Default.PersonAdd, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(t("Add person", "व्यक्ति जोड़ें"), fontSize = 16.sp) }
                    // Closes the two fields again without adding anyone, so no blank person rows stay open.
                    TextButton(onClick = { name = ""; role = ""; adding = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-add-attendee-cancel")) { Text(t("Cancel", "रद्द करें"), color = DoctorCardSub, fontSize = 16.sp) }
                }
            }
            if (all) Text(t("Everyone agreed", "सभी सहमत"), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = DoctorStatusGood, modifier = Modifier.testTag("doctor-attendees-status").announce())
            else Button(onClick = { persist(rows.map { it.copy(consented = true) }) }, enabled = loaded && editable && rows.isNotEmpty(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doctor-attendees-status")) { Text(t("Everyone agrees", "सभी सहमत हैं"), fontSize = 16.sp) }
        }
    }
}
