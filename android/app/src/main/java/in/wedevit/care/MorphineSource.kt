package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/**
 * "Could you help another family? Where did you get it?" After a dose of oral morphine is marked taken, the person is asked once (at most every
 * 30 days per medicine) where it came from. The answer is a place only: a centre from the directory, or a pharmacy/hospital
 * they type. It goes to the Sahara team privately (sahara_morphine_reports) to confirm which centres really dispense oral
 * morphine; the report keeps no account (only a one-way code for the daily limit) and is seen only by the team; nothing is public until the team has checked it is a recognised institution.
 */
data class MorphineSourceDraft(val centreId: String, val centreName: String, val city: String, val state: String, val place: String = "क्या आप किसी और परिवार की मदद करेंगे?")

object MorphineSourceRules {
    private val names = Regex("morph|morcontin|oramorph|sevredol|मॉर्फ|मार्फ|मॉर्फ़", RegexOption.IGNORE_CASE)
    const val ASK_EVERY_DAYS = 30L
    const val PLACE_MAX = 160

    fun isMorphine(title: String): Boolean = names.containsMatchIn(title)

    fun error(d: MorphineSourceDraft): Pair<String, String>? = when {
        d.centreId.isBlank() && d.place.trim().length < 3 -> "Choose a centre or type the place." to "कोई केंद्र चुनें या जगह का नाम लिखें।"
        d.place.trim().length > PLACE_MAX -> "The place name is too long." to "जगह का नाम बहुत लंबा है।"
        else -> null
    }

    /** Directory search by name, town, district or state; empty query shows nothing (the list is long). */
    fun search(centres: List<Centre>, query: String, limit: Int = 25): List<Centre> {
        val q = query.trim().lowercase(); if (q.length < 2) return emptyList()
        return centres.asSequence().filter { c -> listOf(c.name, c.city, c.district, c.state).any { it.lowercase().contains(q) } }
            .sortedBy { if (it.name.lowercase().startsWith(q)) 0 else 1 }.take(limit).toList()
    }

    fun shouldAsk(context: Context, itemId: String, now: Long = System.currentTimeMillis()): Boolean {
        val last = context.getSharedPreferences("sahara-morphine-source", Context.MODE_PRIVATE).getLong(itemId, 0L)
        return now - last > ASK_EVERY_DAYS * 86_400_000L
    }
    fun asked(context: Context, itemId: String, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences("sahara-morphine-source", Context.MODE_PRIVATE).edit().putLong(itemId, now).apply()
    }
}

/** A morphine dose just marked taken; the prompt shows once wherever doses are marked. */
object MorphineSourcePrompt { val ask = MutableStateFlow<CareItem?>(null) }

internal object MorphineSourceOps {
    const val KIND = "morphine.source"
    const val PERSON = "sahara-directory"
    const val RPC = "/rest/v1/rpc/sahara_report_morphine"

    fun write(draft: MorphineSourceDraft, id: String = newId(), now: Long = System.currentTimeMillis()): CarePendingWrite {
        require(MorphineSourceRules.error(draft) == null)
        val row = JSONObject().put("id", id).put("centre_id", draft.centreId.take(200)).put("centre_name", draft.centreName.take(200)).put("city", draft.city.take(80))
            .put("state", draft.state.take(80)).put("place", draft.place.trim()).put("saved_at", java.time.Instant.ofEpochMilli(now).toString())
        return CarePendingWrite(kind = KIND, personId = PERSON, recordKey = id, payload = row.toString(), isAppend = true, createdAt = now)
    }
}

/** One call, safe to repeat (the same id twice is ignored). The server keeps no account on the report. */
internal suspend fun CareCloud.sendMorphineSource(op: CarePendingWrite, account: String) {
    request("POST", MorphineSourceOps.RPC, JSONObject().put("p_report", JSONObject(op.payload)), expectedAccountId = account)
}

/** Shown wherever doses are marked; asks only after a morphine dose was marked taken. */
@Composable internal fun MorphineSourceHost(vm: CareModel) {
    val asking by MorphineSourcePrompt.ask.collectAsState()
    val item = asking ?: return
    MorphineSourceSheet(vm, item) { MorphineSourcePrompt.ask.value = null }
}

@Composable internal fun MorphineSourceSheet(vm: CareModel, item: CareItem, onDone: () -> Unit) {
    val t = careText(); val context = LocalContext.current
    val centres = remember { runCatching { CareCentreDirectory.bundled(context) }.getOrDefault(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var chosen by remember { mutableStateOf<Centre?>(null) }
    var other by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { MorphineSourceRules.search(centres, query) }
    LaunchedEffect(item.id) { MorphineSourceRules.asked(context, item.id) }
    AlertDialog(onDismissRequest = onDone, modifier = Modifier.testTag("morphine-source"),
        title = { Text(t("Could you help another family?", "क्या आप किसी और परिवार की मदद करेंगे?"), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result.isNotBlank()) {
                    Text(t("Thank you for helping. We will check it, then share it with families who need it.", "मदद के लिए धन्यवाद। हम इसे जाँचेंगे, फिर ज़रूरतमंद परिवारों के साथ साझा करेंगे।"))
                    return@Column
                }
                Text(t("Oral morphine can be hard to find. If you share where you got yours, we can help families near you find it too. Your answer stays private with the Sahara team; other families only ever see the place, after we check it. This is completely up to you.",
                    "मुँह से ली जाने वाली मॉर्फ़ीन ढूँढना मुश्किल हो सकता है। अगर आप बताएँ कि आपको कहाँ से मिली, तो हम आपके पास के परिवारों को भी इसे ढूँढने में मदद कर सकते हैं। आपका जवाब सिर्फ़ Sahara टीम के पास रहता है; दूसरे परिवारों को जाँच के बाद सिर्फ़ जगह दिखती है। यह पूरी तरह आपकी मर्ज़ी है।"),
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(query, { query = it.take(60); chosen = null }, label = { Text(t("Where did you get it? Search a centre, town or state", "कहाँ से मिली? केंद्र, शहर या राज्य खोजें")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("morphine-source-search"))
                results.forEach { c ->
                    val picked = chosen?.id == c.id
                    Surface(shape = RoundedCornerShape(12.dp), color = if (picked) SaharaTone.Teal.wash else Paper, border = BorderStroke(1.dp, if (picked) SaharaTone.Teal.solid else DoctorHairline),
                        modifier = Modifier.fillMaxWidth().clickable { chosen = c; other = "" }.testTag("morphine-source-centre")) {
                        Column(Modifier.padding(10.dp)) {
                            Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(listOf(c.city, c.district, c.state).filter { it.isNotBlank() }.distinct().joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                OutlinedTextField(other, { other = it.take(MorphineSourceRules.PLACE_MAX); if (it.isNotBlank()) chosen = null },
                    label = { Text(t("Not listed? Pharmacy or hospital, and town", "सूची में नहीं? फ़ार्मेसी या अस्पताल, और शहर")) },
                    modifier = Modifier.fillMaxWidth().testTag("morphine-source-other"))
            }
        },
        confirmButton = {
            if (result.isNotBlank()) TextButton(onClick = onDone) { Text(t("Done", "हो गया")) }
            else {
                val c = chosen
                val draft = MorphineSourceDraft(c?.id.orEmpty(), c?.name.orEmpty(), c?.city.orEmpty(), c?.state.orEmpty(), if (c == null) other else "")
                TextButton(onClick = { vm.reportMorphineSource(draft) { result = it } }, enabled = MorphineSourceRules.error(draft) == null && vm.cloud != null,
                    modifier = Modifier.testTag("morphine-source-send")) { Text(t("Send", "भेजें"), fontWeight = FontWeight.SemiBold) }
            }
        },
        dismissButton = { if (result.isBlank()) TextButton(onClick = onDone, modifier = Modifier.testTag("morphine-source-skip")) { Text(t("Not now", "अभी नहीं")) } })
}
