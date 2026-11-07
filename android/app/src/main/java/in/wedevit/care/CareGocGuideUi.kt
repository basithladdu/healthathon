@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What the doctor says and what is decided for one topic, English and Hindi (the other languages come from the dictionary by the English text). */
private class GuideTopicText(val decide: Pair<String, String>, val say: List<Pair<String, String>>, val answers: List<Pair<String, String>>)

private val GUIDE_TEXT: List<GuideTopicText> = listOf(
    // 0 Patient details
    GuideTopicText(
        "Confirm who the patient is and how to reach them." to "पुष्टि करें कि मरीज़ कौन हैं और उनसे कैसे संपर्क करें।",
        listOf("Let me first check your name and contact details." to "पहले मैं आपका नाम और संपर्क विवरण देख लूँ।", "Is this still the best number to reach you?" to "क्या अब भी इसी नंबर पर आपसे बात हो सकती है?"),
        listOf("Details correct" to "विवरण सही है", "Needs updating" to "बदलना है"),
    ),
    // 1 Caregiver details
    GuideTopicText(
        "Who looks after the patient most, and how to reach them." to "मरीज़ की सबसे ज़्यादा देखभाल कौन करता है, और उससे कैसे संपर्क करें।",
        listOf("Who looks after you most at home?" to "घर पर आपकी सबसे ज़्यादा देखभाल कौन करता है?", "Who should we call first if something changes?" to "कुछ बदले तो हम सबसे पहले किसे फ़ोन करें?"),
        listOf("Main caregiver named" to "मुख्य देखभालकर्ता का नाम", "Contact number noted" to "फ़ोन नंबर लिखा"),
    ),
    // 2 Patient capacity
    GuideTopicText(
        "Whether the patient can understand and decide for themselves." to "क्या मरीज़ खुद समझकर निर्णय ले सकते हैं।",
        listOf("Can you tell me in your own words what we are talking about today?" to "आज हम किस बारे में बात कर रहे हैं, क्या आप अपने शब्दों में बता सकते हैं?", "How comfortable do you feel making these decisions?" to "ये निर्णय लेने में आप कितना सहज महसूस करते हैं?"),
        listOf("Can decide" to "खुद निर्णय ले सकते हैं", "Needs support to decide" to "निर्णय में सहारा चाहिए", "Cannot decide now" to "अभी निर्णय नहीं ले सकते"),
    ),
    // 3 Existing directives
    GuideTopicText(
        "Any wishes or forms already written down." to "पहले से लिखी कोई इच्छा या फ़ॉर्म।",
        listOf("Have you written down any wishes about your care before?" to "क्या आपने पहले अपनी देखभाल के बारे में कोई इच्छा लिखी है?", "Is there a paper, or a person, we should know about?" to "क्या कोई कागज़ या कोई व्यक्ति है जिसके बारे में हमें जानना चाहिए?"),
        listOf("None" to "कोई नहीं", "Written directive exists" to "लिखित निर्देश है", "Decision-maker chosen" to "निर्णय लेने वाला चुना गया"),
    ),
    // 4 Understanding of disease
    GuideTopicText(
        "What the patient knows, and how much they want to know." to "मरीज़ क्या जानते हैं, और कितना जानना चाहते हैं।",
        listOf("What have the doctors told you about your illness so far?" to "अब तक डॉक्टरों ने आपको बीमारी के बारे में क्या बताया है?", "How much would you like me to share with you?" to "आप चाहेंगे कि मैं आपको कितना बताऊँ?"),
        listOf("Understands" to "समझते हैं", "Partly understands" to "कुछ समझते हैं", "Prefers family to be told" to "परिवार को बताना ठीक"),
    ),
    // 5 Patient values
    GuideTopicText(
        "What matters most to the patient in the time ahead." to "आने वाले समय में मरीज़ के लिए सबसे ज़रूरी क्या है।",
        listOf("What matters most to you in the time ahead?" to "आने वाले समय में आपके लिए सबसे ज़रूरी क्या है?", "What worries you the most right now?" to "अभी आपको सबसे ज़्यादा किस बात की चिंता है?"),
        listOf("Comfort" to "आराम", "Time with family" to "परिवार के साथ समय", "Being at home" to "घर पर रहना", "Staying alert" to "होश में रहना", "Spiritual needs" to "आध्यात्मिक ज़रूरतें"),
    ),
    // 6 Goals of care
    GuideTopicText(
        "The main aim of care from here." to "अब से देखभाल का मुख्य लक्ष्य।",
        listOf("Given everything, what would you most hope for?" to "सब कुछ देखते हुए, आप सबसे ज़्यादा किसकी आशा रखते हैं?", "If time is limited, what would a good day look like?" to "अगर समय सीमित हो, तो आपके लिए एक अच्छा दिन कैसा होगा?"),
        listOf("Live as long as possible" to "जितना हो सके जीवन बढ़ाना", "Comfort first" to "सबसे पहले आराम", "A balance of both" to "दोनों में संतुलन"),
    ),
    // 7 Home care vs hospital transfer
    GuideTopicText(
        "Where the patient would like to be cared for if they become more unwell." to "ज़्यादा तबीयत बिगड़ने पर मरीज़ कहाँ देखभाल चाहते हैं।",
        listOf("If you become more unwell, where would you like to be looked after?" to "अगर तबीयत और बिगड़े, तो आप कहाँ देखभाल चाहेंगे?", "Would you want to go to hospital, or have us help you at home?" to "क्या आप अस्पताल जाना चाहेंगे, या हम घर पर ही मदद करें?"),
        listOf("Stay home" to "घर पर रहें", "Hospital for symptoms only" to "सिर्फ़ तकलीफ़ के लिए अस्पताल", "Hospital for everything" to "हर बात के लिए अस्पताल"),
    ),
    // 8 CPR
    GuideTopicText(
        "Whether to try to restart the heart or breathing if they stop." to "अगर दिल या साँस रुक जाए तो उसे दोबारा चलाने की कोशिश करनी है या नहीं।",
        listOf("If your heart were to stop, would you want us to try to restart it?" to "अगर आपका दिल रुक जाए, तो क्या आप चाहेंगे कि हम उसे दोबारा चलाने की कोशिश करें?", "Let me explain what CPR involves, and then I would like to hear your thoughts." to "पहले मैं समझा दूँ कि CPR में क्या होता है, फिर मैं आपकी राय सुनना चाहूँगा।"),
        listOf("Attempt CPR" to "CPR की कोशिश करें", "Do not attempt CPR" to "CPR की कोशिश न करें"),
    ),
    // 9 ICU care
    GuideTopicText(
        "Whether to be cared for in intensive care if very unwell." to "बहुत बीमार होने पर गहन देखभाल (ICU) में रखना है या नहीं।",
        listOf("If you became very unwell, would you want to be looked after in intensive care?" to "अगर आप बहुत बीमार हो जाएँ, तो क्या आप ICU में देखभाल चाहेंगे?", "Some people choose a short trial of ICU care. What feels right to you?" to "कुछ लोग ICU की थोड़े समय की कोशिश चुनते हैं। आपको क्या ठीक लगता है?"),
        listOf("Full ICU care" to "पूरी ICU देखभाल", "Trial of ICU" to "ICU की कोशिश", "No ICU" to "ICU नहीं"),
    ),
    // 10 Ventilatory support
    GuideTopicText(
        "What breathing support the patient would want if breathing became hard." to "साँस लेना कठिन हो तो मरीज़ कौन सी मदद चाहते हैं।",
        listOf("If breathing became hard, what kind of help would you want?" to "अगर साँस लेना कठिन हो जाए, तो आप किस तरह की मदद चाहेंगे?", "There are masks, and tubes with a machine. Shall I explain each one?" to "इसमें मास्क होते हैं, और मशीन वाली नली भी। क्या मैं हर एक समझाऊँ?"),
        listOf("None" to "कोई नहीं", "Oxygen only" to "सिर्फ़ ऑक्सीजन", "NIV (BiPAP)" to "NIV (BiPAP)", "Invasive ventilation" to "नली वाली साँस की मशीन"),
    ),
    // 11 Dialysis
    GuideTopicText(
        "Whether to have dialysis if the kidneys stop working." to "अगर गुर्दे काम करना बंद कर दें तो डायलिसिस कराना है या नहीं।",
        listOf("If your kidneys stopped working, would you want dialysis?" to "अगर आपके गुर्दे काम करना बंद कर दें, तो क्या आप डायलिसिस चाहेंगे?", "Some people try it for a while and then review. How do you feel about that?" to "कुछ लोग इसे कुछ समय आज़माकर फिर देखते हैं। इस बारे में आप क्या सोचते हैं?"),
        listOf("Yes" to "हाँ", "Trial" to "आज़माकर देखें", "No" to "नहीं"),
    ),
    // 12 Nutritional support
    GuideTopicText(
        "How to help with food and fluids if eating becomes difficult." to "खाना मुश्किल हो जाए तो भोजन और पानी में कैसे मदद करें।",
        listOf("If eating became difficult, how would you like us to help?" to "अगर खाना मुश्किल हो जाए, तो आप चाहेंगे कि हम कैसे मदद करें?", "Is a feeding tube or a drip something you would want?" to "क्या आप नली से खाना या ड्रिप चाहेंगे?"),
        listOf("Normal diet" to "सामान्य भोजन", "NG tube" to "नाक की नली (NG)", "PEG" to "PEG", "IV fluids" to "नस से तरल", "None" to "कोई नहीं"),
    ),
)

private fun sectionTitle(section: String): Pair<String, String> = when (section) {
    "I" -> "SECTION I" to "खंड I"
    "II" -> "SECTION II" to "खंड II"
    else -> "SECTION III · DECISIVE ACTIONS" to "खंड III · निर्णायक कदम"
}

/** "How do you want to run it?": two cards, Guide me (default) or Free flow. */
@Composable internal fun GocRunModePicker(guideMe: Boolean, onChange: (Boolean) -> Unit) {
    val t = careText()
    Column(Modifier.fillMaxWidth().testTag("goc-guide-mode"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("How do you want to run it?", "आप इसे कैसे चलाना चाहेंगे?"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeCard(guideMe, t("Guide me", "मुझे राह दिखाएँ"), t("One topic at a time, with prompts", "एक बार में एक विषय, सुझावों के साथ"), "goc-mode-guide", Modifier.weight(1f)) { onChange(true) }
            ModeCard(!guideMe, t("Free flow", "अपने क्रम में"), t("Any order; tap topics as you go", "किसी भी क्रम में; बात करते हुए विषय छुएँ"), "goc-mode-free", Modifier.weight(1f)) { onChange(false) }
        }
    }
}

@Composable private fun ModeCard(selected: Boolean, title: String, sub: String, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) SaharaColors.PrimaryWash else Paper, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Rust else DoctorCardSub.copy(alpha = .5f)),
        modifier = modifier.fillMaxHeight().heightIn(min = 72.dp).clip(RoundedCornerShape(16.dp)).selectable(selected, role = Role.RadioButton, onClick = onClick).testTag(tag),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (selected) RustText else DoctorCardInk)
            Text(sub, fontSize = 14.sp, lineHeight = 19.sp, color = DoctorCardSub)
        }
    }
}

/**
 * The topic guide shown before and during the talk. [state] holds start times and the topic being discussed; [cur] is the topic the Guide-me card shows;
 * [seconds] is the recording time (-1 when nothing records). The page keeps the state and ticks the checklist from it.
 */
@Composable internal fun GocTopicPanel(
    guideMe: Boolean, state: GocGuideState, cur: Int, seconds: Int, warnTitle: String?,
    onTap: (Int) -> Unit, onDone: () -> Unit, onAllTopics: () -> Unit, onDismissWarning: () -> Unit,
) {
    val t = careText(); val hindi = careHindi()
    val rows = GocChecklist.ROWS
    val discussed = state.discussed
    if (guideMe) {
        val idx = cur.coerceIn(rows.indices); val row = rows[idx]; val text = GUIDE_TEXT[idx]
        val next = GocTopicGuide.next(discussed + idx, idx, rows.size)
        Column(Modifier.fillMaxWidth().testTag("goc-guide-card"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(when { i == idx -> Rust; i in discussed -> DoctorStatusGood; else -> DoctorCardSub.copy(alpha = .25f) }))
                }
            }
            Text("${t("Topic", "विषय")} ${idx + 1}/${rows.size}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardSub)
            Surface(color = Paper, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, DoctorCardSub.copy(alpha = .4f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.heightIn(min = 28.dp).clip(RoundedCornerShape(8.dp)).background(SaharaColors.PrimaryWash).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            Text(row.section, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RustText)
                        }
                        if (idx in discussed) Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, null, tint = DoctorStatusGood, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                            Text("${t("Discussed", "बात हुई")} ${GocTopicGuide.mmss(state.stamps[idx] ?: -1)}", fontSize = 14.sp, color = DoctorStatusGood)
                        }
                    }
                    Text(row.title.pick(hindi), fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, fontFamily = SaharaTitleFont)
                    Text("${t("Decide:", "तय करना है:")} ${t(text.decide.first, text.decide.second)}", fontSize = 16.sp, lineHeight = 23.sp, color = DoctorCardInk)
                    Text(t("TRY SAYING", "ऐसे कह सकते हैं"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DoctorCardSub, letterSpacing = 0.8.sp)
                    text.say.forEach { (en, hi) ->
                        Surface(color = Cream, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("“${t(en, hi)}”", fontSize = 16.sp, lineHeight = 23.sp, color = DoctorCardInk, modifier = Modifier.padding(12.dp))
                        }
                    }
                    Text(t("ANSWER YOU'LL CONFIRM LATER", "जिसकी पुष्टि आप बाद में करेंगे"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DoctorCardSub, letterSpacing = 0.8.sp)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        text.answers.forEach { (en, hi) ->
                            Surface(color = Color.Transparent, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, DoctorCardSub.copy(alpha = .6f))) {
                                Text(t(en, hi), fontSize = 14.sp, color = DoctorCardInk, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onAllTopics, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.5.dp, RustText), modifier = Modifier.weight(.4f).heightIn(min = 52.dp).testTag("goc-guide-all")) {
                    Text(t("All topics", "सभी विषय"), color = RustText, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
                Button(onClick = onDone, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White), modifier = Modifier.weight(.6f).heightIn(min = 52.dp).testTag("goc-guide-done")) {
                    Text(if (next == null) t("Done", "हो गया") else "${t("Done", "हो गया")} · ${t("Next", "अगला")}: ${rows[next].title.pick(hindi)}", fontSize = 15.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
                }
            }
        }
    } else {
        val titles = rows.map { it.title.en }
        val essentials = GocTopicGuide.essentialsDone(discussed, titles)
        Column(Modifier.fillMaxWidth().testTag("goc-topic-grid"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(t("Topics", "विषय"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                Text(t("Tap a topic when you start it", "विषय शुरू करते समय उसे छुएँ"), fontSize = 14.sp, color = DoctorCardSub)
            }
            if (warnTitle != null) Surface(color = DoctorStatusRestBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("goc-guide-warning")) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$warnTitle ${t("started before What the patient knows. Consider covering it first.", "“मरीज़ क्या जानते हैं” से पहले शुरू हुआ। इसे पहले करने पर विचार करें।")}", color = DoctorStatusRest, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                    TextButton(onClick = onDismissWarning, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Dismiss", "बंद करें"), color = DoctorStatusRest, fontWeight = FontWeight.SemiBold) }
                }
            }
            listOf("I", "II", "III").forEach { sec ->
                val inSection = rows.indices.filter { rows[it].section == sec }
                Text(sectionTitle(sec).let { t(it.first, it.second) }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DoctorCardSub, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 6.dp))
                inSection.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { i -> TopicTile(rows[i], i, state, seconds, Modifier.weight(1f)) { onTap(i) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Surface(color = Cream, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("goc-guide-essentials")) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${t("Essentials", "ज़रूरी विषय")} $essentials/${GocTopicGuide.ESSENTIAL_COUNT}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                    LinearProgressIndicator(progress = { essentials.toFloat() / GocTopicGuide.ESSENTIAL_COUNT }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = DoctorStatusGood, trackColor = DoctorCardSub.copy(alpha = .2f))
                }
            }
        }
    }
}

@Composable private fun TopicTile(row: GocChecklistRow, idx: Int, state: GocGuideState, seconds: Int, modifier: Modifier, onClick: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    val now = idx == state.now; val done = idx in state.discussed
    val start = state.stamps[idx] ?: -1
    val essential = GocTopicGuide.isEssential(row.title.en)
    val shape = RoundedCornerShape(14.dp)
    Surface(
        color = when { done -> DoctorStatusGoodBg; now -> SaharaColors.PrimaryWash; else -> Paper }, shape = shape,
        border = BorderStroke(if (now) 2.dp else 1.dp, when { done -> DoctorStatusGood.copy(alpha = .5f); now -> Rust; else -> DoctorCardSub.copy(alpha = .4f) }),
        modifier = modifier.fillMaxHeight().heightIn(min = 72.dp).clip(shape).clickable(role = Role.Button, onClick = onClick).testTag("goc-topic-$idx"),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.title.pick(hindi), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
            when {
                done -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, tint = DoctorStatusGood, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    Text("${t("Discussed", "बात हुई")} ${GocTopicGuide.mmss(start)}", fontSize = 13.sp, lineHeight = 17.sp, color = DoctorStatusGood)
                }
                now -> Text("${t("Now discussing", "अभी बात हो रही है")} · ${GocTopicGuide.mmss(start)}", fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = RustText)
                else -> Text(if (essential) "${t("Not yet", "अभी नहीं")} · ${t("Essential", "ज़रूरी")}" else t("Not yet", "अभी नहीं"), fontSize = 13.sp, lineHeight = 17.sp, color = DoctorCardSub)
            }
        }
    }
}
