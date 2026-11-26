package `in`.wedevit.care

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One colour picture per symptom (res/drawable/ic_symptom_*.xml). Keys are the stored symptom names; older keys keep their own picture. */
@DrawableRes internal fun symptomIconRes(key: String): Int = when (key.trim()) {
    "Pain" -> R.drawable.ic_symptom_pain; "Tiredness" -> R.drawable.ic_symptom_tiredness; "Sleepiness" -> R.drawable.ic_symptom_sleepiness
    "Poor appetite" -> R.drawable.ic_symptom_appetite; "Dry mouth" -> R.drawable.ic_symptom_dry_mouth; "Breathlessness" -> R.drawable.ic_symptom_breathlessness
    "Constipation" -> R.drawable.ic_symptom_constipation; "Diarrhea" -> R.drawable.ic_symptom_diarrhea; "Nausea" -> R.drawable.ic_symptom_nausea
    "Vomiting" -> R.drawable.ic_symptom_vomiting; "Difficulty swallowing" -> R.drawable.ic_symptom_swallowing; "Cough" -> R.drawable.ic_symptom_cough
    "Bowel / bladder incontinence" -> R.drawable.ic_symptom_incontinence; "Mood" -> R.drawable.ic_symptom_mood
    "Sadness" -> R.drawable.ic_symptom_sadness; "Worry" -> R.drawable.ic_symptom_worry; "Weight loss" -> R.drawable.ic_symptom_weight_loss
    "Overall well-being" -> R.drawable.ic_symptom_wellbeing; "Sleep difficulty" -> R.drawable.ic_symptom_sleep_difficulty
    else -> R.drawable.ic_symptom_other
}

/** The main colour of each symptom picture, so tints and accents next to the icon match it. */
internal fun symptomColor(key: String): Color = when (key.trim()) {
    "Pain" -> Color(0xFFB5564A); "Tiredness" -> Color(0xFFB8774A); "Sleepiness" -> Color(0xFF7D6AA8)
    "Poor appetite" -> Color(0xFF3F7F77); "Dry mouth" -> Color(0xFFB06178); "Breathlessness" -> Color(0xFF4A8DA0)
    "Constipation" -> Color(0xFF8B6A4F); "Diarrhea" -> Color(0xFFB08D3A); "Nausea" -> Color(0xFF7C9A4F)
    "Vomiting" -> Color(0xFF65804A); "Difficulty swallowing" -> Color(0xFF8F6BA3); "Cough" -> Color(0xFF6F7F90)
    "Bowel / bladder incontinence" -> Color(0xFF4F83A8); "Mood", "Sadness" -> Color(0xFF6A84B5); "Worry" -> Color(0xFFB98443)
    "Weight loss" -> Color(0xFFA85F82); "Overall well-being" -> Color(0xFFC4A444); "Sleep difficulty" -> Color(0xFF5A5F96)
    else -> Color(0xFF78716C)
}

/** The symptom picture as a painter. Draw it with [Image] (or Icon with tint = Color.Unspecified) so its colours stay. */
@Composable internal fun symptomIcon(key: String): Painter = painterResource(symptomIconRes(key))

/** The symptom picture in full colour. Pass [contentDescription] only when no label sits next to it. */
@Composable internal fun SymptomIcon(key: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(symptomIcon(key), contentDescription, modifier)
}

/** Round badge: a soft wash of the symptom colour behind its picture. */
@Composable internal fun SymptomIconBadge(key: String, modifier: Modifier = Modifier, size: Dp = 40.dp, contentDescription: String? = null) {
    Box(modifier.size(size).clip(CircleShape).background(lerp(Paper, symptomColor(key), .14f)), contentAlignment = Alignment.Center) {
        SymptomIcon(key, Modifier.size(size * .72f), contentDescription)
    }
}

internal fun ratingColor(n: Int): Color = if (n <= 5) lerp(DoctorStatusGood, Color(0xFFB08D3A), n / 5f) else lerp(Color(0xFFB08D3A), Color(0xFF9A5444), (n - 5) / 5f)

/** A rounded section card with a short helper line; [accent] puts a coloured bar beside the title. */
@Composable internal fun SymptomSection(number: Int?, title: String, helper: String? = null, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (number != null) Box(Modifier.sizeIn(minWidth = 28.dp, minHeight = 28.dp).clip(CircleShape).background(DoctorAccent), contentAlignment = Alignment.Center) { Text(number.toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                else if (accent != null) Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(accent))
                Column(Modifier.weight(1f)) {
                    Text(title, color = DoctorCardInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp, style = SymptomWords, modifier = Modifier.a11yHeading())
                    if (helper != null) Text(helper, color = DoctorCardSub, fontSize = 15.sp, lineHeight = 20.sp, style = SymptomWords)
                }
            }
            content()
        }
    }
}

/** Text style that never splits a word across lines (no hyphen breaks; words wrap whole). */
internal val SymptomWords = TextStyle(lineBreak = LineBreak.Paragraph, hyphens = Hyphens.None)

/** The icon chip of a bento tile: a flat light tint of the symptom colour with a ring, the full-colour picture inside. */
@Composable internal fun SymptomIconChip(key: String, size: Dp = 44.dp) {
    val c = symptomColor(key)
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(lerp(Paper, c, .16f)).border(1.5.dp, c.copy(alpha = .45f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        SymptomIcon(key, Modifier.size(size * .7f))
    }
}

/**
 * Bento tile (AI-FIR launchpad grammar): white surface, coloured icon chip, label, a 5dp bottom bar in the symptom colour.
 * Selected: a 2dp ring and a tick in that colour. Presses scale slightly; colours animate (instant when motion is off).
 */
@Composable internal fun SymptomTile(selected: Boolean, label: String, key: String, tag: String, modifier: Modifier = Modifier, role: Role = Role.RadioButton, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp); val c = symptomColor(key)
    val interaction = remember { MutableInteractionSource() }
    val ring by animateColorAsState(if (selected) c else DoctorHairline, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "tile ring")
    val fill by animateColorAsState(if (selected) lerp(Paper, c, .12f) else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "tile fill")
    Column(modifier.heightIn(min = 64.dp).saharaPressBounce().testTag(tag).clip(shape).background(fill, shape).border(if (selected) 2.dp else 1.dp, ring, shape)
        .then(if (role == Role.RadioButton) Modifier.selectable(selected, interaction, ripple(), role = role, onClick = onClick) else Modifier.clickable(interaction, ripple(), role = role, onClick = onClick))) {
        // Icon above the name, so a long word such as "Breathlessness" gets the whole tile width and never breaks mid-word.
        Column(Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SymptomIconChip(key)
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(selected, enter = scaleIn(SaharaMotion.tween(SaharaMotion.SELECT_MS)) + fadeIn(SaharaMotion.tween(SaharaMotion.SELECT_MS)), exit = fadeOut(SaharaMotion.tween(SaharaMotion.EXIT_MS))) {
                    Box(Modifier.size(24.dp).background(c, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = readableOn(c)) }
                }
            }
            Text(label, Modifier.fillMaxWidth(), color = DoctorCardInk, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, style = SymptomWords)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(c))
    }
}

/** Pill option: solid fill with a check when selected, strong outline when not. [multi] switches the semantics to Checkbox. */
@Composable internal fun SymptomPill(selected: Boolean, label: String, tag: String?, modifier: Modifier = Modifier, multi: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val bg by animateColorAsState(if (selected) DoctorAccent else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "pill fill")
    val edge by animateColorAsState(if (selected) DoctorAccent else DoctorCardSub.copy(alpha = .45f), SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "pill edge")
    val ink by animateColorAsState(if (selected) Color.White else DoctorCardInk, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "pill ink")
    val tick = rememberSaharaTick()
    val base = modifier.heightIn(min = 48.dp).saharaSelectBounce(selected, .06f).let { if (tag != null) it.testTag(tag) else it }.clip(shape).background(bg, shape)
        .border(1.5.dp, edge, shape)
    val clickable = if (multi) base.toggleable(selected, role = Role.Checkbox, onValueChange = { tick(); onClick() }) else base.selectable(selected, role = Role.RadioButton, onClick = { tick(); onClick() })
    Row(clickable.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        // The tick grows in beside the words instead of popping.
        AnimatedVisibility(selected, enter = scaleIn(SaharaMotion.tween(SaharaMotion.SELECT_MS)) + fadeIn(SaharaMotion.tween(SaharaMotion.SELECT_MS)), exit = fadeOut(SaharaMotion.tween(SaharaMotion.EXIT_MS))) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = Color.White)
        }
        Text(label, color = ink, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, style = SymptomWords, modifier = Modifier.weight(1f, fill = false))
    }
}

/** One number on the 0–10 scale: its own colour, solid and slightly larger when chosen. */
@Composable internal fun RatingButton(number: Int, selected: Boolean, modifier: Modifier = Modifier, tag: String = "symptom-rating-$number", onClick: () -> Unit) {
    val c = ratingColor(number)
    val bg by animateColorAsState(if (selected) c else lerp(c, Paper, .82f), SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "rating fill")
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "rating scale")
    val edge by animateColorAsState(if (selected) DoctorCardInk else c.copy(alpha = .7f), SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "rating edge")
    val tick = rememberSaharaTick()
    Box(modifier.heightIn(min = 48.dp).graphicsLayer { scaleX = scale; scaleY = scale }.testTag(tag).clip(CircleShape).background(bg, CircleShape)
        .border(if (selected) 3.dp else 1.5.dp, edge, CircleShape)
        .selectable(selected, role = Role.RadioButton, onClick = { if (!selected) tick(); onClick() }), contentAlignment = Alignment.Center) {
        Text(number.toString(), color = if (selected) readableOn(c) else DoctorCardInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

/** Display the patient's actual number without inventing clinical severity bands; the number rolls when it changes. */
@Composable internal fun RatingReadout(rating: Int, hindi: Boolean, scale: String = SymptomDiaryRules.PERSONAL_SCALE) {
    Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedContent(rating, transitionSpec = { SaharaMotion.valueChange(targetState > initialState) }, label = "rating readout") { r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    r >= 0 -> {
                        Text(r.toString(), color = ratingColor(r), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 56.sp)
                        Text("/ 10", color = DoctorCardInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    }
                    r == -1 -> Text(careTr(hindi, "Not rated", "अंक नहीं बताया"), color = DoctorCardInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    else -> Text(careTr(hindi, "Tap a number below", "नीचे से एक अंक चुनें"), color = DoctorCardSub, fontSize = 16.sp)
                }
            }
        }
        if (scale == SymptomDiaryRules.WELLBEING_SCALE && rating in setOf(0, 10)) Text(if (rating == 0) {
            careTr(hindi, "Best possible well-being", "सबसे अच्छी तबीयत")
        } else careTr(hindi, "Worst possible well-being", "सबसे खराब तबीयत"), color = DoctorCardInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, style = SymptomWords, modifier = Modifier.weight(1f))
    }
}

/** Soft pastel of one symptom: [tint] card background, [accent] deeper same-hue border or bar, [ink] dark same-hue text (>= 4.5:1 on the tint). */
internal class SymptomPastel(val tint: Color, val accent: Color, val ink: Color)

private val pastelIndigo = SymptomPastel(Color(0xFFD9D8F5), Color(0xFF8C8FE0), Color(0xFF3E3A8F))
private val pastelOrange = SymptomPastel(Color(0xFFFADDBF), Color(0xFFE0A15F), Color(0xFF8A4B12))
private val pastelPink = SymptomPastel(Color(0xFFF1D2E1), Color(0xFFD98BB0), Color(0xFF7A2E4F))
private val pastelTeal = SymptomPastel(Color(0xFFCDE8DD), Color(0xFF6DBBA0), Color(0xFF1F6B53))
private val pastelPurple = SymptomPastel(Color(0xFFE2D4F4), Color(0xFFB093DD), Color(0xFF5A3A99))
private val pastelRed = SymptomPastel(Color(0xFFF8D2CD), Color(0xFFE29A93), Color(0xFF9A2F27))
private val pastelBlue = SymptomPastel(Color(0xFFD0E1F2), Color(0xFF86B0DA), Color(0xFF2D5A86))
private val pastelBrown = SymptomPastel(Color(0xFFE8DCC6), Color(0xFFC2A878), Color(0xFF6E5126))

/** One pastel per symptom (grid, chooser and the diary cards): indigo, orange, pink, teal, purple, red, blue, brown. */
internal fun symptomPastel(key: String): SymptomPastel = when (key.trim()) {
    "Pain" -> pastelRed; "Tiredness" -> pastelIndigo; "Sleepiness" -> pastelPurple
    "Poor appetite" -> pastelOrange; "Dry mouth" -> pastelPink; "Breathlessness" -> pastelBlue
    "Constipation" -> pastelBrown; "Diarrhea" -> pastelOrange; "Nausea" -> pastelTeal
    "Vomiting" -> pastelTeal; "Difficulty swallowing" -> pastelPurple; "Cough" -> pastelBlue
    "Bowel / bladder incontinence" -> pastelBrown; "Mood", "Sadness" -> pastelPink; "Worry" -> pastelIndigo
    "Overall well-being" -> pastelTeal; "Sleep difficulty" -> pastelIndigo; "Weight loss" -> pastelBrown
    else -> pastelIndigo
}

/** The deeper accent of the symptom's pastel (left colour bars on the diary cards). */
internal fun symptomTileColor(key: String): Color = symptomPastel(key).accent

/** A decorative emoji for each symptom (hidden from TalkBack; the name beside it carries the meaning). */
internal fun symptomEmoji(key: String): String = when (key.trim()) {
    "Pain" -> "🤕"; "Tiredness" -> "😴"; "Sleepiness" -> "💤"; "Poor appetite" -> "🍽️"; "Dry mouth" -> "👄"
    "Breathlessness" -> "😮‍💨"; "Constipation" -> "😖"; "Diarrhea" -> "💧"; "Nausea" -> "🤢"; "Vomiting" -> "🤮"
    "Difficulty swallowing" -> "😶"; "Cough" -> "😷"; "Bowel / bladder incontinence" -> "🚻"; "Mood", "Sadness" -> "😔"
    "Worry" -> "😟"; "Overall well-being" -> "🌿"; "Sleep difficulty" -> "🛌"; "Weight loss" -> "⚖️"; else -> "➕"
}

/** A line icon for each symptom (replaces the emoji on the tiles and cards). */
internal fun symptomVector(key: String): androidx.compose.ui.graphics.vector.ImageVector = when (key.trim()) {
    "Pain" -> androidx.compose.material.icons.Icons.Default.Bolt
    "Tiredness" -> androidx.compose.material.icons.Icons.Default.BatteryAlert
    "Sleepiness" -> androidx.compose.material.icons.Icons.Default.Bedtime
    "Poor appetite" -> androidx.compose.material.icons.Icons.Default.NoFood
    "Dry mouth" -> androidx.compose.material.icons.Icons.Default.WaterDrop
    "Breathlessness" -> androidx.compose.material.icons.Icons.Default.Air
    "Constipation" -> androidx.compose.material.icons.Icons.Default.Block
    "Diarrhea" -> androidx.compose.material.icons.Icons.Default.Opacity
    "Nausea", "Vomiting" -> androidx.compose.material.icons.Icons.Default.Sick
    "Difficulty swallowing" -> androidx.compose.material.icons.Icons.Default.Compress
    "Cough" -> androidx.compose.material.icons.Icons.Default.Masks
    "Bowel / bladder incontinence" -> androidx.compose.material.icons.Icons.Default.WaterDrop
    "Mood", "Sadness", "Worry" -> androidx.compose.material.icons.Icons.Default.Psychology
    "Overall well-being" -> androidx.compose.material.icons.Icons.Default.Favorite
    "Sleep difficulty" -> androidx.compose.material.icons.Icons.Default.Hotel
    "Weight loss" -> androidx.compose.material.icons.Icons.Default.MonitorWeight
    else -> androidx.compose.material.icons.Icons.Default.Add
}
