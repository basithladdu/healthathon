@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        } }
        // The sample label stays at the top so nobody takes these for real people.
        item { Surface(shape = RoundedCornerShape(50), color = SaharaSoftGold, contentColor = DoctorCardInk, modifier = Modifier.testTag("community-sample-label")) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = RustText); Spacer(Modifier.width(6.dp))
                Text(t("Sample: not real people", "नमूना: असली लोग नहीं"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        } }
        item { Column { Spacer(Modifier.height(8.dp)); SectionTitle(t("Support groups", "सहायता समूह")) } }
        // One surface with dividers: a coloured badge per group, the name, who it is for, and Join on the right.
        item { Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column {
            CareCommunitySample.groups.forEachIndexed { index, group ->
                val asked = group.id in tapped
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = DoctorHairline)
                Row(Modifier.fillMaxWidth().padding(16.dp).a11yCard(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = group.color, contentColor = DoctorCardInk, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, null, Modifier.size(22.dp)) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t(group.name, group.nameHindi), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = DoctorCardInk)
                        Text(t(group.forWhom, group.forWhomHindi), fontSize = 14.sp, color = DoctorCardSub)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (asked) OutlinedButton(onClick = {}, enabled = false, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("community-join-${group.id}")) { Text(t("Coming soon", "जल्द आ रहा है")) }
                    else OutlinedButton(onClick = { tapped = tapped + group.id }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("community-join-${group.id}")) { Text(t("Join", "जुड़ें"), color = RustText, fontWeight = FontWeight.SemiBold) }
                }
            }
        } } }
        item { Column { Spacer(Modifier.height(8.dp)); SectionTitle(t("Discussions", "बातचीत")) } }
        items(CareCommunitySample.posts, key = { "${it.groupId}:${it.author}" }) { post ->
            val group = CareCommunitySample.groups.first { it.id == post.groupId }
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(group.color)); Spacer(Modifier.width(8.dp))
                    Text(t(post.author, post.authorHindi), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                    Surface(shape = RoundedCornerShape(50), color = group.color, contentColor = DoctorCardInk) { Text(t(group.name, group.nameHindi), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Spacer(Modifier.height(8.dp))
                Text(t(post.text, post.textHindi), fontSize = 16.sp, lineHeight = 23.sp, color = DoctorCardInk)
                Spacer(Modifier.height(8.dp))
                Text(t("Sample post", "नमूना पोस्ट"), style = MaterialTheme.typography.labelMedium, color = DoctorCardSub)
            }
        }
        item { InfoDisclosure(t("About this page", "इस पेज के बारे में")) {
            Text(t("This is an example of how families could find each other. The groups and posts are written by the Sahara team. They are not real people.", "यह एक उदाहरण है कि परिवार एक-दूसरे को कैसे ढूँढ सकते हैं। समूह और पोस्ट सहारा टीम ने लिखे हैं। ये असली लोग नहीं हैं।"), style = MaterialTheme.typography.bodySmall)
            Text(t("Real groups will be added only after your doctors check them.", "असली समूह आपके डॉक्टरों की जाँच के बाद ही जोड़े जाएँगे।"), style = MaterialTheme.typography.bodySmall)
            Text(t("If you need help now, talk to your care team.", "अभी मदद चाहिए तो अपनी देखभाल टीम से बात करें।"), style = MaterialTheme.typography.bodySmall)
        } }
    }
}
