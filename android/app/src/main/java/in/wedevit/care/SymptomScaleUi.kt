@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * Patient-facing pictures for the symptom diary and distress screens (points 5, 7 and 8 of the clinical team's notes for older
 * and non-reading patients). Rules and the word↔number mapping are in SymptomScaleRules.kt. Text here is 18sp or more, and
 * nothing has a fixed height, so the screens work at the phone's largest font size.
 */

/** A large speaker button that reads [text] aloud with the phone's own voices (offline). */
@Composable internal fun SymptomListenButton(speaker: SimpleSpeaker, text: (CareLang) -> String, tag: String, modifier: Modifier = Modifier) {
    val t = careText(); val tick = rememberSaharaTick()
    FilledTonalIconButton(onClick = { tick(); speaker.say(text) }, modifier = modifier.size(56.dp).saharaPressBounce().testTag(tag),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = SaharaTone.Info.wash, contentColor = SaharaTone.Info.ink)) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, t("Read aloud", "पढ़कर सुनाएँ"), Modifier.size(30.dp))
    }
}

/** The colour of one face's tile: the chart bands' green, amber and red, so a face matches the trend graph's colour. */
private fun levelColor(level: SymptomScaleRules.Level): Color = ChartBands.color(level.rating)

/**
 * The patient's scale: four large faces with a word under each (None, A little, A lot, Worst; or the well-being words).
 * A tap saves the matching 0-10 number ([SymptomScaleRules.rating]) through [onRate] and says the word aloud; a saved number
 * from anyone lights the nearest face. [rating] is -2 when not answered yet, -1 for "Not sure".
 */
@Composable internal fun PatientFaceScale(rating: Int, scale: String, question: String, onRate: (Int) -> Unit, modifier: Modifier = Modifier,
    speaker: SimpleSpeaker? = null, tagPrefix: String = "symptom-face", onUnrated: (() -> Unit)? = null) {
    val t = careText(); val hindi = careHindi()
    val chosen = rating.takeIf { it in 0..10 }?.let(SymptomScaleRules::fromRating)
    val oneColumn = LocalDensity.current.fontScale > 1.3f
    Column(modifier.fillMaxWidth().testTag(tagPrefix), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(question, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, style = SymptomWords, modifier = Modifier.weight(1f).a11yHeading())
            if (speaker != null) SymptomListenButton(speaker, { lang -> SymptomScaleRules.spoken(question, scale, lang == CareLang.HI) }, "$tagPrefix-listen")
        }
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SymptomScaleRules.Level.entries.chunked(if (oneColumn) 1 else 2).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { level ->
                        val word = SymptomScaleRules.word(level, scale, hindi)
                        PatientFaceTile(level, word, chosen == level, "$tagPrefix-${level.key}", Modifier.weight(1f).fillMaxHeight()) {
                            onRate(SymptomScaleRules.rating(level)); speaker?.say { lang -> SymptomScaleRules.word(level, scale, lang == CareLang.HI) }
                        }
                    }
                }
            }
        }
        if (onUnrated != null) SymptomPill(rating == -1, t("Not sure", "पक्का नहीं"), "$tagPrefix-unsure", Modifier.fillMaxWidth()) { onUnrated() }
        // The conversion, said plainly: the care team reads the same answer as a number.
        AnimatedVisibility(rating in 0..10, enter = SaharaMotion.enter, exit = SaharaMotion.exit) {
            val n = rating.coerceIn(0, 10)
            Text(t("Your care team sees this as $n out of 10.", "आपकी देखभाल टीम इसे 10 में से $n देखती है।"), fontSize = 18.sp, lineHeight = 24.sp, color = DoctorCardSub,
                style = SymptomWords, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("$tagPrefix-number"))
        }
    }
}

/** One face tile: the face, its word in large type, a coloured edge, and a tick when chosen. */
@Composable private fun PatientFaceTile(level: SymptomScaleRules.Level, word: String, selected: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val c = levelColor(level); val shape = RoundedCornerShape(20.dp)
    val fill by animateColorAsState(if (selected) lerp(Paper, c, .18f) else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "face fill")
    val edge by animateColorAsState(if (selected) c else c.copy(alpha = .45f), SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "face edge")
    val tick = rememberSaharaTick()
    Box(modifier.heightIn(min = 132.dp).saharaPressBounce().saharaSelectBounce(selected, .05f).clip(shape).background(fill, shape).border(if (selected) 3.dp else 1.5.dp, edge, shape)
        .selectable(selected, role = Role.RadioButton, onClick = { if (!selected) tick(); onClick() }).semantics(mergeDescendants = true) { contentDescription = word }.testTag(tag)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CareMoodFace(level.face, Modifier.size(64.dp).clearAndSetSemantics {})
            Text(word, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = DoctorCardInk, textAlign = TextAlign.Center, style = SymptomWords)
        }
        AnimatedVisibility(selected, enter = SaharaMotion.enter, exit = SaharaMotion.exit, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Box(Modifier.size(30.dp).background(c, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = readableOn(c)) }
        }
    }
}

/** A saved rating as a patient reads it: a small face and the word ("A lot"), or "Not rated". */
@Composable internal fun PatientScoreBadge(rating: Int?, scale: String = SymptomDiaryRules.PERSONAL_SCALE, faceSize: Dp = 26.dp, tag: String = "symptom-card-badge") {
    val hindi = careHindi()
    val shape = RoundedCornerShape(50)
    if (rating == null) {
        Text(careTr(hindi, "Not rated", "अंक नहीं बताया"), color = DoctorCardSub, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag(tag).border(1.dp, DoctorHairline, shape).padding(horizontal = 12.dp, vertical = 8.dp))
        return
    }
    val level = SymptomScaleRules.fromRating(rating); val c = levelColor(level)
    Row(Modifier.testTag(tag).background(lerp(Paper, c, .16f), shape).border(1.5.dp, c, shape).padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CareMoodFace(level.face, Modifier.size(faceSize).clearAndSetSemantics {})
        Text(SymptomScaleRules.word(level, scale, hindi), color = DoctorCardInk, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/** "Faces" / "Numbers 0–10": who is helping can switch the scale; the answer is saved as the same number either way. */
@Composable internal fun SymptomScaleSwitch(pictures: Boolean, onChange: (Boolean) -> Unit, tag: String = "symptom-scale-switch") {
    val t = careText(); val tick = rememberSaharaTick()
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag(tag)) {
        SegmentedButton(selected = pictures, onClick = { if (!pictures) tick(); onChange(true) }, shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-faces")) { Text(t("Faces", "चेहरे"), fontSize = 16.sp) }
        SegmentedButton(selected = !pictures, onClick = { if (pictures) tick(); onChange(false) }, shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-numbers")) { Text(t("Numbers 0–10", "अंक 0–10"), fontSize = 16.sp) }
    }
}

// ---------------------------------------------------------------- times are tapped, never typed

internal fun dayPartIcon(part: DoseSlot): ImageVector = when (part) {
    DoseSlot.MORNING -> Icons.Default.WbSunny
    DoseSlot.AFTERNOON -> Icons.Default.LightMode
    DoseSlot.EVENING -> Icons.Default.WbTwilight
    DoseSlot.NIGHT -> Icons.Default.NightsStay
}

internal fun dayPartTone(part: DoseSlot): SaharaTone = when (part) {
    DoseSlot.MORNING -> SaharaTone.Marigold; DoseSlot.AFTERNOON -> SaharaTone.Warning; DoseSlot.EVENING -> SaharaTone.Rose; DoseSlot.NIGHT -> SaharaTone.Indigo
}

/** One big tap target with a picture and a word, for days and parts of the day. */
@Composable private fun SymptomPictureChoice(selected: Boolean, label: String, icon: ImageVector, tone: SaharaTone, tag: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val fill by animateColorAsState(if (selected) tone.wash else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "when fill")
    val edge by animateColorAsState(if (selected) tone.solid else DoctorCardSub.copy(alpha = .4f), SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "when edge")
    val tick = rememberSaharaTick()
    Column(modifier.heightIn(min = 72.dp).saharaSelectBounce(selected, .05f).clip(shape).background(fill, shape).border(if (selected) 2.5.dp else 1.dp, edge, shape)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = { if (!selected) tick(); onClick() }).semantics(mergeDescendants = true) {}.testTag(tag)
        .padding(horizontal = 6.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        val alpha = if (enabled) 1f else .38f
        Icon(icon, null, Modifier.size(30.dp), tint = tone.ink.copy(alpha = alpha))
        Text(label, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = DoctorCardInk.copy(alpha = alpha), textAlign = TextAlign.Center, style = SymptomWords)
    }
}

/**
 * When it happened, with no typing: Today / Yesterday / Earlier (a calendar), then Morning / Afternoon / Evening / Night with
 * sun and moon pictures. A part of today that has not begun cannot be chosen, and a tapped part is never later than now.
 * Family and the care team also get the exact-time wheel ([CareTimeButton]); patients see the pictures only.
 * [date] is "yyyy-MM-dd" and [time] "HH:mm"; [onChange] receives both.
 */
@Composable internal fun SymptomWhenPicker(date: String, time: String, exactTime: Boolean, onChange: (String, String) -> Unit, tagPrefix: String = "symptom-when") {
    val t = careText(); val lang = careLanguage()
    val now = LocalDateTime.now(); val today = now.toLocalDate()
    val day = runCatching { LocalDate.parse(date) }.getOrDefault(today)
    val which = SymptomScaleRules.dayOf(day, today)
    var earlier by rememberSaveable { mutableStateOf(which == SymptomScaleRules.Day.EARLIER) }
    val part = careClock(time)?.let(SymptomScaleRules::partOf)
    val oneColumnDays = LocalDensity.current.fontScale > 1.3f
    fun pickDay(d: LocalDate) = onChange(d.toString(), SymptomScaleRules.clamp(d, time, now))
    Column(Modifier.fillMaxWidth().testTag(tagPrefix), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val days = listOf(
            Triple(SymptomScaleRules.Day.TODAY, t("Today", "आज"), Icons.Default.Today),
            Triple(SymptomScaleRules.Day.YESTERDAY, t("Yesterday", "कल"), Icons.Default.History),
            Triple(SymptomScaleRules.Day.EARLIER, t("Earlier", "पहले"), Icons.Default.CalendarMonth))
        (if (oneColumnDays) days.chunked(1) else listOf(days)).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (d, label, icon) ->
                    val on = if (d == SymptomScaleRules.Day.EARLIER) earlier || which == d else !earlier && which == d
                    SymptomPictureChoice(on, label, icon, SaharaTone.Teal, "$tagPrefix-day-${d.name.lowercase()}", Modifier.weight(1f).fillMaxHeight()) {
                        when (d) {
                            SymptomScaleRules.Day.TODAY -> { earlier = false; pickDay(today) }
                            SymptomScaleRules.Day.YESTERDAY -> { earlier = false; pickDay(today.minusDays(1)) }
                            SymptomScaleRules.Day.EARLIER -> earlier = true
                        }
                    }
                }
            }
        }
        // An earlier day is chosen on the calendar (no typing); the latest day it offers is today.
        AnimatedVisibility(earlier || which == SymptomScaleRules.Day.EARLIER, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
            CareDateButton(if (which == SymptomScaleRules.Day.EARLIER) careEpoch(day) else 0L, t("Choose a day", "दिन चुनें"), { pickDay(careDay(it)) }, latest = today)
        }
        val parts = DoseSlot.entries
        (if (oneColumnDays) parts.chunked(2) else listOf(parts)).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    val allowed = SymptomScaleRules.partAllowed(p, day, now)
                    SymptomPictureChoice(part == p, careTr(lang, p.english, p.hindi), dayPartIcon(p), dayPartTone(p), "$tagPrefix-part-${p.name.lowercase()}", Modifier.weight(1f).fillMaxHeight(), enabled = allowed) {
                        onChange(day.toString(), SymptomScaleRules.timeFor(p, day, now))
                    }
                }
            }
        }
        if (exactTime) CareTimeButton(time, { picked -> onChange(day.toString(), picked) }, t("Exact time (optional)", "ठीक समय (वैकल्पिक)"), Modifier.testTag("$tagPrefix-exact"))
    }
}

/**
 * "When did it start?" without typing: Today / Yesterday (counted from the note's own day), or Earlier on the calendar.
 * [startedOn] is "" when not given; [onChange] gets "" to clear it.
 */
@Composable internal fun SymptomStartedPicker(startedOn: String, observedDate: String, onChange: (String) -> Unit, tagPrefix: String = "symptom-started") {
    val t = careText()
    val observed = runCatching { LocalDate.parse(observedDate) }.getOrDefault(LocalDate.now())
    val started = runCatching { LocalDate.parse(startedOn) }.getOrNull()
    val which = started?.let { SymptomScaleRules.dayOf(it, observed) }
    var earlier by rememberSaveable { mutableStateOf(which == SymptomScaleRules.Day.EARLIER) }
    val oneColumn = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxWidth().testTag(tagPrefix), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("When did it start? (optional)", "यह कब शुरू हुआ? (वैकल्पिक)"), fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, style = SymptomWords)
        val days = listOf(
            Triple(SymptomScaleRules.Day.TODAY, t("Today", "आज"), Icons.Default.Today),
            Triple(SymptomScaleRules.Day.YESTERDAY, t("Yesterday", "कल"), Icons.Default.History),
            Triple(SymptomScaleRules.Day.EARLIER, t("Earlier", "पहले"), Icons.Default.CalendarMonth))
        (if (oneColumn) days.chunked(1) else listOf(days)).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (d, label, icon) ->
                    val on = if (d == SymptomScaleRules.Day.EARLIER) earlier || which == d else !earlier && which == d
                    SymptomPictureChoice(on, label, icon, SaharaTone.Plum, "$tagPrefix-${d.name.lowercase()}", Modifier.weight(1f).fillMaxHeight()) {
                        if (d == SymptomScaleRules.Day.EARLIER) earlier = true
                        else { earlier = false; onChange(if (which == d) "" else SymptomScaleRules.startedOn(d, observed).orEmpty()) }
                    }
                }
            }
        }
        AnimatedVisibility(earlier || which == SymptomScaleRules.Day.EARLIER, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
            CareDateButton(if (which == SymptomScaleRules.Day.EARLIER && started != null) careEpoch(started) else 0L, t("Choose a day", "दिन चुनें"), { onChange(careDay(it).toString()) }, latest = observed)
        }
        AnimatedVisibility(started != null, enter = SaharaMotion.enter, exit = SaharaMotion.exit) {
            TextButton(onClick = { earlier = false; onChange("") }, modifier = Modifier.heightIn(min = 48.dp).testTag("$tagPrefix-clear")) { Text(t("Clear start date", "शुरू होने की तारीख हटाएँ"), fontSize = 16.sp) }
        }
    }
}
