package `in`.wedevit.care

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "If someone has died": a quiet, step-by-step guide for families in India. Reached from a soft row at the bottom of the SOS sheet,
 * never shown on its own. Plain words ("died"), one step at a time, no rush for an expected death.
 * Facts checked on the web on 2026-11-20 (Registration of Births and Deaths Act 1969 and its 2023 amendment, MCCD Forms 4/4A,
 * Pallium India's guide to a death at home, RBI and UIDAI pages, eye-bank guidance, Tele-MANAS). Sources are listed in [AfterDeathGuide.sources].
 * Rules differ a little by state, which the guide says.
 */
data class AfterDeathStep(val id: String, val title: String, val titleHi: String, val points: List<Pair<String, String>>)

object AfterDeathGuide {
    const val TELE_MANAS = "14416"
    const val EMERGENCY = "112"

    // The SOS row names what the family sees, not a conclusion; "if they have died" comes only after the checking step.
    val entry = "Not breathing or not waking up?" to "साँस नहीं ले रहे या जाग नहीं रहे?"
    val entrySub = "What to check, and what to do next" to "क्या देखें, और आगे क्या करें"
    val opening = "What to do, step by step." to "क्या करें, एक-एक करके।"
    val reassurance = "Check gently first. If you are not sure, or this was sudden, call 112 now." to
        "पहले धीरे से देखें। अगर आपको यक़ीन न हो, या यह अचानक हुआ हो, तो अभी 112 पर कॉल करें।"

    val steps = listOf(
        AfterDeathStep("check", "Gently check", "धीरे से देखें", listOf(
            "If they have passed away, they will not be breathing and will not respond to your voice or touch. Their eyes may stay a little open and their jaw may relax." to
                "अगर वे चले गए हैं, तो वे साँस नहीं ले रहे होंगे और आपकी आवाज़ या स्पर्श पर कोई प्रतिक्रिया नहीं देंगे। उनकी आँखें थोड़ी खुली रह सकती हैं और जबड़ा ढीला हो सकता है।",
            "One or two last breaths can come about a minute after what seemed the last one. This is normal." to
                "जो आख़िरी साँस लगी, उसके लगभग एक मिनट बाद एक-दो साँसें और आ सकती हैं। यह सामान्य है।",
            "If you are not sure, or the death was sudden or from an injury, call 112." to
                "अगर आपको यक़ीन न हो, या मृत्यु अचानक या किसी चोट से हुई हो, तो 112 पर कॉल करें।",
        )),
        AfterDeathStep("doctor", "Call their doctor", "उनके डॉक्टर को बुलाएँ", listOf(
            "Call their palliative care or home-care doctor, or your family doctor. A doctor confirms the death and writes the time and cause." to
                "उनके पैलिएटिव केयर या होम-केयर डॉक्टर, या अपने फ़ैमिली डॉक्टर को कॉल करें। डॉक्टर मृत्यु की पुष्टि करते हैं और समय व कारण लिखते हैं।",
            "For a death at home this is the cause-of-death certificate (Form 4A). In a hospital, the hospital gives it (Form 4) and reports the death for you." to
                "घर पर मृत्यु होने पर यह मृत्यु के कारण का प्रमाणपत्र (फ़ॉर्म 4A) है। अस्पताल में, अस्पताल यह (फ़ॉर्म 4) देता है और मृत्यु की सूचना आपकी ओर से देता है।",
            "If their own doctor cannot come, ask a nearby doctor or hospital. A death from a known illness does not need the police." to
                "अगर उनके अपने डॉक्टर नहीं आ सकते, तो पास के डॉक्टर या अस्पताल से पूछें। किसी ज्ञात बीमारी से हुई मृत्यु में पुलिस की ज़रूरत नहीं होती।",
        )),
        AfterDeathStep("first-day", "The first day", "पहला दिन", listOf(
            "Let close family know. Ask one person to help with calls." to "क़रीबी परिवार को बताएँ। फ़ोन करने में मदद के लिए किसी एक व्यक्ति से कहें।",
            "If the funeral is not the same day, a freezer box can be rented to keep them cool. Your palliative team can suggest one, and a hearse." to
                "अगर अंतिम संस्कार उसी दिन नहीं है, तो ठंडा रखने के लिए फ़्रीज़र बॉक्स किराए पर मिल सकता है। आपकी पैलिएटिव टीम एक बॉक्स और शव-वाहन सुझा सकती है।",
            "Call the cremation or burial ground ahead. Keep their receipt: you need it to register the death." to
                "श्मशान या क़ब्रिस्तान को पहले से फ़ोन करें। उनकी रसीद रख लें: मृत्यु दर्ज कराने में इसकी ज़रूरत होती है।",
            "If they wished to donate their eyes, call an eye bank within about 6 hours. Until then, switch off the fan, close the eyes, place damp cotton on them and raise the head on a pillow." to
                "अगर वे नेत्रदान करना चाहते थे, तो लगभग 6 घंटे के अंदर किसी नेत्र बैंक को फ़ोन करें। तब तक पंखा बंद करें, आँखें बंद करें, उन पर गीली रुई रखें और सिर को तकिये पर ऊँचा रखें।",
            "If they pledged their body to a medical college, call its anatomy department straight away." to
                "अगर उन्होंने देहदान का संकल्प लिया था, तो तुरंत मेडिकल कॉलेज के एनाटॉमी विभाग को फ़ोन करें।",
        )),
        AfterDeathStep("medicines", "Leftover medicines", "बची हुई दवाइयाँ", listOf(
            "Return unused morphine and other strong pain medicines to the palliative team or the pharmacy that gave them. Please do not keep, share or throw them away." to
                "बची हुई मॉर्फ़ीन और दर्द की दूसरी तेज़ दवाइयाँ पैलिएटिव टीम या उस फ़ार्मेसी को लौटा दें जहाँ से मिली थीं। कृपया इन्हें न रखें, न किसी को दें, न फेंकें।",
        )),
        AfterDeathStep("register", "Registering the death", "मृत्यु दर्ज कराना", listOf(
            "Register within 21 days. It is free within that time." to "21 दिनों के अंदर दर्ज कराएँ। इस समय में यह मुफ़्त है।",
            "For a death at home, a family member reports it to the local registrar: the municipal office in a town, the gram panchayat in a village, or online on your state's portal or dc.crsorgi.gov.in." to
                "घर पर मृत्यु होने पर परिवार का कोई सदस्य स्थानीय रजिस्ट्रार को सूचना देता है: शहर में नगर निगम कार्यालय, गाँव में ग्राम पंचायत, या ऑनलाइन अपने राज्य के पोर्टल या dc.crsorgi.gov.in पर।",
            "Usually needed: the doctor's certificate, their ID such as Aadhaar, your ID and address proof, and the cremation or burial receipt." to
                "आम तौर पर चाहिए: डॉक्टर का प्रमाणपत्र, उनकी पहचान जैसे आधार, आपकी पहचान और पते का प्रमाण, और श्मशान या क़ब्रिस्तान की रसीद।",
            "Ask for 5 to 10 copies of the death certificate. Banks, insurance and offices each keep one." to
                "मृत्यु प्रमाणपत्र की 5 से 10 प्रतियाँ माँगें। बैंक, बीमा और दफ़्तर हर एक प्रति अपने पास रखते हैं।",
            "After 21 days there is a small late fee; after 30 days an affidavit is also needed; after a year, a magistrate's order." to
                "21 दिनों के बाद थोड़ा विलंब शुल्क लगता है; 30 दिनों के बाद शपथपत्र भी चाहिए; एक साल के बाद मजिस्ट्रेट का आदेश।",
        )),
        AfterDeathStep("weeks", "In the weeks after", "आने वाले हफ़्तों में", listOf(
            "Bank accounts: the nominee or family gives the bank the death certificate and a claim form. Banks are asked to settle within 15 days." to
                "बैंक खाते: नॉमिनी या परिवार बैंक को मृत्यु प्रमाणपत्र और दावा फ़ॉर्म देते हैं। बैंकों को 15 दिनों के अंदर निपटाने को कहा गया है।",
            "Life insurance and pension: the claim form, the death certificate and the policy or pension papers. A spouse can usually start family pension with a letter to the bank." to
                "जीवन बीमा और पेंशन: दावा फ़ॉर्म, मृत्यु प्रमाणपत्र और पॉलिसी या पेंशन के कागज़। पति या पत्नी आम तौर पर बैंक को एक पत्र देकर पारिवारिक पेंशन शुरू कर सकते हैं।",
            "A legal heir certificate comes from the Tahsildar or revenue office. Some claims need a succession certificate from a court instead." to
                "कानूनी वारिस प्रमाणपत्र तहसीलदार या राजस्व कार्यालय से मिलता है। कुछ दावों के लिए अदालत से उत्तराधिकार प्रमाणपत्र चाहिए होता है।",
            "Their Aadhaar can be marked on myAadhaar using the death registration number." to "मृत्यु पंजीकरण संख्या से myAadhaar पर उनका आधार दर्ज कराया जा सकता है।",
        )),
        AfterDeathStep("you", "Support for the family", "परिवार के लिए सहायता", listOf(
            "Your palliative team can stay in touch with your family after the death. You can call Tele-MANAS on 14416, free, any time, in your language." to
                "मृत्यु के बाद भी आपकी पैलिएटिव टीम आपके परिवार से जुड़ी रह सकती है। आप किसी भी समय, मुफ़्त, अपनी भाषा में Tele-MANAS को 14416 पर कॉल कर सकते हैं।",
        )),
    )

    val stateNote = "Rules differ a little from state to state. Your palliative team or local municipal office can tell you what applies where you live." to
        "नियम हर राज्य में थोड़े अलग होते हैं। आपकी पैलिएटिव टीम या स्थानीय नगर कार्यालय बता सकते हैं कि आपके यहाँ क्या लागू है।"

    val sources = listOf(
        "Registration of Births and Deaths Act 1969, ss. 8 and 13; 2023 amendment (PRS India summary)",
        "MCCD Form 4A (non-institutional deaths)",
        "Pallium India: How to deal with death at home",
        "Civil Registration System, dc.crsorgi.gov.in",
        "RBI: settlement of claims of deceased depositors",
        "UIDAI: reporting a death on myAadhaar",
        "AIIMS Nagpur eye bank FAQs",
        "Ministry of Health: Tele-MANAS 14416",
    )
}

/** The quiet row at the bottom of the SOS sheet. Muted on purpose: it is there when needed, never loud. */
@Composable fun AfterDeathEntry(onOpen: () -> Unit) {
    val t = careText()
    Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("sos-after-death")) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Spa, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t(AfterDeathGuide.entry.first, AfterDeathGuide.entry.second), fontWeight = FontWeight.SemiBold, color = Ink)
                Text(t(AfterDeathGuide.entrySub.first, AfterDeathGuide.entrySub.second),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The guide itself: one calm page of folded steps; only the first is open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AfterDeathGuideSheet(onClose: () -> Unit) {
    val t = careText(); val context = LocalContext.current
    SaharaScreenSheet(onDismissRequest = onClose, containerColor = Paper) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp).testTag("after-death-guide")) {
            TextButton(onClick = onClose, modifier = Modifier.testTag("after-death-close")) { Text(t("Close", "बंद करें")) }
            Spacer(Modifier.height(8.dp))
            Text(t(AfterDeathGuide.opening.first, AfterDeathGuide.opening.second), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(t(AfterDeathGuide.reassurance.first, AfterDeathGuide.reassurance.second), style = MaterialTheme.typography.bodyLarge, color = Ink)
            Spacer(Modifier.height(20.dp))
            AfterDeathGuide.steps.forEachIndexed { index, step ->
                var open by rememberSaveable(step.id) { mutableStateOf(index == 0) }
                Surface(shape = RoundedCornerShape(16.dp), color = if (open) SaharaTone.Teal.wash else Paper, border = BorderStroke(1.dp, DoctorHairline),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).testTag("after-death-${step.id}")) {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", fontWeight = FontWeight.Bold, color = SaharaTone.Teal.ink, modifier = Modifier.width(24.dp))
                            Text(t(step.title, step.titleHi), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Ink, modifier = Modifier.weight(1f))
                            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (open) Column(Modifier.padding(start = 40.dp, end = 16.dp, bottom = 16.dp)) {
                            step.points.forEach { (en, hi) ->
                                Text(t(en, hi), style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            when (step.id) {
                                "check" -> CallButton(t("Call 112", "112 पर कॉल करें"), AfterDeathGuide.EMERGENCY) { context.startActivity(it) }
                                "you" -> CallButton(t("Call Tele-MANAS 14416", "Tele-MANAS 14416 पर कॉल करें"), AfterDeathGuide.TELE_MANAS) { context.startActivity(it) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(t(AfterDeathGuide.stateNote.first, AfterDeathGuide.stateNote.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(t("Sources: ", "स्रोत: ") + AfterDeathGuide.sources.joinToString("; "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Opens the dialler only; the app never places a call. */
@Composable private fun CallButton(label: String, number: String, start: (Intent) -> Unit) {
    OutlinedButton(onClick = { runCatching { start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) } }, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.heightIn(min = 48.dp).testTag("after-death-call-$number")) {
        Icon(Icons.Default.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label)
    }
}
