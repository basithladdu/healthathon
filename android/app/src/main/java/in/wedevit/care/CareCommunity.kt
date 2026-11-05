@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A sample support group. Not a real group: the kinds follow docs/research/SUPPORT_GROUPS_INDIA.md. */
internal data class CommunitySampleGroup(val id: String, val name: String, val nameHindi: String, val forWhom: String, val forWhomHindi: String, val color: Color)
/** A sample post. Written by the team as an example; the author is a role, never a name. */
internal data class CommunitySamplePost(val groupId: String, val author: String, val authorHindi: String, val text: String, val textHindi: String)

/**
 * Mock only (team meeting, 3 Nov 2026: "the community thing where they can find other people", "we should do a mock thing").
 * Nothing here is real: no real people, no real groups, no phone numbers, nothing is sent. Joining shows "Coming soon".
 * Real groups need the clinicians to confirm them first (see the research note).
 */
internal object CareCommunitySample {
    /** Kinds of group found in the research note: caregiver sharing sessions, parent support groups, childhood survivor peer groups, loss and bereavement. */
    val groups = listOf(
        CommunitySampleGroup("caregivers", "Family caregivers circle", "परिवार के देखभाल करने वालों का समूह", "For family members caring for someone at home", "घर पर किसी की देखभाल कर रहे परिवार वालों के लिए", Mint),
        CommunitySampleGroup("parents", "Parents of children with cancer", "कैंसर से जूझ रहे बच्चों के माता-पिता", "For parents and close family of a child in treatment", "इलाज करा रहे बच्चे के माता-पिता और करीबी परिवार के लिए", Peach),
        CommunitySampleGroup("survivors", "Young survivors", "युवा सर्वाइवर", "For young people who finished treatment", "इलाज पूरा कर चुके युवाओं के लिए", Sage),
        CommunitySampleGroup("loss", "Loss and grief", "बिछड़ने का दुख", "For families after the loss of someone close", "किसी करीबी को खोने के बाद परिवारों के लिए", SaharaLavender),
    )
    val posts = listOf(
        CommunitySamplePost("caregivers", "A caregiver", "एक देखभाल करने वाले", "How do you get a little rest when you care every day? A neighbour sitting in for one hour helped me.", "रोज़ देखभाल करते हुए आप थोड़ा आराम कैसे करते हैं? एक पड़ोसी के एक घंटे बैठने से मुझे मदद मिली।"),
        CommunitySamplePost("parents", "A parent", "एक माता-पिता", "We wrote our questions down before each visit. It made the talk with the doctor easier.", "हम हर मुलाक़ात से पहले अपने सवाल लिख लेते थे। इससे डॉक्टर से बात आसान हुई।"),
        CommunitySamplePost("survivors", "A young survivor", "एक युवा सर्वाइवर", "Going back to college felt strange at first. Talking to others who went through it helped.", "कॉलेज वापस जाना पहले अजीब लगा। जो इससे गुज़रे हैं उनसे बात करके मदद मिली।"),
        CommunitySamplePost("loss", "A family member", "परिवार के एक सदस्य", "Some days are still hard. It helps to know other families feel this too.", "कुछ दिन अब भी मुश्किल होते हैं। यह जानकर सहारा मिलता है कि दूसरे परिवार भी ऐसा महसूस करते हैं।"),
    )
}

@Composable fun CareCommunityScreen(onBack: () -> Unit) {
    val t = careText()
    var tapped by rememberSaveable { mutableStateOf(emptyList<String>()) }
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().testTag("community-screen"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) }
            Spacer(Modifier.width(4.dp))
            Heading(t("Community", "समुदाय"), 26)
            Spacer(Modifier.weight(1f))
            // Small tag so nobody takes the sample groups for real ones.
            Surface(shape = RoundedCornerShape(50), color = SaharaSoftGold, contentColor = DoctorCardInk, modifier = Modifier.testTag("community-sample-label")) {
                Text(t("Sample", "नमूना"), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } }
        // One card per group: icon, name, one line of who it is for, and one action.
        items(CareCommunitySample.groups, key = { it.id }) { group ->
            val asked = group.id in tapped
            Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp).a11yCard(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = group.color, contentColor = DoctorCardInk, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, null, Modifier.size(22.dp)) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t(group.name, group.nameHindi), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t(group.forWhom, group.forWhomHindi), fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (asked) OutlinedButton(onClick = {}, enabled = false, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("community-join-${group.id}")) { Text(t("Coming soon", "जल्द आ रहा है"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    else OutlinedButton(onClick = { tapped = tapped + group.id }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("community-join-${group.id}")) { Text(t("Join", "जुड़ें"), color = RustText, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
            }
        }
    }
}
