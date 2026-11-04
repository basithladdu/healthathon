package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/**
 * What changes on Today for a family member (an invited account that is not the record's owner, or the walkthrough's
 * family user). The role is never chosen: it comes from [CareModel.viewRole].
 *
 * Family sees: "Caring for <name>", the patient's summary first, their own caregiver distress check (kept on their own
 * phone, never added to the patient's record), caregiver tips and the to-do list. The patient's own "How was today?"
 * check-in stays with the patient.
 */
internal object CareFamilyRules {
    fun isFamily(role: String, clinician: Boolean): Boolean = !clinician && role == "Family"

    /** Same 0-10 scale and question as the NCCN Distress Thermometer, which is also used with family caregivers. */
    val scores = 0..10
    /** NCCN: a score of 4 or more suggests distress worth talking about with the care team. */
    const val TALK_FROM = 4

    fun band(score: Int, hindi: Boolean): String = when {
        score <= 0 -> careTr(hindi, "No distress", "कोई परेशानी नहीं")
        score < TALK_FROM -> careTr(hindi, "Mild", "थोड़ी")
        score < 8 -> careTr(hindi, "Moderate", "मध्यम")
        else -> careTr(hindi, "Severe", "बहुत ज़्यादा")
    }

    /** Thermometer colour from calm green to rust. */
    fun colour(score: Int): Color = lerp(Color(0xFF3E8A68), Color(0xFFB9472C), score.coerceIn(0, 10) / 10f)

    /** General caregiving tips, not medical advice. One a day, in turn. */
    val tips = listOf(
        "Rest when you can, even for a short while." to "जब हो सके, थोड़ी देर ही सही, आराम कर लें।",
        "When someone offers help, say yes and name one task they can do." to "कोई मदद पूछे तो हाँ कहें और एक काम बता दें।",
        "Keep the emergency numbers and the QR card where the whole family can find them." to "आपातकालीन नंबर और QR कार्ड ऐसी जगह रखें जहाँ पूरा परिवार देख सके।",
        "Write down questions for the next visit as they come up." to "अगली मुलाक़ात के सवाल जैसे-जैसे आएँ, लिखते जाएँ।",
        "Tell the care team how you are coping too. Your wellbeing matters." to "देखभाल टीम को अपना हाल भी बताएँ। आपकी सेहत भी मायने रखती है।",
        "Share the caring with others in the family, so no one carries it alone." to "देखभाल का काम परिवार में बाँटें, ताकि कोई अकेला न उठाए।",
    )
    fun tipIndex(day: LocalDate, offset: Int = 0): Int = Math.floorMod(day.dayOfYear + offset, tips.size)

    fun encode(day: LocalDate, score: Int): String = "$day|${score.coerceIn(0, 10)}"
    /** Today's saved score, or null when nothing was saved today (yesterday's answer does not count). */
    fun decode(raw: String?, today: LocalDate): Int? {
        val parts = raw?.split('|') ?: return null
        if (parts.size != 2 || parts[0] != today.toString()) return null
        return parts[1].toIntOrNull()?.takeIf { it in scores }
    }
    fun key(storageNamespace: String, person: String) = "$storageNamespace|$person"
}

/** The family member's own distress score, on this phone only. */
internal class CaregiverCheckStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sahara_caregiver_check", Context.MODE_PRIVATE)
    fun load(key: String, today: LocalDate): Int? = CareFamilyRules.decode(prefs.getString(key, null), today)
    fun save(key: String, today: LocalDate, score: Int) { prefs.edit().putString(key, CareFamilyRules.encode(today, score)).apply() }
}

/** "How are you holding up?": the caregiver's own 0-10 distress check. Not the patient's, and not shared. */
@Composable fun FamilyCaregiverCheckCard(vm: CareModel, personName: String) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current
    val person by vm.selected.collectAsStateWithLifecycle()
    val store = remember(context) { CaregiverCheckStore(context) }
    val key = CareFamilyRules.key(vm.storageNamespace, person)
    val today = LocalDate.now()
    var saved by remember(key) { mutableStateOf(store.load(key, today)) }
    var picking by rememberSaveable(key) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(22.dp), color = SaharaLavender, modifier = Modifier.fillMaxWidth().testTag("family-caregiver-check")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Paper) { Icon(Icons.Default.Favorite, null, Modifier.padding(9.dp).size(22.dp), tint = DoctorAccent) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("How are you holding up?", "आप ख़ुद कैसे हैं?"), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
                    val current = saved
                    val band = current?.let { CareFamilyRules.band(it, hindi) }.orEmpty()
                    Text(if (current == null) t("Your own check, not $personName's", "आपकी अपनी जाँच, $personName की नहीं") else t("Today: $current/10 · $band", "आज: $current/10 · $band"), fontSize = 14.sp, color = DoctorCardSub)
                }
                CareHelpIcon(t("Your own check", "आपकी अपनी जाँच"), t("This is the Distress Thermometer question, for you as a carer: how much distress have you felt this past week, including today? 0 is none, 10 is extreme. Only you see it. It stays on this phone and is not added to $personName's record. A score of 4 or more is worth telling the care team about.", "यह आपके लिए, देखभाल करने वाले के रूप में, डिस्ट्रेस थर्मामीटर का सवाल है: पिछले हफ़्ते, आज समेत, आपको कितनी परेशानी रही? 0 मतलब कोई नहीं, 10 मतलब बहुत ज़्यादा। इसे सिर्फ़ आप देखते हैं। यह इसी फ़ोन पर रहता है और $personName के रिकॉर्ड में नहीं जुड़ता। 4 या ज़्यादा हो तो देखभाल टीम को ज़रूर बताएँ।"))
            }
            if (saved == null || picking) {
                Text(t("Distress this past week, including today", "पिछले हफ़्ते, आज समेत, परेशानी"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CareFamilyRules.scores.forEach { score ->
                        val chosen = saved == score
                        val fill = CareFamilyRules.colour(score)
                        Box(Modifier.size(44.dp).clip(CircleShape).background(if (chosen) fill else Paper).border(2.dp, fill, CircleShape)
                            .clickable(role = Role.RadioButton) { store.save(key, today, score); saved = score; picking = false }
                            .semantics { contentDescription = "$score/10"; selected = chosen }.testTag("family-check-$score"), contentAlignment = Alignment.Center) {
                            Text("$score", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (chosen) Color.White else DoctorCardInk)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(t("None", "कोई नहीं"), fontSize = 12.sp, color = DoctorCardSub, modifier = Modifier.weight(1f))
                    Text(t("Extreme", "बहुत ज़्यादा"), fontSize = 12.sp, color = DoctorCardSub, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            } else {
                val current = saved ?: 0
                LinearProgressIndicator(progress = { current / 10f }, color = CareFamilyRules.colour(current), trackColor = Paper, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)))
                if (current >= CareFamilyRules.TALK_FROM) Text(t("That is a lot to carry. Tell the care team how you are at the next visit or call.", "यह बहुत भारी है। अगली मुलाक़ात या फ़ोन पर देखभाल टीम को अपना हाल बताएँ।"), fontSize = 14.sp, color = DoctorCardInk)
                TextButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("family-check-change")) { Text(t("Change", "बदलें"), color = RustText) }
            }
        }
    }
}


/** A caregiving tip for the day, with "Next tip". */
@Composable fun FamilyCaregiverTipsCard() {
    val t = careText()
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val tip = CareFamilyRules.tips[CareFamilyRules.tipIndex(LocalDate.now(), offset)]
    Surface(shape = RoundedCornerShape(22.dp), color = SaharaSoftGold, modifier = Modifier.fillMaxWidth().testTag("family-caregiver-tips")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Paper) { Icon(Icons.Default.Lightbulb, null, Modifier.padding(9.dp).size(22.dp), tint = DoctorAccent) }
                Spacer(Modifier.width(12.dp))
                Text(t("A tip for carers", "देखभाल करने वालों के लिए एक सुझाव"), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading())
                CareHelpIcon(t("About these tips", "इन सुझावों के बारे में"), t("General tips for family carers. They are not medical advice. Ask the palliative care team about anything to do with treatment.", "परिवार में देखभाल करने वालों के लिए सामान्य सुझाव। यह इलाज की सलाह नहीं है। इलाज से जुड़ी हर बात पैलिएटिव देखभाल टीम से पूछें।"))
            }
            Text(t(tip.first, tip.second), fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardInk)
            TextButton(onClick = { offset += 1 }, modifier = Modifier.heightIn(min = 48.dp).testTag("family-tip-next")) { Text(t("Next tip", "अगला सुझाव"), color = RustText) }
        }
    }
}
