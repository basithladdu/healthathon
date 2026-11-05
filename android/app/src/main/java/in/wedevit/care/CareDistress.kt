@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

    fun problemLabel(id: String, hindi: Boolean) = careTr(hindi, id, hindiLabels[id] ?: id)
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

    /** Chart points, oldest first: (when saved, score, whether it was a "Yes"). A "No" answer counts as 0. */
    fun series(history: List<Pair<CareItem, DistressCheck>>): List<Triple<Long, Int, Boolean>> =
        history.sortedBy { it.first.created }.map { (item, check) -> Triple(item.created, if (check.distress) check.score ?: 0 else 0, check.distress) }

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
            // A saved score shows on the Yes chip itself ("Yes · 7/10"); tapping it again opens the thermometer to change it.
            FilterChip(selected = answer?.distress == true, enabled = !busy, onClick = onYes, label = { Text(t("Yes", "हाँ") + (answer?.takeIf { it.distress }?.score?.let { " · $it/10" } ?: ""), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("distress-yes"))
            FilterChip(selected = answer?.distress == false, enabled = !busy, onClick = {
                val change = DistressRules.change(today, DistressCheck(false))
                if (change != null && canEditCareContext(vm, person, capturedAccount)) { if (today != null) vm.updateItem(change) {} else vm.saveItem("checkin", change.title, change.detail) }
            }, label = { Text(t("No", "नहीं"), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("distress-no"))
        }
    }
}

/** One screen: the 0–10 thermometer (the only required answer), the problem list as tick boxes, and "Any other concern". */
@Composable fun DistressScreen(vm: CareModel, onClose: () -> Unit) {
    val t = careText()
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
    val hindi = careHindi()
    // Which group is open (-1 none; 1..5 the NCCN groups, 6 "Any other concern"). Nothing opens by itself: only a tap opens a group.
    var open by rememberSaveable { mutableStateOf(-1) }
    var doneSteps by rememberSaveable { mutableStateOf("") }
    val finished = doneSteps.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    fun markDone(step: Int) { doneSteps = (finished + step).joinToString(",") }
    LaunchedEffect(Unit) { vm.actionError.value = null }
    BackHandler { onClose() }
    Column(Modifier.fillMaxSize().background(Cream).safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp).testTag("distress-screen"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("distress-close")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Close", "बंद करें"), tint = Ink) }
            Box(Modifier.weight(1f)) { Heading(t("Distress Thermometer", "परेशानी थर्मामीटर"), 24) }
            CareChartInfoButton(t("About this", "इसके बारे में"), distressAbout(t), "distress")
            CareLanguageMenu(compact = true)
        }
        Text(t("How much distress have you felt in the past week, including today?", "पिछले एक हफ़्ते में, आज समेत, आपको कितनी परेशानी रही है?"), fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardInk)
        // The thermometer on the left, the six concern groups beside it, so the whole check fits one screen.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DistressThermometer(score, Modifier.width(112.dp)) { score = it }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DistressRules.categories.forEachIndexed { index, category ->
                    val step = index + 1
                    val (icon, colour) = distressGroupStyle(category.en)
                    DistressGroupTile(t(category.en, category.hi), category.items.count { it.first in chosen }, step in finished, open == step, icon, colour, "distress-step-$step") { open = if (open == step) -1 else step }
                }
                DistressGroupTile(t("Any other concern", "कोई और चिंता"), if (other.isNotBlank()) 1 else 0, 6 in finished, open == 6, Icons.Filled.Edit, DoctorCardSub, "distress-step-other") { open = if (open == 6) -1 else 6 }
            }
        }
        DistressRules.categories.forEachIndexed { index, category ->
            val step = index + 1
            val (_, colour) = distressGroupStyle(category.en)
            AnimatedVisibility(open == step, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t(category.en, category.hi), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
                    DistressProblemGroup(category, colour, chosen) { id -> ticked = (if (id in chosen) chosen - id else chosen + id).joinToString("\n") }
                    OutlinedButton(onClick = { markDone(step); open = -1 }, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("distress-done-$step")) {
                        Text(t("Done", "हो गया"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                    }
                }
            }
        }
        AnimatedVisibility(open == 6, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(other, { if (it.length <= DistressRules.OTHER_LIMIT) other = it }, label = { Text(t("Any other concern (optional)", "कोई और चिंता (चाहें तो)")) }, minLines = 2, maxLines = 4, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("distress-other"))
                OutlinedButton(onClick = { markDone(6); open = -1 }, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("distress-done-6")) {
                    Text(t("Done", "हो गया"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                }
            }
        }
        modelError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
        Button(onClick = {
            val check = DistressCheck(true, score, DistressRules.problems.filter { it in chosen }, other.trim())
            val change = DistressRules.change(today, check)
            when { change == null -> onClose(); !canEditCareContext(vm, person, capturedAccount) -> onClose(); today != null -> vm.updateItem(change, onClose); else -> vm.saveItem("checkin", change.title, change.detail, 0, onClose) }
        }, enabled = !busy && score in 0..10, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("distress-save")) {
            Text(t("Save", "सहेजें"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** What the "i" button on the thermometer says: the scale's ends and where the questions come from. */
private fun distressAbout(t: (String, String) -> String): String =
    t("0 = No distress, 10 = Extreme distress", "0 = कोई परेशानी नहीं, 10 = बहुत ज़्यादा परेशानी") + "\n\n" +
        t("Source: NCCN Distress Thermometer and Problem List, NCCN Guidelines Version 1.2026 (Distress Management).", "स्रोत: NCCN Distress Thermometer and Problem List, NCCN Guidelines संस्करण 1.2026। हिंदी अनुवाद Sahara का है, NCCN का नहीं।")

@Composable private fun DistressSource() {
    val t = careText()
    Text(t("Source: NCCN Distress Thermometer and Problem List, NCCN Guidelines Version 1.2026 (Distress Management).", "स्रोत: NCCN Distress Thermometer and Problem List, NCCN Guidelines संस्करण 1.2026। हिंदी अनुवाद Sahara का है, NCCN का नहीं।"), fontSize = 13.sp, lineHeight = 17.sp, color = DoctorCardFaint, modifier = Modifier.testTag("distress-source"))
}

/** Thermometer colours: green for 0–3, amber for 4–6, red for 7–10. */
/** The same three bands as every other chart in the app (ChartBands), so a 7 looks the same here and on the symptom graphs. */
private fun distressBand(n: Int): Color = ChartBands.color(n)
/** Darker versions of the same colours, for text on the cream background. */
private fun distressBandText(n: Int): Color = when { n <= 3 -> Color(0xFF1E6B3C); n <= 6 -> Color(0xFF8A5A00); else -> Color(0xFFB3261E) }
private fun distressOnBand(n: Int): Color = if (n in 4..6) Ink else Color.White

private fun distressGroupStyle(en: String): Pair<ImageVector, Color> = when (en) {
    "Physical Concerns" -> Icons.Filled.Healing to Color(0xFFC2453B)
    "Emotional Concerns" -> Icons.Filled.Favorite to Color(0xFFC2366E)
    "Social Concerns" -> Icons.Filled.People to Color(0xFF2B6CB0)
    "Practical Concerns" -> Icons.Filled.Home to Color(0xFF9A6212)
    else -> Icons.Filled.SelfImprovement to Color(0xFF6B4FA0)
}

/** Height of one number on the thermometer. Tapping or dragging along the tube picks the number. */
private val DistressRowHeight = 24.dp

/**
 * NCCN-style vertical thermometer, 10 at the top and 0 at the bottom, filled up to the chosen number in its band colour, with the
 * chosen number in the bulb. Each number is a radio button; a drag along the tube also picks.
 */
@Composable private fun DistressThermometer(score: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val t = careText()
    val pick by rememberUpdatedState(onPick)
    val picked = score in 0..10
    val empty = Color(0xFFEDE4D8)
    Column(modifier.testTag("distress-thermometer"), horizontalAlignment = Alignment.Start) {
        Text(t("Extreme distress", "बहुत ज़्यादा परेशानी"), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub, modifier = Modifier.padding(bottom = 4.dp))
        Column(Modifier.selectableGroup().pointerInput(Unit) {
            val row = DistressRowHeight.toPx()
            detectVerticalDragGestures { change, _ -> pick(10 - (change.position.y / row).toInt().coerceIn(0, 10)) }
        }) {
            (10 downTo 0).forEach { n ->
                val on = score == n
                val filled = picked && n in 1..score
                val anchor = when (n) { 0 -> t("No distress", "कोई परेशानी नहीं"); 10 -> t("Extreme distress", "बहुत ज़्यादा परेशानी"); else -> "" }
                val description = if (anchor.isEmpty()) "$n" else "$n, $anchor"
                Row(Modifier.fillMaxWidth().height(DistressRowHeight).selectable(selected = on, role = Role.RadioButton, onClick = { onPick(n) }).semantics { contentDescription = description }.testTag("distress-score-$n"), verticalAlignment = Alignment.CenterVertically) {
                    Text("$n", fontSize = if (on) 17.sp else 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) distressBandText(n) else DoctorCardSub, textAlign = TextAlign.End, modifier = Modifier.width(26.dp))
                    Spacer(Modifier.width(8.dp))
                    val shape = if (n == 10) RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp) else RoundedCornerShape(0.dp)
                    Box(Modifier.width(24.dp).fillMaxHeight().background(if (filled) distressBand(n) else empty, shape)) {
                        if (n in 1..9) Box(Modifier.align(Alignment.TopEnd).width(8.dp).height(1.dp).background(Paper))
                    }
                    if (on) Text("◀", fontSize = 13.sp, color = distressBandText(n), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        // The bulb, under the tube (its centre lines up with the tube's: 26 + 8 + 12 = 46dp from the left).
        Box(Modifier.padding(start = 24.dp).offset(y = (-4).dp).size(44.dp).background(if (picked) distressBand(score) else empty, CircleShape).testTag("distress-chosen"), contentAlignment = Alignment.Center) {
            Text(if (picked) "$score" else "–", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (picked) distressOnBand(score) else DoctorCardSub)
        }
        Text(t("No distress", "कोई परेशानी नहीं"), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
    }
}

/** One concern group beside the thermometer: icon, name and how many problems are ticked. Tapping opens its list below. */
@Composable private fun DistressGroupTile(title: String, count: Int, done: Boolean, open: Boolean, icon: ImageVector, colour: Color, tag: String, onToggle: () -> Unit) {
    val t = careText()
    val state = when { open -> t("Expanded", "खुला"); count > 0 -> t("$count selected", "$count चुने"); done -> t("None selected", "कुछ नहीं चुना"); else -> t("Collapsed", "बंद") }
    Surface(shape = RoundedCornerShape(14.dp), color = if (open) colour.copy(alpha = 0.14f) else Paper, border = BorderStroke(1.dp, if (open) colour else DoctorHairline), modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onToggle).semantics { stateDescription = state }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(28.dp).background(colour.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = colour, modifier = Modifier.size(17.dp)) }
            Text(title, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (count > 0) Box(Modifier.size(22.dp).background(colour, CircleShape), contentAlignment = Alignment.Center) { Text("$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            else Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = DoctorCardSub, modifier = Modifier.size(20.dp))
        }
    }
}

/** The tick-box rows of one NCCN problem group, one big row per problem (the accordion header carries the title). */
@Composable private fun DistressProblemGroup(category: DistressRules.Category, colour: Color, chosen: Set<String>, onToggle: (String) -> Unit) {
    val hindi = careHindi()
    Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Column {
            category.items.forEachIndexed { index, (id, _) ->
                val on = id in chosen
                if (index > 0) HorizontalDivider(color = DoctorHairline)
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(if (on) colour.copy(alpha = 0.10f) else Color.Transparent)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(id) }).padding(horizontal = 8.dp).testTag("distress-problem-$id"), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colour, uncheckedColor = DoctorCardSub), modifier = Modifier.padding(8.dp))
                    Text(DistressRules.problemLabel(id, hindi), fontSize = 17.sp, lineHeight = 22.sp, color = DoctorCardInk, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }
    }
}

/** A saved distress answer in the Check-ins list. */
@Composable fun DistressEntryCard(item: CareItem) {
    val t = careText(); val hindi = careHindi()
    val check = DistressCodec.decode(item.detail)
    SurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (check != null) DistressScoreChip(check)
                Text(check?.let { DistressRules.shownTitle(it, hindi) } ?: t("Couldn't read this note.", "यह नोट पढ़ा नहीं जा सका।"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.weight(1f))
            }
            check?.let { DistressRules.summary(it, hindi) }?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardInk) }
            Text(date(item.created), fontSize = 14.sp, color = DoctorCardSub)
        }
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
            // Every answer from the first one, oldest to newest, as one connected line on a date axis; a "no distress" day is plotted as 0.
            val series = remember(history) { DistressRules.series(history) }
            val pts = series.map { (at, v, said) -> ChartPoint(at.toDouble(), v.toDouble(), if (said) distressBand(v) else Sage) }
            val summary = history.reversed().joinToString("; ") { (item, check) -> Instant.ofEpochMilli(item.created).atZone(ZoneId.systemDefault()).format(day) + ": " + (if (check.distress) "${check.score ?: 0}" else t("No", "नहीं")) }
            CareTimeLineChart(points = pts, yMin = 0.0, yMax = 10.0, yTicks = listOf(0.0, 5.0, 10.0), yText = { it.toInt().toString() },
                description = summary, latestText = { it.toInt().toString() }, tag = "distress-history", height = 160.dp,
                bands = listOf(ChartBand(0.0, 3.5, Sage, 0.10f), ChartBand(3.5, 6.5, distressBand(5), 0.10f), ChartBand(6.5, 10.0, distressBand(8), 0.10f)),
                pointText = { it.toInt().toString() })
            // The latest answers as one list with dividers: score chip, date, then any ticked problems.
            history.take(5).forEachIndexed { i, (item, check) ->
                if (i > 0) HorizontalDivider(color = DoctorHairline)
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DistressScoreChip(check)
                    Column(Modifier.weight(1f)) {
                        Text(Instant.ofEpochMilli(item.created).atZone(ZoneId.systemDefault()).format(day) + " · " + DistressRules.shownTitle(check, hindi), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                        DistressRules.summary(check, hindi).takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 14.sp, lineHeight = 19.sp, color = DoctorCardSub) }
                    }
                }
            }
            DistressSource()
        }
    }
}

/** A round score badge in its band colour; a "No" answer shows a calm sage 0. Decorative: the row text carries the words. */
@Composable private fun DistressScoreChip(check: DistressCheck) {
    val n = if (check.distress) check.score ?: 0 else 0
    Box(Modifier.size(36.dp).background(if (check.distress) distressBand(n) else Sage, CircleShape).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Text("$n", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (check.distress) distressOnBand(n) else Ink)
    }
}
