package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One answer choice. [value] is the stable key; [en] and [hi] are shown. */
internal data class ScreeningOption(val value: String, val en: String, val hi: String, val selectable: Boolean = true)
internal data class ScreeningQuestion(val id: String, val en: String, val hi: String, val options: List<ScreeningOption>, val noteEn: String = "", val noteHi: String = "")
/** A plain suggestion for the doctor. [consider] = these answers point towards offering the conversation. Never a decision. */
internal data class ScreeningSuggestion(val en: String, val hi: String, val consider: Boolean)

/**
 * A screening tool for "Does this patient need a goals-of-care discussion?". Pluggable: when the doctors name
 * their own tool, add it as another implementation and make it [GoalsScreening.active]. The helper is optional.
 */
internal interface GoalsScreeningTool {
    val id: String
    val titleEn: String
    val titleHi: String
    val questions: List<ScreeningQuestion>
    /** Each source as shown under "Source:". */
    val sources: List<String>
    /** Null until enough is answered to say anything. */
    fun suggest(answers: Map<String, String>): ScreeningSuggestion?
}

/**
 * The surprise question plus the ECOG performance status. English ECOG wording is the ECOG-ACRIN published scale
 * (public domain); the Hindi is our reading translation and is labelled as such.
 */
internal object SurpriseQuestionEcog : GoalsScreeningTool {
    override val id = "surprise-question-ecog"
    override val titleEn = "Surprise question and ECOG performance status"
    override val titleHi = "“हैरानी वाला सवाल” और ECOG कार्य-क्षमता"
    const val SURPRISE = "surprise"
    const val ECOG = "ecog"
    const val NOT_SURPRISED = "not_surprised"
    const val SURPRISED = "surprised"
    override val questions = listOf(
        ScreeningQuestion(SURPRISE, "Would you be surprised if this patient died in the next 12 months?", "अगर यह मरीज़ अगले 12 महीनों में गुज़र जाए, तो क्या आपको हैरानी होगी?", listOf(
            ScreeningOption(SURPRISED, "Yes, I would be surprised", "हाँ, मुझे हैरानी होगी"),
            ScreeningOption(NOT_SURPRISED, "No, I would not be surprised", "नहीं, मुझे हैरानी नहीं होगी"),
        )),
        ScreeningQuestion(ECOG, "ECOG performance status", "ECOG कार्य-क्षमता (परफ़ॉर्मेंस स्टेटस)", listOf(
            ScreeningOption("0", "0 · Fully active, able to carry on all pre-disease performance without restriction", "0 · पूरी तरह सक्रिय; बीमारी से पहले के सारे काम बिना रोक-टोक कर पाते हैं"),
            ScreeningOption("1", "1 · Restricted in physically strenuous activity but ambulatory and able to carry out work of a light or sedentary nature, e.g., light house work, office work", "1 · भारी मेहनत के काम में रुकावट, पर चलते-फिरते हैं और हल्का या बैठकर होने वाला काम कर पाते हैं, जैसे हल्का घरेलू काम, दफ़्तर का काम"),
            ScreeningOption("2", "2 · Ambulatory and capable of all selfcare but unable to carry out any work activities; up and about more than 50% of waking hours", "2 · चलते-फिरते हैं और अपनी पूरी देखभाल कर लेते हैं, पर कोई काम नहीं कर पाते; जागने के आधे से ज़्यादा समय उठे-बैठे रहते हैं"),
            ScreeningOption("3", "3 · Capable of only limited selfcare; confined to bed or chair more than 50% of waking hours", "3 · अपनी थोड़ी ही देखभाल कर पाते हैं; जागने के आधे से ज़्यादा समय बिस्तर या कुर्सी पर रहते हैं"),
            ScreeningOption("4", "4 · Completely disabled; cannot carry on any selfcare; totally confined to bed or chair", "4 · पूरी तरह अक्षम; अपनी कोई देखभाल नहीं कर पाते; पूरी तरह बिस्तर या कुर्सी पर"),
            ScreeningOption("5", "5 · Dead", "5 · मृत्यु", selectable = false),
        ), noteEn = "English wording: ECOG-ACRIN. The Hindi is a translation for reading.", noteHi = "अंग्रेज़ी शब्द: ECOG-ACRIN। हिंदी सिर्फ़ पढ़ने के लिए अनुवाद है।"),
    )
    override val sources = listOf(
        "Surprise question: Gold Standards Framework, Proactive Identification Guidance (PIG). Downar J et al. CMAJ 2017;189(13):E484–E493 (on its own it is a modest predictor).",
        "ECOG Performance Status Scale: Oken MM et al. Am J Clin Oncol 1982;5(6):649–655. Developed by the Eastern Cooperative Oncology Group, Robert L. Comis, MD, Group Chair. Public domain (ECOG-ACRIN).",
        "Poor or declining performance status as a general indicator: Supportive and Palliative Care Indicators Tool (SPICT), University of Edinburgh.",
    )

    override fun suggest(answers: Map<String, String>): ScreeningSuggestion? {
        val ecog = answers[ECOG]?.toIntOrNull()?.takeIf { it in 0..4 }
        val limited = ecog != null && ecog >= 3
        return when (answers[SURPRISE]) {
            NOT_SURPRISED -> ScreeningSuggestion(
                "Suggestion: you would not be surprised. Consider offering a goals-of-care conversation soon." + if (limited) " ECOG $ecog also shows limited self-care." else "",
                "सुझाव: आपको हैरानी नहीं होगी। जल्द ही देखभाल के लक्ष्यों पर बातचीत की पेशकश करने पर विचार करें।" + if (limited) " ECOG $ecog भी अपनी देखभाल में कमी दिखाता है।" else "",
                consider = true)
            SURPRISED -> if (limited) ScreeningSuggestion(
                "Suggestion: you would be surprised, but ECOG $ecog means the patient is in bed or a chair more than half the day. Poor or declining function is a general indicator in SPICT; consider asking again if things change.",
                "सुझाव: आपको हैरानी होगी, पर ECOG $ecog का मतलब है कि मरीज़ आधे से ज़्यादा दिन बिस्तर या कुर्सी पर रहते हैं। SPICT में कमज़ोर या घटती कार्य-क्षमता एक सामान्य संकेत है; हालत बदले तो फिर से पूछें।",
                consider = false)
            else ScreeningSuggestion(
                "No prompt from these answers. You can still offer the conversation at any time.",
                "इन जवाबों से कोई संकेत नहीं। आप फिर भी कभी भी बातचीत की पेशकश कर सकते हैं।",
                consider = false)
            else -> null
        }
    }
}

internal object GoalsScreening {
    /** Registered tools; the first is active until the doctors' own tool is added. */
    val tools: List<GoalsScreeningTool> = listOf(SurpriseQuestionEcog)
    val active: GoalsScreeningTool get() = tools.first()
    /** Keeps only answers that belong to [tool] and are selectable options. */
    fun clean(tool: GoalsScreeningTool, answers: Map<String, String>): Map<String, String> =
        answers.filter { (q, v) -> tool.questions.firstOrNull { it.id == q }?.options?.any { it.value == v && it.selectable } == true }
}

/** Optional, collapsed helper at the start of the doctor's conversation flow. Nothing is saved; it never blocks Continue. */
@Composable internal fun GoalsScreeningHelper(scope: String, tool: GoalsScreeningTool = GoalsScreening.active) { val t = careText()
    var open by rememberSaveable(scope) { mutableStateOf(false) }
    var answers by rememberSaveable(scope, tool.id) { mutableStateOf(mapOf<String, String>()) }
    val suggestion = remember(answers) { tool.suggest(GoalsScreening.clean(tool, answers)) }
    Surface(shape = RoundedCornerShape(20.dp), color = DoctorCardReport, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("goals-screening")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = if (open) t("Collapse", "बंद करें") else t("Expand", "खोलें")) { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Optional · Does this patient need a goals-of-care discussion?", "वैकल्पिक · क्या इस मरीज़ से देखभाल के लक्ष्यों पर बात करनी चाहिए?"), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                    Text(t(tool.titleEn, tool.titleHi), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
                }
                Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = RustText, modifier = Modifier.size(48.dp).padding(12.dp))
            }
            if (open) {
                tool.questions.forEach { q ->
                    HorizontalDivider(color = DoctorHairline)
                    Text(t(q.en, q.hi), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.padding(top = 4.dp))
                    q.options.forEach { option ->
                        val chosen = answers[q.id] == option.value
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(chosen, enabled = option.selectable, role = Role.RadioButton) { answers = if (chosen) answers - q.id else answers + (q.id to option.value) }.testTag("goals-screening-${q.id}-${option.value}"), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = chosen, onClick = null, enabled = option.selectable)
                            Spacer(Modifier.width(8.dp))
                            Text(t(option.en, option.hi), fontSize = 15.sp, lineHeight = 21.sp, color = if (option.selectable) DoctorCardInk else DoctorCardFaint, modifier = Modifier.weight(1f))
                        }
                    }
                    if (q.noteEn.isNotBlank()) Text(t(q.noteEn, q.noteHi), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardFaint)
                }
                if (suggestion != null) Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, if (suggestion.consider) DoctorStatusWarn.copy(alpha = .35f) else DoctorHairline), color = if (suggestion.consider) DoctorStatusWarnBg else DoctorStatusRestBg, modifier = Modifier.fillMaxWidth().testTag("goals-screening-suggestion")) {
                    Text(t(suggestion.en, suggestion.hi), fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk, modifier = Modifier.padding(12.dp).announce())
                }
                Text(t("A suggestion, not a decision. Your clinical judgement decides. Answers are not saved.", "यह सिर्फ़ सुझाव है, फ़ैसला नहीं। फ़ैसला आपकी डॉक्टरी समझ से होगा। जवाब सहेजे नहीं जाते।"), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
                InfoDisclosure(t("Sources", "स्रोत")) {
                    tool.sources.forEach { Text(t("Source: ", "स्रोत: ") + it, fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub) }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
