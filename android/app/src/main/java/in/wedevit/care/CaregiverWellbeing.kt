package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The family member's wellbeing checks and private journal, on this phone only. One key per account and person. */
internal class CaregiverWellbeingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sahara_caregiver_wellbeing", Context.MODE_PRIVATE)
    fun history(key: String): List<WellbeingEntry> = CaregiverWellbeingRules.decodeHistory(prefs.getString("$key|zbi4", null))
    fun saveCheck(key: String, day: LocalDate, score: Int): List<WellbeingEntry> {
        val next = CaregiverWellbeingRules.add(history(key), WellbeingEntry(day, score))
        prefs.edit().putString("$key|zbi4", CaregiverWellbeingRules.encodeHistory(next)).apply()
        return next
    }
    fun journal(key: String): List<JournalNote> = CaregiverWellbeingRules.decodeJournal(prefs.getString("$key|journal", null))
    fun addNote(key: String, at: Long, text: String): List<JournalNote> {
        val next = CaregiverWellbeingRules.addNote(journal(key), at, text)
        prefs.edit().putString("$key|journal", CaregiverWellbeingRules.encodeJournal(next)).apply()
        return next
    }
}

private val zbiAnswerEn = listOf("Never", "Rarely", "Sometimes", "Quite often", "Nearly always")
private val zbiAnswerHi = listOf("कभी नहीं", "शायद ही कभी", "कभी-कभी", "अक्सर", "लगभग हमेशा")

/**
 * "You matter too": a short, warm full-screen-friendly message. [onCheck] opens the 1-minute wellbeing check
 * (the lead decides where that goes), [onDone] closes it. Show it to a family member after they save a symptom note.
 */
@Composable fun CaregiverYouMatter(onDone: () -> Unit, onCheck: () -> Unit) {
    val t = careText()
    Column(Modifier.fillMaxSize().background(SaharaColors.GroundWarm).padding(24.dp).testTag("caregiver-you-matter"),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = Paper) { SaharaLottie(SaharaLottieKind.Heart, Modifier.padding(10.dp).size(72.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(t("You matter, and your health matters too.", "आप मायने रखते हैं, और आपकी सेहत भी मायने रखती है।"), fontFamily = SaharaTitleFont, fontSize = 26.sp, lineHeight = 32.sp,
            fontWeight = FontWeight.Medium, color = DoctorCardInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.a11yHeading())
        Spacer(Modifier.height(12.dp))
        Text(t("Thank you for caring so well. Have you had some water, something to eat, and a moment to sit down today? Looking after yourself is part of looking after them.",
            "इतने प्यार से देखभाल करने के लिए धन्यवाद। आज आपने पानी पिया, कुछ खाया, और थोड़ी देर बैठकर आराम किया? अपना ध्यान रखना भी उनकी देखभाल का हिस्सा है।"),
            fontSize = 17.sp, lineHeight = 24.sp, color = DoctorCardInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        SaharaPrimaryButton(t("Check how I am (1 minute)", "अपना हाल जाँचें (1 मिनट)"), onCheck, Modifier.fillMaxWidth().testTag("caregiver-you-matter-check"), icon = Icons.Default.Favorite, tone = SaharaTone.Teal)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp).testTag("caregiver-you-matter-done")) { Text(t("Done", "हो गया"), color = RustText, fontSize = 16.sp) }
    }
}

/** The "Your wellbeing" card on the family home: ZBI-4 check with a small trend, a calm flag with help options, and a private journal. */
@Composable fun CaregiverWellbeingCard(vm: CareModel, personName: String) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current
    val person by vm.selected.collectAsStateWithLifecycle()
    val store = remember(context) { CaregiverWellbeingStore(context) }
    val key = CaregiverWellbeingRules.storeKey(vm.storageNamespace, person)
    var history by remember(key) { mutableStateOf(store.history(key)) }
    var journal by remember(key) { mutableStateOf(store.journal(key)) }
    var checking by rememberSaveable(key) { mutableStateOf(false) }
    var writing by rememberSaveable(key) { mutableStateOf(false) }
    val answers = remember(key) { mutableStateListOf<Int?>(null, null, null, null) }
    var draft by rememberSaveable(key) { mutableStateOf("") }
    val latest = history.maxByOrNull { it.day }
    val flagged = CaregiverWellbeingRules.shouldFlag(history)

    Surface(shape = RoundedCornerShape(20.dp), color = SaharaTone.Teal.wash, modifier = Modifier.fillMaxWidth().saharaAppear().testTag("caregiver-wellbeing")) {
        Column(Modifier.saharaContentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Paper) { Icon(Icons.Default.Favorite, null, Modifier.padding(10.dp).size(22.dp), tint = SaharaTone.Teal.ink) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Your wellbeing", "आपकी सेहत"), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
                    Text(t("Kept on this phone only. Not shared.", "सिर्फ़ इस फ़ोन पर। साझा नहीं होता।"), fontSize = 14.sp, color = DoctorCardSub)
                }
            }
            if (latest == null) {
                Text(t("Caring is hard work. A one-minute check helps you notice when you need support.", "देखभाल करना मेहनत का काम है। एक मिनट की जाँच से पता चलता है कि आपको कब सहारे की ज़रूरत है।"), fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardInk)
            } else {
                val band = if (CaregiverWellbeingRules.isHigh(latest.score)) t("A lot to carry", "बहुत बोझ") else t("Manageable", "संभल रहा है")
                Surface(shape = RoundedCornerShape(50), color = Paper) {
                    Text(t("Last check: ${latest.score}/${CaregiverWellbeingRules.MAX_SCORE} · $band", "पिछली जाँच: ${latest.score}/${CaregiverWellbeingRules.MAX_SCORE} · $band"),
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                }
                WellbeingTrend(history)
            }

            if (checking) {
                Text(t("In the past month, how often have you felt this way?", "पिछले एक महीने में, आपको कितनी बार ऐसा लगा?"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                val name = personName
                val questions = listOf(
                    t("Stressed, between caring for $name and meeting your other responsibilities?", "$name की देखभाल और अपनी दूसरी ज़िम्मेदारियों के बीच तनाव?"),
                    t("Strained when you are around $name?", "$name के पास होने पर दबाव या खिंचाव?"),
                    t("That your own health has suffered because of caring for $name?", "$name की देखभाल से आपकी अपनी सेहत बिगड़ी है?"),
                    t("That you don't have enough time for yourself because of caring for $name?", "$name की देखभाल के कारण अपने लिए समय नहीं मिलता?"),
                )
                questions.forEachIndexed { i, q ->
                    Text(q, fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardInk)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0..CaregiverWellbeingRules.ITEM_MAX).forEach { v ->
                            val chosen = answers[i] == v
                            val label = if (hindi) zbiAnswerHi[v] else zbiAnswerEn[v]
                            Surface(shape = RoundedCornerShape(50), color = if (chosen) SaharaTone.Teal.solid else Paper, border = BorderStroke(1.5.dp, SaharaTone.Teal.solid),
                                modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).clickable(role = Role.RadioButton) { answers[i] = v }
                                    .semantics { contentDescription = label; selected = chosen }.testTag("wellbeing-$i-$v")) {
                                Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (chosen) SaharaTone.Teal.onSolid else DoctorCardInk)
                                }
                            }
                        }
                    }
                }
                val total = CaregiverWellbeingRules.score(answers.toList())
                SaharaPrimaryButton(t("Save", "सहेजें"), {
                    if (total != null) { history = store.saveCheck(key, LocalDate.now(), total); checking = false; for (i in answers.indices) answers[i] = null }
                }, Modifier.fillMaxWidth().testTag("wellbeing-save"), enabled = total != null, tone = SaharaTone.Teal)
                if (total == null) Text(t("Answer all four to save.", "सहेजने के लिए चारों के जवाब दें।"), fontSize = 14.sp, color = DoctorCardSub)
                TextButton(onClick = { checking = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Not now", "अभी नहीं"), color = RustText) }
            } else {
                SaharaPrimaryButton(if (latest == null) t("Take the 1-minute check", "1 मिनट की जाँच करें") else t("Check again", "फिर से जाँचें"), { checking = true },
                    Modifier.fillMaxWidth().testTag("wellbeing-start"), tone = SaharaTone.Teal)
            }

            if (!checking) CaregiverSupportCard(hindi = hindi, flagged = flagged)

            // Private journal
            Text(t("How are you feeling about caring for $personName?", "$personName की देखभाल को लेकर आप कैसा महसूस कर रहे हैं?"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
            if (writing) {
                OutlinedTextField(value = draft, onValueChange = { draft = it.take(CaregiverWellbeingRules.MAX_NOTE_CHARS) }, modifier = Modifier.fillMaxWidth().testTag("journal-text"),
                    placeholder = { Text(t("Write a few words. Only you can see this.", "कुछ शब्द लिखें। इसे सिर्फ़ आप देख सकते हैं।")) }, minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Paper, unfocusedContainerColor = Paper))
                SaharaPrimaryButton(t("Save note", "नोट सहेजें"), {
                    journal = store.addNote(key, System.currentTimeMillis(), draft); draft = ""; writing = false
                }, Modifier.fillMaxWidth().testTag("journal-save"), enabled = draft.isNotBlank(), tone = SaharaTone.Teal)
                TextButton(onClick = { writing = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें"), color = RustText) }
            } else {
                SaharaSecondaryButton(t("Write in my journal", "अपनी डायरी में लिखें"), { writing = true }, Modifier.fillMaxWidth().testTag("journal-write"), tone = SaharaTone.Teal)
            }
            if (journal.isNotEmpty()) {
                Text(t("Only you can see these, on this phone.", "इन्हें सिर्फ़ आप देख सकते हैं, इसी फ़ोन पर।"), fontSize = 14.sp, color = DoctorCardSub)
                val fmt = remember(hindi) { DateTimeFormatter.ofPattern("d MMM, h:mm a", if (hindi) Locale("hi", "IN") else Locale.ENGLISH) }
                journal.take(5).forEach { n ->
                    Surface(shape = RoundedCornerShape(14.dp), color = Paper, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(java.time.Instant.ofEpochMilli(n.at).atZone(java.time.ZoneId.systemDefault()).format(fmt), fontSize = 13.sp, color = DoctorCardSub)
                            Text(n.text, fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardInk)
                        }
                    }
                }
            }
        }
    }
}

/** A small bar per past check, oldest to newest; bars turn rust at the high-burden line (8). */
@Composable private fun WellbeingTrend(history: List<WellbeingEntry>) {
    val t = careText()
    val shown = CaregiverWellbeingRules.recent(history)
    if (shown.size < 2) return
    val summary = shown.joinToString(", ") { "${it.score}" }
    Column(Modifier.semantics { contentDescription = t("Past checks, oldest first: $summary out of ${CaregiverWellbeingRules.MAX_SCORE}", "पिछली जाँचें, पुरानी पहले: $summary, कुल ${CaregiverWellbeingRules.MAX_SCORE} में से") }.testTag("wellbeing-trend"),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            shown.forEach { e ->
                val h = (6 + 42f * e.score / CaregiverWellbeingRules.MAX_SCORE).dp
                Box(Modifier.weight(1f).height(h).clip(RoundedCornerShape(6.dp)).background(if (CaregiverWellbeingRules.isHigh(e.score)) SaharaTone.Rust.solid else SaharaTone.Teal.solid))
            }
        }
        val label = when (CaregiverWellbeingRules.trend(history)) {
            CaregiverWellbeingRules.Trend.Rising -> t("Higher than your last check", "पिछली जाँच से ज़्यादा")
            CaregiverWellbeingRules.Trend.Falling -> t("Lower than your last check", "पिछली जाँच से कम")
            else -> t("About the same as your last check", "पिछली जाँच जैसा ही")
        }
        Text(label, fontSize = 14.sp, color = DoctorCardSub)
    }
}

/** The calm red flag: "You're carrying a lot", with a counsellor, respite care and caregiver support groups. */
@Composable fun CaregiverSupportCard(hindi: Boolean, flagged: Boolean = true) {
    val t = careText(); val context = LocalContext.current
    // Always on the family home: calm and folded when the check is fine, open and flagged when it is high or rising.
    var open by remember(flagged) { mutableStateOf(flagged) }
    Surface(shape = RoundedCornerShape(16.dp), color = if (flagged) SaharaTone.Error.wash else SaharaTone.Teal.wash, border = BorderStroke(1.5.dp, if (flagged) SaharaTone.Error.solid else SaharaTone.Teal.solid), modifier = Modifier.fillMaxWidth().animateContentSize().testTag(if (flagged) "caregiver-flag" else "caregiver-support")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Text(if (flagged) t("You're carrying a lot", "आप बहुत कुछ उठा रहे हैं") else t("Support for you", "आपके लिए मदद"), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading())
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = DoctorCardInk)
            }
            if (!open) Text(t("Psychiatrists and counsellors, respite care and caregiver support groups.", "मनोचिकित्सक और काउंसलर, आराम के लिए देखभाल और देखभालकर्ता सहायता समूह।"), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub)
            if (open) {
            if (flagged) Text(t("Many family caregivers feel this way. It is not a failing, and help is there. This check is a screening, not a diagnosis.",
                "कई परिवारजन ऐसा ही महसूस करते हैं। यह कोई कमी नहीं है, और मदद उपलब्ध है। यह जाँच सिर्फ़ एक शुरुआती संकेत है, निदान नहीं।"), fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardInk)

            Text(t("Talk to a psychiatrist or counsellor", "मनोचिकित्सक या काउंसलर से बात करें"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
            Text(t("Tele-MANAS is free, day and night.", "टेली-मानस मुफ़्त है, दिन-रात।"), fontSize = 14.sp, color = DoctorCardSub)
            SaharaPrimaryButton(t("Call Tele-MANAS 14416", "टेली-मानस 14416 पर कॉल करें"), { open(context, Intent(Intent.ACTION_DIAL, Uri.parse(NearbyHelp.dialUri(CaregiverWellbeingRules.TELE_MANAS_NUMBER)))) },
                Modifier.fillMaxWidth().testTag("caregiver-flag-call"), icon = Icons.Default.Call, tone = SaharaTone.Indigo)
            SaharaSecondaryButton(t("Find a psychiatrist or counsellor nearby", "पास में मनोचिकित्सक या काउंसलर खोजें"), { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(CaregiverWellbeingRules.MAPS_QUERY)))) },
                Modifier.fillMaxWidth().testTag("caregiver-flag-nearby"), icon = Icons.Default.LocationOn, tone = SaharaTone.Indigo)

            Text(t("Respite care", "कुछ समय का आराम (रेस्पाइट केयर)"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
            Text(t("Your palliative care team can arrange temporary nursing or day-care support, so you can rest. Ask them at your next call or visit.",
                "आपकी पैलिएटिव केयर टीम कुछ समय के लिए नर्सिंग या डे-केयर सहायता का इंतज़ाम कर सकती है, ताकि आप आराम कर सकें। अगली कॉल या मुलाक़ात में उनसे पूछें।"), fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardInk)

            Text(t("Caregiver support groups", "देखभालकर्ता सहायता समूह"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
            SaharaSecondaryButton(t("Swayam Foundation caregiver support", "स्वयं फ़ाउंडेशन देखभालकर्ता सहायता"), { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(CaregiverWellbeingRules.SUPPORT_GROUP_SWAYAM))) },
                Modifier.fillMaxWidth().testTag("caregiver-flag-swayam"), icon = Icons.Default.Groups, tone = SaharaTone.Teal)
            SaharaSecondaryButton(t("SoulUp caregiving support group", "सोलअप देखभाल सहायता समूह"), { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(CaregiverWellbeingRules.SUPPORT_GROUP_SOULUP))) },
                Modifier.fillMaxWidth().testTag("caregiver-flag-soulup"), icon = Icons.Default.OpenInNew, tone = SaharaTone.Teal)
            Text(t("These open in your browser. In an emergency, call 112.", "ये आपके ब्राउज़र में खुलते हैं। आपात स्थिति में 112 पर कॉल करें।"), fontSize = 14.sp, color = DoctorCardSub)
            }
        }
    }
}
