@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One daily distress answer. [distress] false is the one-tap "No"; true carries the 0–10 score and any ticked problems. */
internal data class DistressCheck(val distress: Boolean, val score: Int? = null, val problems: List<String> = emptyList(), val other: String = "")

/**
 * NCCN Distress Thermometer and Problem List, English wording copied exactly from NCCN Guidelines Version 1.2026
 * (Distress Management, DIS-A, 1/14/2026). Item ids are the exact English wording so saved records stay readable.
 * The Hindi labels are Sahara's own translation: NCCN's Hindi version needs an NCCN login and was not checked.
 */
internal object DistressRules {
    const val SOURCE = "NCCN Distress Thermometer and Problem List"
    const val VERSION = "NCCN Guidelines Version 1.2026, Distress Management"
    const val NO_TITLE = "No distress today"
    const val LOW_ANCHOR = "No distress"
    const val HIGH_ANCHOR = "Extreme distress"
    const val OTHER_LIMIT = 500

    data class Category(val en: String, val hi: String, val items: List<Pair<String, String>>)

    val categories = listOf(
        Category("Physical Concerns", "शारीरिक चिंताएँ", listOf(
            "Pain" to "दर्द", "Sleep" to "नींद", "Fatigue" to "थकान", "Tobacco use" to "तंबाकू का सेवन", "Substance use" to "नशीले पदार्थों का सेवन",
            "Memory or concentration" to "याददाश्त या ध्यान लगाना", "Sexual health" to "यौन स्वास्थ्य", "Changes in eating" to "खाने-पीने में बदलाव",
            "Loss or change of physical abilities" to "शारीरिक क्षमता में कमी या बदलाव")),
        Category("Emotional Concerns", "भावनात्मक चिंताएँ", listOf(
            "Worry or anxiety" to "चिंता या घबराहट", "Sadness or depression" to "उदासी या अवसाद", "Loss of interest or enjoyment" to "रुचि या आनंद में कमी",
            "Grief or loss" to "शोक या कुछ खो देना", "Fear" to "डर", "Loneliness" to "अकेलापन", "Anger" to "गुस्सा", "Changes in appearance" to "रूप-रंग में बदलाव",
            "Feelings of worthlessness or being a burden" to "बेकार या बोझ होने का एहसास")),
        Category("Social Concerns", "सामाजिक चिंताएँ", listOf(
            "Relationship with spouse or partner" to "पति/पत्नी या साथी से रिश्ता", "Relationship with children" to "बच्चों से रिश्ता",
            "Relationship with family members" to "परिवार के लोगों से रिश्ता", "Relationship with friends or coworkers" to "दोस्तों या साथ काम करने वालों से रिश्ता",
            "Communication with health care team" to "इलाज करने वाली टीम से बातचीत", "Ability to have children" to "संतान होने की क्षमता",
            "Prejudice or discrimination" to "पूर्वाग्रह या भेदभाव")),
        Category("Practical Concerns", "व्यावहारिक चिंताएँ", listOf(
            "Taking care of myself" to "अपनी देखभाल करना", "Taking care of others" to "दूसरों की देखभाल करना", "Safety" to "सुरक्षा", "Work" to "काम",
            "School" to "पढ़ाई", "Housing/Utilities" to "घर/बिजली-पानी", "Finances" to "पैसे", "Insurance" to "बीमा", "Transportation" to "आने-जाने का साधन",
            "Child care" to "बच्चों की देखभाल", "Having enough food" to "पर्याप्त खाना होना", "Access to medicine" to "दवा मिल पाना", "Treatment decisions" to "इलाज के फ़ैसले")),
        Category("Spiritual or Religious Concerns", "आध्यात्मिक या धार्मिक चिंताएँ", listOf(
            "Sense of meaning or purpose" to "जीवन का अर्थ या उद्देश्य", "Changes in faith or beliefs" to "आस्था या मान्यताओं में बदलाव",
            "Death, dying, or afterlife" to "मृत्यु, मरना या मृत्यु के बाद", "Conflict between beliefs and cancer treatments" to "मान्यताओं और कैंसर के इलाज के बीच टकराव",
            "Relationship with the sacred" to "ईश्वर या पवित्र से रिश्ता", "Ritual or dietary needs" to "धार्मिक रीति या खान-पान की ज़रूरतें")),
    )
    private val hindiLabels = categories.flatMap { it.items }.toMap()
    val problems: List<String> = categories.flatMap { c -> c.items.map { it.first } }

    fun problemLabel(id: String, hindi: Boolean) = if (hindi) hindiLabels[id] ?: id else id
    fun title(check: DistressCheck) = if (!check.distress) NO_TITLE else "Distress ${check.score}/10"
    fun shownTitle(check: DistressCheck, hindi: Boolean) = when { !hindi -> title(check); !check.distress -> "आज कोई परेशानी नहीं"; else -> "परेशानी ${check.score}/10" }

    /** Only the score is required; "No" carries nothing else. */
    fun error(check: DistressCheck): String? = when {
        !check.distress && (check.score != null || check.problems.isNotEmpty() || check.other.isNotBlank()) -> "A \"No\" answer has no score or problems."
        check.distress && (check.score == null || check.score !in 0..10) -> "Choose a number from 0 to 10."
        check.problems.any { it !in problems } || check.problems.distinct().size != check.problems.size -> "Choose items from the problem list."
        check.other.length > OTHER_LIMIT -> "Please shorten the other concern."
        else -> null
    }

    fun isDistress(item: CareItem) = item.kind == "checkin" && DistressCodec.isStructured(item.detail)

    /** Today's distress answer for this person; tapping again updates it instead of adding a second one. */
    fun today(items: List<CareItem>, personId: String, dayStart: Long): CareItem? =
        items.filter { it.personId == personId && isDistress(it) && it.created >= dayStart }.maxByOrNull { it.created }

    /** Every readable distress answer for this person, newest first. */
    fun history(items: List<CareItem>, personId: String): List<Pair<CareItem, DistressCheck>> = items
        .filter { it.personId == personId && isDistress(it) }
        .mapNotNull { item -> DistressCodec.decode(item.detail)?.let { item to it } }
        .sortedWith(compareByDescending<Pair<CareItem, DistressCheck>> { it.first.created }.thenBy { it.first.id })

    /** What to save for an answer: a new item, today's item changed, or null when the same answer is already saved. */
    fun change(today: CareItem?, check: DistressCheck): CareItem? {
        val detail = DistressCodec.encode(check)
        return when {
            today == null -> CareItem(personId = "", kind = "checkin", title = title(check), detail = detail)
            today.detail == detail -> null
            else -> today.copy(title = title(check), detail = detail)
        }
    }

    fun summary(check: DistressCheck, hindi: Boolean): String = (check.problems.map { problemLabel(it, hindi) } + listOfNotNull(check.other.trim().takeIf { it.isNotEmpty() })).joinToString(", ")
}

/** Stored in a check-in's detail, the same way symptom notes are, so it syncs through shared care with no new server table. */
internal object DistressCodec {
    const val PREFIX = "sahara:distress:v1\n"
    fun isStructured(text: String) = text.startsWith(PREFIX)
    fun encode(check: DistressCheck): String {
        require(DistressRules.error(check) == null) { DistressRules.error(check)!! }
        val ordered = DistressRules.problems.filter { it in check.problems }
        return PREFIX + JSONObject().put("distress", check.distress).put("score", check.score ?: JSONObject.NULL)
            .put("problems", JSONArray(ordered)).put("other", check.other.trim()).put("source", DistressRules.VERSION).toString()
    }
    fun decode(text: String): DistressCheck? {
        if (!isStructured(text)) return null
        return runCatching {
            val j = JSONObject(text.removePrefix(PREFIX)); val list = j.getJSONArray("problems")
            DistressCheck(j.getBoolean("distress"), if (j.isNull("score")) null else j.getInt("score"), (0 until list.length()).map { list.getString(it) }, j.optString("other"))
                .also { require(DistressRules.error(it) == null) }
        }.getOrNull()
    }
}

/** "Are you in any distress today?" under the mood buttons: one tap saves "No"; "Yes" opens the thermometer screen. */
@Composable fun DistressQuestion(vm: CareModel, onYes: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val today = DistressRules.today(items, person, dayStart); val answer = today?.let { DistressCodec.decode(it.detail) }
    val capturedAccount = remember(person) { vm.accountNamespace }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("distress-question")) {
        Text(t("Are you in any distress today?", "क्या आज आप किसी परेशानी में हैं?"), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = answer?.distress == true, enabled = !busy, onClick = onYes, label = { Text(t("Yes", "हाँ"), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("distress-yes"))
            FilterChip(selected = answer?.distress == false, enabled = !busy, onClick = {
                val change = DistressRules.change(today, DistressCheck(false))
                if (change != null && canEditCareContext(vm, person, capturedAccount)) { if (today != null) vm.updateItem(change) {} else vm.saveItem("checkin", change.title, change.detail) }
            }, label = { Text(t("No", "नहीं"), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("distress-no"))
        }
        if (answer?.distress == true) Text(t("Saved today: ${answer.score}/10. Tap Yes to change it.", "आज सहेजा: ${answer.score}/10। बदलने के लिए हाँ दबाएँ।"), fontSize = 13.sp, color = DoctorCardSub)
    }
}

/** One screen: the 0–10 thermometer (the only required answer), the problem list as tick boxes, and "Any other concern". */
@Composable fun DistressScreen(vm: CareModel, onClose: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val modelError by vm.actionError.collectAsStateWithLifecycle()
    val dayStart = remember { LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val today = remember(person) { DistressRules.today(items, person, dayStart) }
    val previous = remember(today) { today?.let { DistressCodec.decode(it.detail) }?.takeIf { it.distress } }
    var score by rememberSaveable { mutableStateOf(previous?.score ?: -1) }
    var ticked by rememberSaveable { mutableStateOf(previous?.problems.orEmpty().joinToString("\n")) }
    var other by rememberSaveable { mutableStateOf(previous?.other.orEmpty()) }
    val capturedAccount = remember(person) { vm.accountNamespace }
    val chosen = ticked.split("\n").filter { it.isNotEmpty() }.toSet()
    LaunchedEffect(Unit) { vm.actionError.value = null }
    BackHandler { onClose() }
    Column(Modifier.fillMaxSize().background(Cream).safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).testTag("distress-screen"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("distress-close")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Close", "बंद करें"), tint = Ink) }
            Heading(t("Distress Thermometer", "परेशानी थर्मामीटर"), 26)
        }
        Text(t("Choose the number (0–10) that best describes how much distress you have been experiencing in the past week, including today.", "0 से 10 में वह अंक चुनें जो सबसे अच्छी तरह बताए कि पिछले एक हफ़्ते में, आज समेत, आपको कितनी परेशानी रही है।"), fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardInk)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(t("0 = No distress", "0 = कोई परेशानी नहीं"), fontSize = 13.sp, color = DoctorCardSub); Text(t("10 = Extreme distress", "10 = बहुत ज़्यादा परेशानी"), fontSize = 13.sp, color = DoctorCardSub) }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..10).forEach { n ->
                val on = score == n
                Surface(shape = CircleShape, color = if (on) Rust else Paper, border = BorderStroke(1.dp, if (on) Rust else DoctorHairline),
                    modifier = Modifier.size(52.dp).clickable(role = Role.RadioButton) { score = n }.semantics { contentDescription = when (n) { 0 -> t("0, No distress", "0, कोई परेशानी नहीं"); 10 -> t("10, Extreme distress", "10, बहुत ज़्यादा परेशानी"); else -> "$n" } }.testTag("distress-score-$n")) {
                    Box(contentAlignment = Alignment.Center) { Text("$n", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else DoctorCardInk) }
                }
            }
        }
        Text(t("Have you had concerns about any of the items below in the past week, including today? (Optional. Mark all that apply.)", "क्या पिछले एक हफ़्ते में, आज समेत, आपको नीचे दी गई किसी बात की चिंता रही है? (चाहें तो। जितनी लागू हों, सब चुनें।)"), fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardInk)
        DistressRules.categories.forEach { category ->
            Text(t(category.en, category.hi), fontFamily = SaharaTitleFont, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            category.items.forEach { (id, _) ->
                val on = id in chosen
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { ticked = (if (on) chosen - id else chosen + id).joinToString("\n") }.testTag("distress-problem-$id"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = on, onCheckedChange = null); Spacer(Modifier.width(8.dp)); Text(DistressRules.problemLabel(id, hindi), fontSize = 15.sp, color = DoctorCardInk)
                }
            }
        }
        OutlinedTextField(other, { if (it.length <= DistressRules.OTHER_LIMIT) other = it }, label = { Text(t("Any other concern (optional)", "कोई और चिंता (चाहें तो)")) }, minLines = 2, maxLines = 4, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("distress-other"))
        modelError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
        Button(onClick = {
            val check = DistressCheck(true, score, DistressRules.problems.filter { it in chosen }, other.trim())
            val change = DistressRules.change(today, check)
            when { change == null -> onClose(); !canEditCareContext(vm, person, capturedAccount) -> onClose(); today != null -> vm.updateItem(change, onClose); else -> vm.saveItem("checkin", change.title, change.detail, 0, onClose) }
        }, enabled = !busy && score in 0..10, shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("distress-save")) {
            Text(if (score in 0..10) t("Save", "सहेजें") else t("Choose a number to save", "सहेजने के लिए अंक चुनें"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        DistressSource()
    }
}

@Composable private fun DistressSource() {
    val t = careText()
    Text(t("Source: NCCN Distress Thermometer and Problem List, NCCN Guidelines Version 1.2026 (Distress Management).", "स्रोत: NCCN Distress Thermometer and Problem List, NCCN Guidelines संस्करण 1.2026। हिंदी अनुवाद Sahara का है, NCCN का नहीं।"), fontSize = 12.sp, lineHeight = 16.sp, color = DoctorCardFaint, modifier = Modifier.testTag("distress-source"))
}

/** A saved distress answer in the Check-ins list. */
@Composable fun DistressEntryCard(item: CareItem) {
    val t = careText(); val hindi = careHindi()
    val check = DistressCodec.decode(item.detail)
    SurfaceCard {
        Text(check?.let { DistressRules.shownTitle(it, hindi) } ?: t("This distress answer could not be read.", "यह परेशानी वाला जवाब पढ़ा नहीं जा सका।"), fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
        check?.let { DistressRules.summary(it, hindi) }?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text(date(item.created), style = MaterialTheme.typography.bodySmall)
        DistressSource()
    }
}

/** Score history, oldest to newest left to right, then the latest answers with their problems. Shown to family and doctor. */
@Composable fun DistressHistoryCard(entries: List<CareItem>, personId: String) {
    val t = careText(); val hindi = careHindi()
    val history = remember(entries, personId) { DistressRules.history(entries, personId) }
    if (history.isEmpty()) return
    val day = careDateFormat("d MMM", hindi)
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("distress-history")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("Distress score history", "परेशानी के अंकों का इतिहास"), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                history.take(14).reversed().forEach { (item, check) ->
                    val value = check.score ?: 0
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (check.distress) "$value" else t("No", "नहीं"), fontSize = 11.sp, color = DoctorCardSub, maxLines = 1)
                        Box(Modifier.fillMaxWidth().height((4 + value * 6).dp).background(if (check.distress) Rust else Sage, RoundedCornerShape(4.dp)))
                        Text(Instant.ofEpochMilli(item.created).atZone(ZoneId.systemDefault()).format(day), fontSize = 9.sp, color = DoctorCardFaint, maxLines = 1)
                    }
                }
            }
            history.take(5).forEach { (item, check) ->
                Text("${Instant.ofEpochMilli(item.created).atZone(ZoneId.systemDefault()).format(day)} · ${DistressRules.shownTitle(check, hindi)}" + DistressRules.summary(check, hindi).let { if (it.isBlank()) "" else " · $it" }, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardInk)
            }
            Text(t("Self-reported. Not a diagnosis.", "अपनी बताई सूचना। यह निदान नहीं है।"), fontSize = 12.sp, color = DoctorCardFaint)
            DistressSource()
        }
    }
}
