package `in`.wedevit.care

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Today's redesign (owner, 26 Nov, after a health-app reference): a friendly header, a red SOS card that is easy to find,
// four quick actions, and a red SOS button that stays on screen once the card has scrolled away. Fonts are unchanged.

private val SosRedTop = Color(0xFFE53935)
private val SosRedBottom = Color(0xFFB71C1C)

/** The greeting, the date, a short headline and the Sahara mark on a soft circle (the reference's portrait). */
@Composable fun TodayHero(greeting: String, date: String, patient: Boolean, caringFor: String, onRefresh: (() -> Unit)?) {
    val t = careText()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, Modifier.a11yHeading().testTag("today-greeting"), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val accent = SaharaColors.Primary
            Text(buildAnnotatedString {
                if (patient) { append(t("Take care of", "अपना ख़याल रखें,") + "\n"); withStyle(SpanStyle(color = accent)) { append(t("yourself today.", "आज भी।")) } }
                else { append(t("Care for them,", "उनका ख़याल रखें,") + "\n"); withStyle(SpanStyle(color = accent)) { append(t("and for you.", "और अपना भी।")) } }
            }, fontFamily = SaharaTitleFont, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, null, Modifier.size(16.dp), tint = DoctorCardSub); Spacer(Modifier.width(6.dp))
                Text(date, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardSub, modifier = Modifier.testTag("today-date"))
            }
            if (caringFor.isNotBlank()) Surface(shape = RoundedCornerShape(50), color = SaharaApricot, modifier = Modifier.padding(top = 8.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FavoriteBorder, null, Modifier.size(16.dp), tint = RustText); Spacer(Modifier.width(6.dp))
                    Text(t("Caring for $caringFor", "$caringFor की देखभाल"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RustText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("today-caring-for"))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(SaharaColors.PrimaryWash), contentAlignment = Alignment.Center) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(SaharaColors.Surface), contentAlignment = Alignment.Center) {
                    Image(saharaImage(R.drawable.sahara_professional_logo, 40.dp), null, Modifier.size(40.dp), contentScale = ContentScale.Fit)
                }
            }
            if (onRefresh != null) TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = RustText)) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Refresh", "रिफ़्रेश करें"), fontSize = 14.sp)
            }
        }
    }
}

/** SOS as a bold red card (owner, 26 Nov: "red theme, easy to reach"): what it does in plain words and one white "Get help" pill. */
@Composable fun TodaySosCard(onSos: () -> Unit) {
    val t = careText()
    Surface(onClick = onSos, shape = RoundedCornerShape(24.dp), color = Color.Transparent, shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().testTag("today-sos").semantics { contentDescription = t("SOS, emergency help", "SOS, आपातकालीन मदद") }) {
        Box(Modifier.background(Brush.linearGradient(listOf(SosRedTop, SosRedBottom))).padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("SOS · Emergency help", "SOS · आपातकालीन मदद"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(t("Call an ambulance or show the emergency card", "एम्बुलेंस बुलाएँ या इमरजेंसी कार्ड दिखाएँ"), fontSize = 15.sp, lineHeight = 20.sp, color = Color.White.copy(alpha = .9f))
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Call, null, Modifier.size(24.dp), tint = SosRedTop)
                    }
                }
            }
        }
    }
}

/** A red SOS button that floats over Today once the SOS card has scrolled out of sight. */
@Composable fun TodaySosFloating(visible: Boolean, onSos: () -> Unit, modifier: Modifier = Modifier) {
    val t = careText()
    AnimatedVisibility(visible, modifier, enter = fadeIn() + scaleIn(initialScale = .8f), exit = fadeOut() + scaleOut(targetScale = .8f)) {
        ExtendedFloatingActionButton(onClick = onSos, containerColor = SosRedTop, contentColor = Color.White, shape = RoundedCornerShape(50),
            icon = { Icon(Icons.Default.Call, null) }, text = { Text(t("SOS", "SOS"), fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            modifier = Modifier.heightIn(min = 56.dp).testTag("today-sos-float").semantics { contentDescription = t("SOS, emergency help", "SOS, आपातकालीन मदद") })
    }
}

/** One quick action: an icon on a soft circle, a short name and a one-line hint (the reference's "Our services" tiles). */
private data class TodayQuick(val label: String, val hint: String, val icon: ImageVector, val tone: Color, val ink: Color, val tag: String, val onClick: () -> Unit)

/** Four everyday shortcuts with a "View all" link to every care tool. */
@Composable fun TodayQuickActions(onSymptom: () -> Unit, onMedicines: () -> Unit, onVisits: () -> Unit, onFindCare: () -> Unit, onAll: () -> Unit) {
    val t = careText()
    val tiles = listOf(
        TodayQuick(t("Symptoms", "लक्षण"), t("Note one", "लिखें"), Icons.Default.Healing, SaharaColors.ErrorWash, SosRedBottom, "today-quick-symptoms", onSymptom),
        TodayQuick(t("Medicines", "दवाइयाँ"), t("Doses", "खुराक"), Icons.Default.Medication, Color(0xFFF1E4F0), Color(0xFF7A3E73), "today-quick-medicines", onMedicines),
        TodayQuick(t("Visits", "मुलाक़ातें"), t("Calendar", "कैलेंडर"), Icons.Default.Event, SaharaColors.InfoWash, SaharaColors.Info, "today-quick-visits", onVisits),
        TodayQuick(t("Find care", "देखभाल खोजें"), t("Near you", "पास में"), Icons.Default.Place, SaharaColors.PrimaryWash, SaharaColors.Primary, "today-quick-find", onFindCare),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Quick actions", "जल्दी करें"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading())
            TextButton(onClick = onAll, modifier = Modifier.heightIn(min = 48.dp).testTag("today-quick-all"), colors = ButtonDefaults.textButtonColors(contentColor = SaharaColors.Primary)) {
                Text(t("View all", "सब देखें"), fontWeight = FontWeight.SemiBold); Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.forEach { q ->
                Surface(onClick = q.onClick, shape = RoundedCornerShape(18.dp), color = SaharaColors.Surface, border = BorderStroke(1.dp, DoctorHairline), shadowElevation = 1.dp,
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag(q.tag)) {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(q.tone), contentAlignment = Alignment.Center) { Icon(q.icon, null, Modifier.size(26.dp), tint = q.ink) }
                        Text(q.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 17.sp)
                        // Name only (26 Nov refinement): the grey hint line is dropped to keep the row compact.
                    }
                }
            }
        }
    }
}
