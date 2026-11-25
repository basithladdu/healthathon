package `in`.wedevit.care

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Pictures instead of words for medicines (clinical team, point 6): a sun for Morning, a full bright sun for Afternoon, a sunset
 * for Evening and a moon for Night, the same everywhere a dose time shows (Today, Medicines, History, Reminders, the time chips,
 * the alarm); a large green tick for Taken and a red cross for Not taken.
 */

/** The picture for a part of the day: sun, bright sun, sunset, moon (the same icons Simple Mode uses). */
internal fun doseSlotIcon(slot: DoseSlot): ImageVector = when (slot) {
    DoseSlot.MORNING -> Icons.Default.WbSunny
    DoseSlot.AFTERNOON -> Icons.Default.LightMode
    DoseSlot.EVENING -> Icons.Default.WbTwilight
    DoseSlot.NIGHT -> Icons.Default.NightsStay
}

/** Warm gold for the day, a deeper ochre for the evening, indigo for the night. */
internal fun doseSlotTone(slot: DoseSlot): SaharaTone = when (slot) {
    DoseSlot.MORNING, DoseSlot.AFTERNOON -> SaharaTone.Gold
    DoseSlot.EVENING -> SaharaTone.Marigold
    DoseSlot.NIGHT -> SaharaTone.Indigo
}

/** "Morning", "Afternoon", "Evening", "Night" in the reader's language. */
@Composable internal fun doseSlotWord(slot: DoseSlot): String { val t = careText(); return t(slot.english, slot.hindi) }

/** The part-of-day picture alone, in its tone ([size] dp). TalkBack reads the part of the day. */
@Composable internal fun DoseSlotIcon(slot: DoseSlot, size: Int = 20, modifier: Modifier = Modifier, describe: Boolean = true) {
    val word = doseSlotWord(slot)
    Icon(doseSlotIcon(slot), if (describe) word else null, modifier.size(size.dp).testTag("dose-slot-${slot.name.lowercase()}"), tint = doseSlotTone(slot).ink)
}

/** The picture on a soft round tile, for headings ("☀ Morning · 08:00 · 3 medicines"). */
@Composable internal fun DoseSlotBadge(slot: DoseSlot, size: Int = 32, modifier: Modifier = Modifier) {
    val tone = doseSlotTone(slot)
    Surface(shape = CircleShape, color = tone.wash, modifier = modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) { DoseSlotIcon(slot, (size * 0.66f).toInt(), describe = false) }
    }
}

/** Every part of the day in [times], in day order, as pictures with their words: "☀ Morning  🌙 Night". */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable internal fun DoseSlotLine(times: List<String>, modifier: Modifier = Modifier, iconSize: Int = 18, fontSize: Int = 14) {
    val slots = times.map(MedicineDoseRules::slot).distinct().sortedBy { it.ordinal }
    if (slots.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        slots.forEach { slot ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                DoseSlotIcon(slot, iconSize, describe = false); Spacer(Modifier.width(4.dp))
                Text(doseSlotWord(slot), fontSize = fontSize.sp, color = DoctorCardSub, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * A dose's state as a picture big enough to read without words: a green circle with a white tick (Taken), a red circle with a
 * white cross (Not taken), a grey dash (Skipped). Before it is marked, the part-of-day picture of [time] (or nothing when untimed).
 * It pops in when the state changes ([SaharaMotion]; still when animations are off).
 */
@Composable internal fun DoseStateMark(state: MedicineDoseState, size: Int = 36, time: String = "", modifier: Modifier = Modifier) {
    val word = doseStateWord(state)
    Box(modifier.size(size.dp).semantics { contentDescription = word }, contentAlignment = Alignment.Center) {
        AnimatedContent(state, transitionSpec = {
            (fadeIn(SaharaMotion.tween(SaharaMotion.SAVED_MS)) + scaleIn(SaharaMotion.tween(SaharaMotion.SAVED_MS, easing = SaharaMotion.Emphasized), initialScale = 0.4f)) togetherWith fadeOut(SaharaMotion.tween(SaharaMotion.EXIT_MS))
        }, label = "dose-state-mark") { shown ->
            when (shown) {
                MedicineDoseState.TAKEN -> Surface(shape = CircleShape, color = SaharaTone.Success.solid, modifier = Modifier.size(size.dp).testTag("dose-mark-taken")) {
                    Box(contentAlignment = Alignment.Center) { SaharaDrawnCheck(Modifier.size((size * 0.62f).dp), SaharaTone.Success.onSolid, (size / 12f).coerceAtLeast(2f).dp) }
                }
                MedicineDoseState.MISSED -> Surface(shape = CircleShape, color = SaharaTone.Error.solid, modifier = Modifier.size(size.dp).testTag("dose-mark-not-taken")) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, null, Modifier.size((size * 0.66f).dp), tint = SaharaTone.Error.onSolid) }
                }
                MedicineDoseState.SKIPPED -> Surface(shape = CircleShape, color = SaharaTone.Neutral.solid, modifier = Modifier.size(size.dp).testTag("dose-mark-skipped")) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, null, Modifier.size((size * 0.66f).dp), tint = SaharaTone.Neutral.onSolid) }
                }
                MedicineDoseState.PENDING -> if (time.isNotBlank()) Surface(shape = CircleShape, color = Paper, border = BorderStroke(1.5.dp, SaharaSection.Medicines.solid), modifier = Modifier.size(size.dp)) {
                    Box(contentAlignment = Alignment.Center) { DoseSlotIcon(MedicineDoseRules.slot(time), (size * 0.6f).toInt(), describe = false) }
                } else Surface(shape = CircleShape, color = Paper, border = BorderStroke(1.5.dp, SaharaSection.Medicines.solid), modifier = Modifier.size(size.dp)) {}
            }
        }
    }
}

/** A strip photo with a big state mark on its corner: the picture the patient matches, and whether it was taken. */
@Composable internal fun StripWithMark(photo: java.io.File?, size: Int, state: MedicineDoseState?, modifier: Modifier = Modifier) {
    Box(modifier) {
        StripThumb(photo, size)
        if (state != null && state != MedicineDoseState.PENDING) DoseStateMark(state, (size * 0.42f).toInt().coerceIn(22, 44), modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
    }
}

/** "Rings as an alarm": a small bell tag on a medicine marked important (point 10). */
@Composable internal fun MedicineAlarmTag(modifier: Modifier = Modifier) {
    val t = careText()
    Surface(shape = RoundedCornerShape(50), color = SaharaTone.Marigold.wash, modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Alarm, null, Modifier.size(16.dp), tint = SaharaTone.Marigold.ink); Spacer(Modifier.width(4.dp))
            Text(t("Rings as an alarm", "अलार्म की तरह बजेगी"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SaharaTone.Marigold.ink, maxLines = 2)
        }
    }
}

/**
 * "Important: ring like an alarm" with a switch (point 10). [on] is the current choice; morphine and other opioids start on.
 * The whole row is the touch target (56 dp or taller as the text wraps).
 */
@Composable internal fun MedicineAlarmSwitch(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = careText(); val tick = rememberSaharaTick()
    Surface(shape = RoundedCornerShape(16.dp), color = if (on) SaharaTone.Marigold.wash else Paper, border = BorderStroke(1.5.dp, if (on) SaharaTone.Marigold.solid else DoctorHairline),
        modifier = modifier.fillMaxWidth().toggleable(value = on, enabled = enabled, role = androidx.compose.ui.semantics.Role.Switch) { tick(); onChange(it) }) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Alarm, null, Modifier.size(28.dp), tint = if (on) SaharaTone.Marigold.ink else DoctorCardSub)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t("Important: ring like an alarm", "ज़रूरी: अलार्म की तरह बजे"), fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                Text(t("At each dose time it rings, says the medicine's name and shows its photo full screen.", "हर खुराक के समय घंटी बजेगी, दवा का नाम बोलेगी और उसकी फ़ोटो पूरी स्क्रीन पर दिखेगी।"), fontSize = 14.sp, lineHeight = 19.sp, color = DoctorCardSub)
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Switch(checked = on, onCheckedChange = null, enabled = enabled)
        }
    }
}

/** Rounded shape used by medicine photo cards on the alarm and history screens. */
internal val MedicinePhotoShape = RoundedCornerShape(20.dp)
