@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FamilyRestroom
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One daily distress answer. [distress] false is the one-tap "No"; true carries the 0–10 score and any ticked problems. */
internal data class DistressCheck(val distress: Boolean, val score: Int? = null, val problems: List<String> = emptyList(), val other: String = "",
    /** Picture groups a patient tapped on the causes step ([SymptomScaleRules.DistressGroup] keys); empty on older answers. */
    val groups: List<String> = emptyList())

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
        !check.distress && (check.score != null || check.problems.isNotEmpty() || check.other.isNotBlank() || check.groups.isNotEmpty()) -> "A \"No\" answer has no score or problems."
        check.distress && (check.score == null || check.score !in 0..10) -> "Choose a number from 0 to 10."
        check.problems.any { it !in problems } || check.problems.distinct().size != check.problems.size -> "Choose items from the problem list."
        check.groups.any { it !in SymptomScaleRules.distressGroupKeys } || check.groups.distinct().size != check.groups.size -> "Choose items from the problem list."
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

    /** Same cut-off and message as the family member's own check ([CareFamilyRules.TALK_FROM]): a saved score of 4 or more. */
    fun tellCareTeam(check: DistressCheck?): Boolean = check?.distress == true && (check.score ?: 0) >= CareFamilyRules.TALK_FROM

    /**
     * The ticked problems, then any picture group the patient tapped without ticking a problem in it ("Money and practical"),
     * then the other concern. Clinicians read the same line, so a picture-only answer still says where the distress comes from.
     */
    fun summary(check: DistressCheck, hindi: Boolean): String {
        val covered = check.problems.mapNotNull { SymptomScaleRules.groupOfProblem(it)?.key }.toSet()
        val groupsOnly = SymptomScaleRules.distressGroupKeys.filter { it in check.groups && it !in covered }.map { SymptomScaleRules.distressGroupLabel(it, hindi) }
        return (check.problems.map { problemLabel(it, hindi) } + groupsOnly + listOfNotNull(check.other.trim().takeIf { it.isNotEmpty() })).joinToString(", ")
    }
}

/** Stored in a check-in's detail, the same way symptom notes are, so it syncs through shared care with no new server table. */
internal object DistressCodec {
    const val PREFIX = "sahara:distress:v1\n"
    fun isStructured(text: String) = text.startsWith(PREFIX)
    fun encode(check: DistressCheck): String {
        require(DistressRules.error(check) == null) { DistressRules.error(check)!! }
        val ordered = DistressRules.problems.filter { it in check.problems }
        return PREFIX + JSONObject().put("distress", check.distress).put("score", check.score ?: JSONObject.NULL)
            .put("problems", JSONArray(ordered)).put("other", check.other.trim()).put("source", DistressRules.VERSION)
            // Only written when a patient tapped a picture group, so older answers stay byte-for-byte the same.
            .also { j -> SymptomScaleRules.distressGroupKeys.filter { it in check.groups }.takeIf { it.isNotEmpty() }?.let { j.put("groups", JSONArray(it)) } }.toString()
    }
    fun decode(text: String): DistressCheck? {
        if (!isStructured(text)) return null
        return runCatching {
            val j = JSONObject(text.removePrefix(PREFIX)); val list = j.getJSONArray("problems")
            val groups = j.optJSONArray("groups")
            DistressCheck(j.getBoolean("distress"), if (j.isNull("score")) null else j.getInt("score"), (0 until list.length()).map { list.getString(it) }, j.optString("other"),
                groups?.let { g -> (0 until g.length()).map { g.getString(it) } }.orEmpty())
                .also { require(DistressRules.error(it) == null) }
        }.getOrNull()
    }
}

/** The patient's name when a family member or carer is answering for them, else null (the patient answers about themselves). */
@Composable internal fun distressAboutName(vm: CareModel, personId: String): String? {
    val role by vm.viewRole.collectAsStateWithLifecycle(); val people by vm.people.collectAsStateWithLifecycle(); val t = careText()
    return if (role == "Family") people.firstOrNull { it.id == personId }?.name?.trim().orEmpty().ifBlank { t("the patient", "मरीज़") } else null
}

/** "Are you in any distress today?" under the mood buttons: one tap saves "No"; "Yes" opens the thermometer screen. */
@Composable fun DistressQuestion(vm: CareModel, onYes: () -> Unit, compact: Boolean = false) {
    val t = careText(); val hindi = careHindi()
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val dayStart = remember(LocalDate.now()) { LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val today = remember(items, person, dayStart) { DistressRules.today(items, person, dayStart) }
    val answer = remember(today) { today?.let { DistressCodec.decode(it.detail) } }
    val capturedAccount = remember(person) { vm.accountNamespace }
    val tick = rememberSaharaTick()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("distress-question")) {
        val about = distressAboutName(vm, person)
        if (compact) {
            val yesSel = answer?.distress == true; val noSel = answer?.distress == false
            val green = androidx.compose.ui.graphics.Color(0xFFCFE8D8); val red = androidx.compose.ui.graphics.Color(0xFFF3C9C4)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (about != null) t("Any distress for $about today?", "क्या आज $about किसी परेशानी में हैं?") else t("Any distress today?", "आज कोई परेशानी?"), Modifier.weight(1f), fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                Row(Modifier.clip(RoundedCornerShape(50)).background(androidx.compose.ui.graphics.Color.White).border(1.5.dp, androidx.compose.ui.graphics.Color(0xFFCDBBAE), RoundedCornerShape(50)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Surface(onClick = { tick(); onYes() }, enabled = !busy, shape = RoundedCornerShape(50), color = if (yesSel) red else androidx.compose.ui.graphics.Color.Transparent, contentColor = if (yesSel) androidx.compose.ui.graphics.Color(0xFF8E2A22) else DoctorCardSub, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp).testTag("distress-yes")) {
                        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(t("Yes", "हाँ") + (answer?.takeIf { it.distress }?.score?.let { " · $it/10" } ?: ""), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    }
                    Surface(onClick = {
                        tick()
                        val change = DistressRules.change(today, DistressCheck(false))
                        if (change != null && canEditCareContext(vm, person, capturedAccount)) { if (today != null) vm.updateItem(change) {} else vm.saveItem("checkin", change.title, change.detail) }
                    }, enabled = !busy, shape = RoundedCornerShape(50), color = if (noSel) green else androidx.compose.ui.graphics.Color.Transparent, contentColor = if (noSel) androidx.compose.ui.graphics.Color(0xFF1F5E3D) else DoctorCardSub, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp).testTag("distress-no")) {
                        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(t("No", "नहीं"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    }
                }
            }
        } else {
        Text(if (about != null) t("Is $about in any distress today?", "क्या आज $about किसी परेशानी में हैं?") else t("Are you in any distress today?", "क्या आज आप किसी परेशानी में हैं?"), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // A saved score shows on the Yes chip itself ("Yes · 7/10"); tapping it again opens the thermometer to change it.
            FilterChip(selected = answer?.distress == true, enabled = !busy, onClick = { tick(); onYes() }, label = { Text(t("Yes", "हाँ") + (answer?.takeIf { it.distress }?.score?.let { " · $it/10" } ?: ""), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("distress-yes"))
            FilterChip(selected = answer?.distress == false, enabled = !busy, onClick = {
                tick()
                val change = DistressRules.change(today, DistressCheck(false))
                if (change != null && canEditCareContext(vm, person, capturedAccount)) { if (today != null) vm.updateItem(change) {} else vm.saveItem("checkin", change.title, change.detail) }
            }, label = { Text(t("No", "नहीं"), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("distress-no"))
        }
        }
        AnimatedVisibility(DistressRules.tellCareTeam(answer), enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
            Text(t("Tell your care team how you are.", "देखभाल टीम को अपना हाल बताएँ।"), fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardInk, modifier = Modifier.testTag("distress-tell-team"))
        }
    }
}

/**
 * Two screens in sequence (clinical team, 24 Nov 2026): first the 0–10 thermometer (the only required answer), then Next to what is
 * adding to it (the NCCN problem list as tick boxes, and "Any other concern"), then Save. Back on the second screen returns to the first.
 * The saved answer is the same one check-in as before ([DistressCodec]).
 */
@Composable fun DistressScreen(vm: CareModel, onClose: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val modelError by vm.actionError.collectAsStateWithLifecycle()
    val dayStart = remember { LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    // Read live, not remembered: if the screen opened before items loaded, Save must still update today's answer instead of adding a second one.
    val today = DistressRules.today(items, person, dayStart)
    val previous = remember(today) { today?.let { DistressCodec.decode(it.detail) }?.takeIf { it.distress } }
    var score by rememberSaveable { mutableStateOf(previous?.score ?: -1) }
    var ticked by rememberSaveable { mutableStateOf(previous?.problems.orEmpty().joinToString("\n")) }
    var other by rememberSaveable { mutableStateOf(previous?.other.orEmpty()) }
    // Picture groups a patient tapped (body, feelings, family, money and practical, faith), saved beside the problem list.
    var groupKeys by rememberSaveable { mutableStateOf(previous?.groups.orEmpty().joinToString("\n")) }
    val groups = remember(groupKeys) { groupKeys.split("\n").filter { it.isNotEmpty() }.toSet() }
    // Patients see the thermometer as a large picture and the causes as five picture tiles; family and the care team keep the full NCCN list.
    val role by vm.viewRole.collectAsStateWithLifecycle()
    val pictures = SymptomScaleRules.picturesByDefault(role)
    val capturedAccount = remember(person) { vm.accountNamespace }
    val chosen = remember(ticked) { ticked.split("\n").filter { it.isNotEmpty() }.toSet() }
    // 1 = the thermometer, 2 = what is adding to it.
    var step by rememberSaveable { mutableStateOf(1) }
    val saved = rememberSaharaHaptic()
    LaunchedEffect(Unit) { vm.actionError.value = null }
    // Closing the check-in changes the care overlay, so this one previews that host; step 2 back to 1 is handled inside the step host below.
    SaharaBackHandler(step == 1) { onClose() }
    fun save() {
        val check = DistressCheck(true, score, DistressRules.problems.filter { it in chosen }, other.trim(), SymptomScaleRules.distressGroupKeys.filter { it in groups })
        val change = DistressRules.change(today, check)
        // A light confirming buzz only when an answer is really written (no celebration on this screen).
        val done = { saved(); onClose() }
        when { change == null -> onClose(); !canEditCareContext(vm, person, capturedAccount) -> onClose(); today != null -> vm.updateItem(change, done); else -> vm.saveItem("checkin", change.title, change.detail, 0, done) }
    }
    SaharaScreenHost(step, Modifier.fillMaxSize().background(Cream).testTag("distress-screen"), label = "distress-step",
        direction = { from, to -> if (to < from) SaharaMotion.Direction.Back else SaharaMotion.Direction.Forward }) { shown ->
        SaharaBackHandler(shown == 2) { step = 1 }
        if (shown == 1) DistressScoreStep(vm, person, score, pictures, onScore = { score = it }, onClose = onClose, onNext = { step = 2 })
        else if (pictures) DistressPictureCausesStep(score, groups, chosen, other, busy, modelError, onBack = { step = 1 },
            // Read the live state, not the remembered sets: unmarking a picture untoggles several problems in one tap.
            onGroup = { key, on -> val now = groupKeys.split("\n").filter { it.isNotEmpty() }.toSet(); groupKeys = (if (on) now + key else now - key).joinToString("\n") },
            onToggle = { id -> val now = ticked.split("\n").filter { it.isNotEmpty() }.toSet(); ticked = (if (id in now) now - id else now + id).joinToString("\n") }, onOther = { other = it }, onSave = ::save)
        else DistressCausesStep(score, chosen, other, busy, modelError, onBack = { step = 1 },
            onToggle = { id -> ticked = (if (id in chosen) chosen - id else chosen + id).joinToString("\n") }, onOther = { other = it }, onSave = ::save)
    }
}

/** The title row both screens share: back arrow, title, the "i" button and the language menu. */
@Composable private fun DistressHeader(onBack: () -> Unit, backLabel: String, backTag: String, title: String? = null) {
    val t = careText()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag(backTag)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, backLabel, tint = Ink) }
        Box(Modifier.weight(1f).testTag("$backTag-title")) { Heading(title ?: t("Distress Thermometer", "परेशानी थर्मामीटर"), 24) }
        CareChartInfoButton(t("About this", "इसके बारे में"), distressAbout(t), "distress")
        CareLanguageMenu(compact = true)
    }
}

/** Screen 1: the question, the thermometer with the chosen number large beside it, then Next. */
@Composable private fun DistressScoreStep(vm: CareModel, person: String, score: Int, pictures: Boolean, onScore: (Int) -> Unit, onClose: () -> Unit, onNext: () -> Unit) {
    val t = careText()
    if (pictures) { DistressPictureScoreStep(score, onScore, onClose, onNext); return }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 16.dp).testTag("distress-step-score"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DistressHeader(onClose, t("Close", "बंद करें"), "distress-close")
        val about = distressAboutName(vm, person)
        Text(if (about != null) t("How much distress has $about felt in the past week, including today?", "पिछले एक हफ़्ते में, आज समेत, $about को कितनी परेशानी रही है?")
            else t("How much distress have you felt in the past week, including today?", "पिछले एक हफ़्ते में, आज समेत, आपको कितनी परेशानी रही है?"), fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardInk)
        // Distress vs symptoms (SVK and Sujay, 23 Nov): this is one overall number; each symptom keeps its own 0–10 in the symptom notes.
        SaharaInfoToggle(t("About the distress number", "परेशानी के अंक के बारे में"), "distress-info", label = t("How this differs from symptoms", "यह लक्षणों से कैसे अलग है")) { Text(t("This is one number for overall distress. Each symptom (pain, sleep and others) has its own 0–10 score in Symptoms.", "यह कुल परेशानी का एक अंक है। हर लक्षण (दर्द, नींद आदि) का अपना 0–10 अंक लक्षणों में अलग से दिया जाता है।"),
            fontSize = 15.sp, lineHeight = 22.sp, color = SaharaColors.Ink, modifier = Modifier.testTag("distress-overall-explainer")) }
        // The chosen number, large, with a plain instruction: nobody should have to guess that the thermometer slides.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            DistressThermometer(score, Modifier.width(112.dp), onPick = onScore)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    // The big number rolls to each new value instead of jumping; the column no longer resizes itself.
                    SaharaAnimatedText(if (score in 0..10) "$score" else "–") { shown ->
                        val n = shown.toIntOrNull()
                        Text(shown, fontFamily = SaharaTitleFont, fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Bold, color = if (n != null) distressBandText(n) else DoctorCardSub, modifier = Modifier.testTag("distress-big-number"))
                    }
                    Text("/10", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardSub, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                }
                Text(if (score in 0..10) t("Slide to change", "बदलने के लिए सरकाएँ") else t("Slide to rate", "अंक देने के लिए सरकाएँ"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorAccent, maxLines = 1, softWrap = false, modifier = Modifier.testTag("distress-slide-hint"))
            }
        }
        // The doctor's plain line, right under the thermometer (24 Nov).
        Text(t("This is your overall distress, not one problem.", "यह आपकी कुल परेशानी है, कोई एक समस्या नहीं।"), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
            color = DoctorCardInk, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag("distress-overall-line"))
        DistressSource()
        Button(onClick = onNext, enabled = score in 0..10, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("distress-next")) {
            Text(t("Next", "आगे"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Screen 2: what is adding to it, one group at a time (its list opens right under it), any other concern, then Save. */
@Composable private fun DistressCausesStep(score: Int, chosen: Set<String>, other: String, busy: Boolean, modelError: String?, onBack: () -> Unit, onToggle: (String) -> Unit, onOther: (String) -> Unit, onSave: () -> Unit) {
    val t = careText()
    // Which group is open (-1 none; 1..5 the NCCN groups, 6 "Any other concern"). Nothing opens by itself: only a tap opens a group.
    var open by rememberSaveable { mutableStateOf(-1) }
    var doneSteps by rememberSaveable { mutableStateOf("") }
    val finished = doneSteps.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    fun markDone(step: Int) { doneSteps = (finished + step).joinToString(",") }
    // Moving on counts as done: closing a group, or opening the next one, marks the one that was open; no "Done" tap needed.
    fun toggle(step: Int) { if (open > 0) markDone(open); open = if (open == step) -1 else step }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 16.dp).testTag("distress-step-causes"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Step 2 has its own title (clinical team, 24 Nov): the thermometer was screen 1, this screen is only the causes.
        DistressHeader(onBack, t("Back", "वापस"), "distress-back", t("What is causing your distress?", "आपकी परेशानी की वजह क्या है?"))
        // The score from the first screen, small, so it is clear what these causes belong to.
        if (score in 0..10) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("distress-causes-score")) {
            Box(Modifier.size(36.dp).background(distressBand(score), CircleShape), contentAlignment = Alignment.Center) { Text("$score", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = distressOnBand(score)) }
            val s = score; Text(t("Distress $s/10", "परेशानी $s/10"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
        }
        Text(t("Tick any that apply (no scores here)", "जो लागू हो चुनें (यहाँ अंक नहीं)"), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.testTag("distress-problems-hint"))
        DistressRules.categories.forEachIndexed { index, category ->
            val step = index + 1
            val (icon, colour) = distressGroupStyle(category.en)
            DistressGroupTile(t(category.en, category.hi), category.items.count { it.first in chosen }, step in finished, open == step, icon, colour, "distress-step-$step") { toggle(step) }
            AnimatedVisibility(open == step, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DistressProblemGroup(category, colour, chosen, onToggle)
                    OutlinedButton(onClick = { markDone(step); open = -1 }, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("distress-done-$step")) {
                        Text(t("Done", "हो गया"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                    }
                }
            }
        }
        DistressGroupTile(t("Any other concern", "कोई और चिंता"), if (other.isNotBlank()) 1 else 0, 6 in finished, open == 6, Icons.Filled.Edit, DoctorCardSub, "distress-step-other") { toggle(6) }
        AnimatedVisibility(open == 6, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(other, { if (it.length <= DistressRules.OTHER_LIMIT) onOther(it) }, label = { Text(t("Any other concern (optional)", "कोई और चिंता (चाहें तो)")) }, minLines = 2, maxLines = 4, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("distress-other"))
                OutlinedButton(onClick = { markDone(6); open = -1 }, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("distress-done-6")) {
                    Text(t("Done", "हो गया"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                }
            }
        }
        // The error slides in and out in place; the last message stays readable while it leaves.
        var lastError by remember { mutableStateOf("") }
        LaunchedEffect(modelError) { if (modelError != null) lastError = modelError }
        AnimatedVisibility(modelError != null, enter = SaharaMotion.enter, exit = SaharaMotion.exit) {
            Text(modelError ?: lastError,color = MaterialTheme.colorScheme.error, modifier = Modifier.announce())
        }
        Button(onClick = onSave, enabled = !busy && score in 0..10, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("distress-save")) {
            Text(t("Save", "सहेजें"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** What the "i" button on the thermometer says: the scale's ends and where the questions come from. */
private fun distressAbout(t: (String, String) -> String): String =
    t("0 = No distress, 10 = Extreme distress", "0 = कोई परेशानी नहीं, 10 = बहुत ज़्यादा परेशानी") + "\n\n" +
        t("The thermometer is one overall distress score. The problem list (physical, emotional, social, practical, spiritual) only shows what is adding to it; it is not scored. Each symptom has its own separate 0–10 score in Symptoms.",
            "थर्मामीटर कुल परेशानी का एक अंक है। समस्याओं की सूची (शारीरिक, भावनात्मक, सामाजिक, व्यावहारिक, आध्यात्मिक) बस बताती है कि परेशानी किस वजह से है; इसका अंक नहीं होता। हर लक्षण का अपना अलग 0–10 अंक लक्षणों में होता है।") + "\n\n" +
        t("Source: NCCN Distress Thermometer and Problem List, NCCN Guidelines Version 1.2026 (Distress Management).", "स्रोत: NCCN Distress Thermometer and Problem List, NCCN Guidelines संस्करण 1.2026। हिंदी अनुवाद Sahara का है, NCCN का नहीं।")

@Composable private fun DistressSource() {
    val t = careText()
    SaharaInfoToggle(t("Where this thermometer comes from", "यह थर्मामीटर कहाँ से है"), "distress-source-info", label = t("Source", "स्रोत")) {
        Text(t("Source: NCCN Distress Thermometer and Problem List, NCCN Guidelines Version 1.2026 (Distress Management).", "स्रोत: NCCN Distress Thermometer and Problem List, NCCN Guidelines संस्करण 1.2026। हिंदी अनुवाद Sahara का है, NCCN का नहीं।"), fontSize = 13.sp, lineHeight = 18.sp, color = SaharaColors.InkSub, modifier = Modifier.testTag("distress-source"))
    }
}

/** Thermometer colours: green for 0–3, amber for 4–6, red for 7–10. */
/** The same three bands as every other chart in the app (ChartBands), so a 7 looks the same here and on the symptom graphs. */
private fun distressBand(n: Int): Color = ChartBands.color(n)
/** Darker versions of the same colours, for text on the cream background. */
private fun distressBandText(n: Int): Color = when { n <= 3 -> Color(0xFF1E6B3C); n <= 6 -> Color(0xFF8A5A00); else -> Color(0xFFB3261E) }
/** The number in the bulb is white on every colour (team feedback 21 Nov). */
@Suppress("UNUSED_PARAMETER") private fun distressOnBand(n: Int): Color = Color.White

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
@Composable private fun DistressThermometer(score: Int, modifier: Modifier = Modifier, rowHeight: androidx.compose.ui.unit.Dp = DistressRowHeight, tube: androidx.compose.ui.unit.Dp = 24.dp, onPick: (Int) -> Unit) {
    val t = careText()
    // The phone's light key tap each time the chosen number changes (tap or drag), like a system picker.
    val tick = rememberSaharaTick()
    // A drag reports the same row many times before the new score recomposes in, so the last ticked number is kept as well.
    val lastTicked = remember { intArrayOf(Int.MIN_VALUE) }
    val choose: (Int) -> Unit = { n -> if (n != score && n != lastTicked[0]) { lastTicked[0] = n; tick() }; onPick(n) }
    val pick by rememberUpdatedState(choose)
    val picked = score in 0..10
    // The patient picture is larger: bigger numbers and words, and a wider number column so "10" never clips.
    val large = rowHeight > DistressRowHeight
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val numberWidth = if (large) (36f * fontScale).dp else 26.dp
    val empty = Color(0xFFEDE4D8)
    // A gentle one-time nudge on first show: a ghost fill rises and falls so people see the tube moves.
    val hint = remember { androidx.compose.animation.core.Animatable(0f) }
    var nudged by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!nudged && score !in 0..10) {
            hint.animateTo(6f, androidx.compose.animation.core.tween(800)); hint.animateTo(0f, androidx.compose.animation.core.tween(800))
        }
        nudged = true
    }
    // The fill rises (or falls) smoothly to the chosen number instead of jumping; instant when motion is off. Level n+1 = rows 0..n full.
    // Kept as State and read only while drawing, so the rising fill redraws the tube without recomposing every row each frame.
    val level = androidx.compose.animation.core.animateFloatAsState(if (picked) score + 1f else 0f, SaharaMotion.tween(SaharaMotion.FILL_MS, easing = SaharaMotion.Emphasized), label = "distress-level")
    val fillColour by androidx.compose.animation.animateColorAsState(if (picked) distressBand(score) else empty, SaharaMotion.tween(SaharaMotion.VALUE_MS), label = "distress-colour")
    Column(modifier.testTag("distress-thermometer"), horizontalAlignment = Alignment.Start) {
        Text(t("Extreme distress", "बहुत ज़्यादा परेशानी"), fontSize = if (large) 18.sp else 14.sp, lineHeight = if (large) 22.sp else 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub, modifier = Modifier.padding(bottom = 4.dp))
        // Keyed on the row height so a font-size change while the screen is open keeps the drag mapping in step with the rows.
        Column(Modifier.selectableGroup().pointerInput(rowHeight) {
            val row = rowHeight.toPx()
            detectVerticalDragGestures { change, _ -> pick(10 - (change.position.y / row).toInt().coerceIn(0, 10)) }
        }) {
            (10 downTo 0).forEach { n ->
                val on = score == n
                // The whole fill takes the chosen level's colour (one colour, not green-then-amber), and starts at 0 so it meets the bulb.
                val ghost = !picked && n in 0..hint.value.toInt()
                val anchor = when (n) { 0 -> t("No distress", "कोई परेशानी नहीं"); 10 -> t("Extreme distress", "बहुत ज़्यादा परेशानी"); else -> "" }
                val description = if (anchor.isEmpty()) "$n" else "$n, $anchor"
                Row(Modifier.fillMaxWidth().height(rowHeight).selectable(selected = on, role = Role.RadioButton, onClick = { pick(n) }).semantics { contentDescription = description }.testTag("distress-score-$n"), verticalAlignment = Alignment.CenterVertically) {
                    Text("$n", fontSize = if (large) (if (on) 22.sp else 18.sp) else if (on) 17.sp else 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) distressBandText(n) else DoctorCardSub, textAlign = TextAlign.End, modifier = Modifier.width(numberWidth))
                    Spacer(Modifier.width(8.dp))
                    val shape = if (n == 10) RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp) else RoundedCornerShape(0.dp)
                    Box(Modifier.width(tube).fillMaxHeight().clip(shape).background(if (ghost) distressBand(hint.value.toInt()).copy(alpha = .45f) else empty).drawBehind {
                        val part = (level.value - n).coerceIn(0f, 1f)
                        if (part > 0f) drawRect(fillColour, topLeft = Offset(0f, size.height * (1f - part)), size = Size(size.width, size.height * part))
                    }) {
                        if (n in 1..9) Box(Modifier.align(Alignment.TopEnd).width(8.dp).height(1.dp).background(Paper))
                    }
                    if (on) Text("◀", fontSize = 13.sp, color = distressBandText(n), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        // The bulb, under the tube (its centre lines up with the tube's: 26 + 8 + half the tube from the left; 44dp wide).
        Box(Modifier.padding(start = numberWidth + 8.dp + tube / 2 - 22.dp).offset(y = (-10).dp).saharaPop(score.takeIf { picked }).size(44.dp).background(fillColour, CircleShape).testTag("distress-chosen"), contentAlignment = Alignment.Center) {
            Text(if (picked) "$score" else "–", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (picked) distressOnBand(score) else DoctorCardSub)
        }
        Text(t("No distress", "कोई परेशानी नहीं"), fontSize = if (large) 18.sp else 14.sp, lineHeight = if (large) 22.sp else 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
    }
}

/** One concern group beside the thermometer: icon, name and how many problems are ticked. Tapping opens its list below. */
@Composable private fun DistressGroupTile(title: String, count: Int, done: Boolean, open: Boolean, icon: ImageVector, colour: Color, tag: String, onToggle: () -> Unit) {
    val t = careText()
    val state = when { open -> t("Expanded", "खुला"); count > 0 -> t("$count selected", "$count चुने"); done -> t("None selected", "कुछ नहीं चुना"); else -> t("Collapsed", "बंद") }
    val fill by androidx.compose.animation.animateColorAsState(if (open) colour.copy(alpha = 0.14f) else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "distress-group")
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val tick = rememberSaharaTick()
    val edge by androidx.compose.animation.animateColorAsState(if (open) colour else DoctorHairline, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "distress-group-edge")
    Surface(shape = RoundedCornerShape(14.dp), color = fill, border = BorderStroke(1.dp, edge), modifier = Modifier.fillMaxWidth().saharaPress(press).testTag(tag)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = { tick(); onToggle() }).semantics { stateDescription = state }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(28.dp).background(colour.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = colour, modifier = Modifier.size(17.dp)) }
            Text(title, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (count > 0) Box(Modifier.size(22.dp).background(colour, CircleShape), contentAlignment = Alignment.Center) { SaharaAnimatedText("$count") { Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) } }
            else SaharaChevron(open, null, Modifier.size(20.dp))
        }
    }
}

/** The tick-box rows of one NCCN problem group, one big row per problem (the accordion header carries the title). */
@Composable private fun DistressProblemGroup(category: DistressRules.Category, colour: Color, chosen: Set<String>, onToggle: (String) -> Unit) {
    val hindi = careHindi()
    val tick = rememberSaharaTick()
    Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Column {
            category.items.forEachIndexed { index, (id, _) ->
                val on = id in chosen
                if (index > 0) HorizontalDivider(color = DoctorHairline)
                // The tint fades in and out with the tick instead of switching in one frame.
                val rowFill by androidx.compose.animation.animateColorAsState(if (on) colour.copy(alpha = 0.10f) else colour.copy(alpha = 0f), SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "distress-problem")
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(rowFill)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { tick(); onToggle(id) }).padding(horizontal = 8.dp).testTag("distress-problem-$id"), verticalAlignment = Alignment.CenterVertically) {
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
            val pts = remember(series) { series.map { (at, v, said) -> ChartPoint(at.toDouble(), v.toDouble(), if (said) distressBand(v) else Sage) } }
            val noWord = t("No", "नहीं")
            val summary = remember(history, hindi, noWord) { history.reversed().joinToString("; ") { (item, check) -> Instant.ofEpochMilli(item.created).atZone(ZoneId.systemDefault()).format(day) + ": " + (if (check.distress) "${check.score ?: 0}" else noWord) } }
            CareTimeLineChart(points = pts, yMin = 0.0, yMax = 10.0, yTicks = listOf(0.0, 5.0, 10.0), yText = { it.toInt().toString() },
                description = summary, latestText = { it.toInt().toString() }, tag = "distress-history", height = 160.dp,
                bands = listOf(ChartBand(0.0, 3.5, Sage, 0.10f), ChartBand(3.5, 6.5, distressBand(5), 0.10f), ChartBand(6.5, 10.0, distressBand(8), 0.10f)),
                pointText = { it.toInt().toString() })
            // The latest answers as one list with dividers: score chip, date, then any ticked problems.
            val answersShown = rememberShownCount(personId)
            val answerRow: @Composable (Pair<CareItem, DistressCheck>) -> Unit = { (item, check) ->
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DistressScoreChip(check)
                    Column(Modifier.weight(1f)) {
                        Text(Instant.ofEpochMilli(item.created).atZone(ZoneId.systemDefault()).format(day) + " · " + DistressRules.shownTitle(check, hindi), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                        DistressRules.summary(check, hindi).takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 14.sp, lineHeight = 19.sp, color = DoctorCardSub) }
                    }
                }
            }
            history.shownOf(answersShown).forEachIndexed { i, pair -> if (i > 0) HorizontalDivider(color = DoctorHairline); answerRow(pair) }
            SaharaShowOlder(history, answersShown, tag = "distress-history", title = t("Distress answers", "परेशानी के जवाब"), searchText = { (item, check) -> Instant.ofEpochMilli(item.created).atZone(ZoneId.systemDefault()).format(day) + " " + DistressRules.shownTitle(check, hindi) + " " + DistressRules.summary(check, hindi) }, row = answerRow)
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

// ---------------------------------------------------------------- patients: the thermometer as a picture, the causes as pictures

/** The picture for each of the five patient groups (body, feelings, family, money and practical, faith). */
private fun distressGroupPicture(group: SymptomScaleRules.DistressGroup): ImageVector = when (group) {
    SymptomScaleRules.DistressGroup.BODY -> Icons.Filled.AccessibilityNew
    SymptomScaleRules.DistressGroup.FEELINGS -> Icons.Filled.Favorite
    SymptomScaleRules.DistressGroup.FAMILY -> Icons.Filled.FamilyRestroom
    SymptomScaleRules.DistressGroup.MONEY -> Icons.Filled.CurrencyRupee
    SymptomScaleRules.DistressGroup.FAITH -> Icons.Filled.SelfImprovement
}

/**
 * Screen 1 for a patient: a large thermometer picture (tap or slide), a face beside it that saddens as the number rises, the
 * number and a plain word, and a speaker that reads the question. Still the NCCN 0-10 number, so the care team sees the same score.
 */
@Composable private fun DistressPictureScoreStep(score: Int, onScore: (Int) -> Unit, onClose: () -> Unit, onNext: () -> Unit) {
    val t = careText(); val hindi = careHindi(); val lang = careLanguage()
    val speaker = rememberSimpleSpeaker(lang)
    val question = { l: CareLang -> careTr(l, "How much distress have you felt this past week? Tap the thermometer.", "पिछले एक हफ़्ते में आपको कितनी परेशानी रही? थर्मामीटर पर छुएँ।") }
    val picked = score in 0..10
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 16.dp).testTag("distress-step-score"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DistressHeader(onClose, t("Close", "बंद करें"), "distress-close")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(question(lang), fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, modifier = Modifier.weight(1f).testTag("distress-picture-question"))
            SymptomListenButton(speaker, question, "distress-listen")
        }
        // A flowing row: at the largest text size the face and number move under the thermometer instead of being squeezed.
        FlowRow(Modifier.fillMaxWidth().testTag("distress-picture"), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Rows grow with the phone's font size, so the numbers are never cut at the largest text setting, and are never under 48dp so a tap lands on the meant number.
            val scale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
            DistressThermometer(score, Modifier.align(Alignment.CenterVertically).width((150f * scale).dp.coerceAtMost(260.dp)), rowHeight = (34f * scale).dp.coerceAtLeast(48.dp), tube = 40.dp, onPick = onScore)
            Column(Modifier.align(Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CareMoodFace(if (picked) SymptomScaleRules.distressFace(score) else .5f, Modifier.size(104.dp).saharaPop(score.takeIf { picked }).clearAndSetSemantics {}.testTag("distress-face"))
                SaharaAnimatedText(if (picked) "$score" else "–") { shown ->
                    val n = shown.toIntOrNull()
                    Text(shown, fontFamily = SaharaTitleFont, fontSize = 60.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold, color = if (n != null) distressBandText(n) else DoctorCardSub, modifier = Modifier.testTag("distress-big-number"))
                }
                // The same words as the face scale, so the number also reads as a picture and a word.
                Text(if (picked) SymptomScaleRules.word(SymptomScaleRules.fromRating(score), SymptomDiaryRules.PERSONAL_SCALE, hindi) else t("Tap a number", "एक अंक छुएँ"),
                    fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("distress-picture-word"))
            }
        }
        Button(onClick = onNext, enabled = picked, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White), modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("distress-next")) {
            Text(t("Next", "आगे"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        DistressSource()
    }
}

/**
 * Screen 2 for a patient: five large picture tiles (body, feelings, family, money and practical, faith); a tap marks one as
 * troubling. The NCCN tick list for a chosen picture stays one tap away under "Add details", for a family member helping.
 */
@Composable private fun DistressPictureCausesStep(score: Int, groups: Set<String>, chosen: Set<String>, other: String, busy: Boolean, modelError: String?, onBack: () -> Unit,
    onGroup: (String, Boolean) -> Unit, onToggle: (String) -> Unit, onOther: (String) -> Unit, onSave: () -> Unit) {
    val t = careText(); val hindi = careHindi(); val lang = careLanguage()
    val speaker = rememberSimpleSpeaker(lang)
    val shown = SymptomScaleRules.shownGroups(groups, chosen).toSet()
    var details by rememberSaveable { mutableStateOf(false) }
    val oneColumn = LocalDensity.current.fontScale > 1.3f
    val prompt = { l: CareLang -> careTr(l, "What is troubling you? Tap the pictures.", "आपको क्या परेशान कर रहा है? तस्वीरें छुएँ।") + " " +
        SymptomScaleRules.DistressGroup.entries.joinToString(", ") { careTr(l, it.english, it.hindi) } + "." }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 16.dp).testTag("distress-step-causes"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DistressHeader(onBack, t("Back", "वापस"), "distress-back", t("What is causing your distress?", "आपकी परेशानी की वजह क्या है?"))
        if (score in 0..10) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("distress-causes-score")) {
            CareMoodFace(SymptomScaleRules.distressFace(score), Modifier.size(44.dp).clearAndSetSemantics {})
            Box(Modifier.size(40.dp).background(distressBand(score), CircleShape), contentAlignment = Alignment.Center) { Text("$score", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = distressOnBand(score)) }
            Text(SymptomScaleRules.word(SymptomScaleRules.fromRating(score), SymptomDiaryRules.PERSONAL_SCALE, hindi), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("What is troubling you? Tap the pictures.", "आपको क्या परेशान कर रहा है? तस्वीरें छुएँ।"), fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, modifier = Modifier.weight(1f).testTag("distress-groups-hint"))
            SymptomListenButton(speaker, prompt, "distress-groups-listen")
        }
        SymptomScaleRules.DistressGroup.entries.chunked(if (oneColumn) 1 else 2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { group ->
                    val on = group.key in shown
                    val colour = distressGroupStyle(group.category).second
                    DistressPictureTile(careTr(lang, group.english, group.hindi), distressGroupPicture(group), colour, on, "distress-group-${group.key}", Modifier.weight(1f).fillMaxHeight()) {
                        if (on) {
                            // Unmarking a picture clears everything ticked under it, so the picture and the saved list always agree.
                            onGroup(group.key, false)
                            DistressRules.categories.firstOrNull { it.en == group.category }?.items?.map { it.first }?.filter { it in chosen }?.forEach(onToggle)
                        } else { onGroup(group.key, true); speaker.say { l -> careTr(l, group.english, group.hindi) } }
                    }
                }
                if (row.size == 1 && !oneColumn) Spacer(Modifier.weight(1f))
            }
        }
        // For a helper: the NCCN items of the chosen pictures, and any other concern. Optional; the pictures alone are a full answer.
        TextButton(onClick = { details = !details }, modifier = Modifier.heightIn(min = 48.dp).testTag("distress-picture-details")) {
            Text(if (details) t("Hide details", "जानकारी छिपाएँ") else t("Add details (optional)", "और जानकारी जोड़ें (वैकल्पिक)"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = RustText)
            SaharaChevron(details, null, Modifier.size(22.dp))
        }
        AnimatedVisibility(details, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SymptomScaleRules.DistressGroup.entries.filter { it.key in shown }.forEach { group ->
                    val category = DistressRules.categories.first { it.en == group.category }
                    Text(careTr(lang, group.english, group.hindi), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.a11yHeading())
                    DistressProblemGroup(category, distressGroupStyle(group.category).second, chosen) { id -> onToggle(id); if (id !in chosen) onGroup(group.key, true) }
                }
                if (shown.isEmpty()) Text(t("Tap a picture first.", "पहले एक तस्वीर छुएँ।"), fontSize = 18.sp, color = DoctorCardSub)
                OutlinedTextField(other, { if (it.length <= DistressRules.OTHER_LIMIT) onOther(it) }, label = { Text(t("Any other concern (optional)", "कोई और चिंता (चाहें तो)")) }, minLines = 2, maxLines = 4, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("distress-other"))
            }
        }
        var lastError by remember { mutableStateOf("") }
        LaunchedEffect(modelError) { if (modelError != null) lastError = modelError }
        AnimatedVisibility(modelError != null, enter = SaharaMotion.enter, exit = SaharaMotion.exit) {
            Text(modelError ?: lastError, color = MaterialTheme.colorScheme.error, fontSize = 18.sp, modifier = Modifier.announce())
        }
        Button(onClick = onSave, enabled = !busy && score in 0..10, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White), modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("distress-save")) {
            Text(t("Save", "सहेजें"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** One large picture tile: a big icon on its colour, the word under it, and a tick when marked. */
@Composable private fun DistressPictureTile(label: String, icon: ImageVector, colour: Color, on: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val fill by androidx.compose.animation.animateColorAsState(if (on) colour.copy(alpha = 0.16f) else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "distress-picture")
    val edge by androidx.compose.animation.animateColorAsState(if (on) colour else DoctorHairline, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "distress-picture-edge")
    val tick = rememberSaharaTick()
    Box(modifier.heightIn(min = 140.dp).saharaPressBounce().saharaSelectBounce(on, .05f).clip(shape).background(fill, shape).border(if (on) 3.dp else 1.5.dp, edge, shape)
        .toggleable(value = on, role = Role.Checkbox, onValueChange = { tick(); onClick() }).semantics(mergeDescendants = true) { contentDescription = label }.testTag(tag)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(72.dp).background(colour.copy(alpha = if (on) 1f else 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(44.dp), tint = if (on) Color.White else colour)
            }
            Text(label, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = DoctorCardInk, textAlign = TextAlign.Center)
        }
        AnimatedVisibility(on, enter = SaharaMotion.enter, exit = SaharaMotion.exit, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Box(Modifier.size(32.dp).background(colour, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null, Modifier.size(22.dp), tint = Color.White) }
        }
    }
}
