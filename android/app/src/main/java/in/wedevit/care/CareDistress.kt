@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    LaunchedEffect(Unit) { vm.actionError.value = null }
    BackHandler { onClose() }
    Column(Modifier.fillMaxSize().background(Cream).safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).testTag("distress-screen"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("distress-close")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Close", "बंद करें"), tint = Ink) }
            Box(Modifier.weight(1f)) { Heading(t("Distress Thermometer", "परेशानी थर्मामीटर"), 26) }
            CareLanguageMenu(compact = true)
        }
        Text(t("How much distress have you felt in the past week, including today? Tap a number.", "पिछले एक हफ़्ते में, आज समेत, आपको कितनी परेशानी रही है? एक अंक दबाएँ।"), fontSize = 17.sp, lineHeight = 24.sp, color = DoctorCardInk)
        DistressScoreBadge(score)
        DistressThermometer(score) { score = it }
        Text(t("Tick anything that has been a problem.", "जो भी बात परेशानी रही हो, उस पर निशान लगाएँ।"), fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.padding(top = 8.dp))
        Text(t("This part is optional.", "यह हिस्सा भरना ज़रूरी नहीं है।"), fontSize = 14.sp, color = DoctorCardSub)
        DistressRules.categories.forEach { category ->
            DistressProblemGroup(category, chosen) { id -> ticked = (if (id in chosen) chosen - id else chosen + id).joinToString("\n") }
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

/** Thermometer colours: green for 0–3, amber for 4–6, red for 7–10. */
private fun distressBand(n: Int): Color = when { n <= 3 -> Color(0xFF2E8B57); n <= 6 -> Color(0xFFE8A317); else -> Color(0xFFC62828) }
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

/** The chosen number, shown large above the thermometer. */
@Composable private fun DistressScoreBadge(score: Int) {
    val t = careText()
    val picked = score in 0..10
    val band = if (picked) distressBand(score) else DoctorHairline
    Surface(shape = RoundedCornerShape(20.dp), color = if (picked) band.copy(alpha = 0.14f) else Paper, border = BorderStroke(1.dp, band), modifier = Modifier.fillMaxWidth().testTag("distress-chosen")) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(72.dp).background(band, CircleShape), contentAlignment = Alignment.Center) {
                Text(if (picked) "$score" else "–", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = if (picked) distressOnBand(score) else DoctorCardSub)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (picked) t("Your number: $score out of 10", "आपका अंक: 10 में से $score") else t("No number chosen yet", "अभी कोई अंक नहीं चुना"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                Text(t("0 = No distress, 10 = Extreme distress", "0 = कोई परेशानी नहीं, 10 = बहुत ज़्यादा परेशानी"), fontSize = 14.sp, lineHeight = 19.sp, color = DoctorCardSub)
            }
        }
    }
}

/** A drawn thermometer, 10 at the top and 0 above the bulb. Each number is a full-width 48dp row that works as a radio button. */
@Composable private fun DistressThermometer(score: Int, onPick: (Int) -> Unit) {
    val t = careText()
    val rowH = 48.dp; val labelW = 48.dp; val gap = 16.dp; val tubeW = 32.dp; val bulbR = 32.dp
    val outline = DoctorCardSub
    Box(Modifier.fillMaxWidth().height(rowH * 11 + bulbR * 2 + 4.dp).testTag("distress-thermometer")) {
        Canvas(Modifier.matchParentSize()) {
            val row = rowH.toPx(); val cx = (labelW + gap + bulbR).toPx(); val half = tubeW.toPx() / 2; val r = bulbR.toPx()
            val wall = 3.dp.toPx(); val pad = 7.dp.toPx(); val top = 4.dp.toPx(); val bulbY = row * 11 + r + 2.dp.toPx()
            fun centre(n: Int) = (10 - n) * row + row / 2
            // Outline first, then the inside, so the tube and the bulb join without a seam.
            drawRoundRect(outline, Offset(cx - half, top), Size(half * 2, bulbY - top), CornerRadius(half, half))
            drawCircle(outline, r, Offset(cx, bulbY))
            drawRoundRect(Paper, Offset(cx - half + wall, top + wall), Size((half - wall) * 2, bulbY - top - wall), CornerRadius(half - wall, half - wall))
            drawCircle(Paper, r - wall, Offset(cx, bulbY))
            // Faint green, amber and red inside the empty tube.
            for (n in 0..10) {
                val from = maxOf(centre(n) - row / 2, top + half); val to = centre(n) + row / 2
                drawRect(distressBand(n).copy(alpha = 0.13f), Offset(cx - half + wall, from), Size((half - wall) * 2, to - from))
            }
            if (score in 0..10) {
                val fill = distressBand(score); val inner = half - pad; val fillTop = centre(score) - 6.dp.toPx()
                drawRoundRect(fill, Offset(cx - inner, fillTop), Size(inner * 2, bulbY - fillTop), CornerRadius(inner, inner))
                drawCircle(fill, r - pad, Offset(cx, bulbY))
            }
            for (n in 0..10) {
                val y = centre(n); val length = if (n % 5 == 0) 14.dp.toPx() else 9.dp.toPx()
                drawLine(outline, Offset(cx - half - length, y), Offset(cx - half - 3.dp.toPx(), y), strokeWidth = 2.dp.toPx())
            }
        }
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            (10 downTo 0).forEach { n ->
                val on = score == n
                val anchor = when (n) { 0 -> t("No distress", "कोई परेशानी नहीं"); 10 -> t("Extreme distress", "बहुत ज़्यादा परेशानी"); else -> "" }
                val description = if (anchor.isEmpty()) "$n" else "$n, $anchor"
                Row(Modifier.fillMaxWidth().height(rowH).selectable(selected = on, role = Role.RadioButton, onClick = { onPick(n) }).semantics { contentDescription = description }.testTag("distress-score-$n"), verticalAlignment = Alignment.CenterVertically) {
                    Text("$n", modifier = Modifier.width(labelW), textAlign = TextAlign.End, fontSize = if (on) 26.sp else 19.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) distressBandText(n) else DoctorCardInk)
                    Spacer(Modifier.width(gap + bulbR * 2 + 12.dp))
                    if (on) Surface(shape = RoundedCornerShape(50), color = distressBand(n)) {
                        Text(if (anchor.isEmpty()) t("Your number", "आपका अंक") else anchor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = distressOnBand(n), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    } else if (anchor.isNotEmpty()) Text(anchor, fontSize = 15.sp, color = DoctorCardSub)
                }
            }
        }
    }
}

/** One NCCN problem group: a coloured icon, the group name, how many are ticked, then one big tick-box row per problem. */
@Composable private fun DistressProblemGroup(category: DistressRules.Category, chosen: Set<String>, onToggle: (String) -> Unit) {
    val t = careText(); val hindi = careHindi()
    val (icon, colour) = distressGroupStyle(category.en)
    val count = category.items.count { it.first in chosen }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).background(colour.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = colour, modifier = Modifier.size(22.dp)) }
            Text(t(category.en, category.hi), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading())
            if (count > 0) Surface(shape = RoundedCornerShape(50), color = colour) {
                Text(t("$count ticked", "$count चुने"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
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
                        Box(Modifier.fillMaxWidth().height((4 + value * 6).dp).background(if (check.distress) distressBand(value) else Sage, RoundedCornerShape(4.dp)))
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
