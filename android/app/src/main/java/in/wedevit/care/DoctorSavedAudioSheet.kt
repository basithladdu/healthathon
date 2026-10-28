package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One saved recording. [label] may be blank; the list is supplied oldest-first and "Audio N" is derived from position. */
data class SavedAudioRow(val id: String, val path: String, val recordedAt: Long, val label: String = "")

const val SavedAudioSeekMs = 10_000L

/** "Audio N" by position (1-based, oldest first); a non-blank explicit label wins. */
fun savedAudioLabels(rows: List<SavedAudioRow>): List<String> = rows.mapIndexed { i, r -> r.label.trim().ifBlank { "Audio ${i + 1}" } }
fun savedAudioContinueEnabled(selected: Set<String>): Boolean = selected.isNotEmpty()
fun savedAudioToggle(selected: Set<String>, id: String): Set<String> = if (id in selected) selected - id else selected + id
fun savedAudioHandleText(count: Int): String = if (count <= 0) "Saved audio" else "Saved audio · $count"
fun savedAudioFormatTime(at: Long, zone: ZoneId = ZoneId.systemDefault()): String = DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a", Locale.ENGLISH).withZone(zone).format(Instant.ofEpochMilli(at))

/**
 * Expanded "Saved Audio" page (the body of the peek sheet). Playback is owned by the caller: [onPlay] starts a row, [onPause] pauses, [onSeek] receives a RELATIVE delta in ms (-10000 / +10000).
 * [nowPlaying] is the id of the current row (kept in the player bar even when paused); null hides the bar. Audio stays on the device; nothing here uploads or transcribes.
 */
@Composable fun DoctorSavedAudioPage(rows: List<SavedAudioRow>, selected: Set<String>, onSelectedChange: (Set<String>) -> Unit, nowPlaying: String?, isPlaying: Boolean, onPlay: (SavedAudioRow) -> Unit, onPause: () -> Unit, onSeek: (Long) -> Unit, onContinue: () -> Unit, modifier: Modifier = Modifier) { val t = careText();
    val labels = savedAudioLabels(rows)
    val current = rows.indexOfFirst { it.id == nowPlaying }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).testTag("saved_audio_sheet")) {
        if (rows.isEmpty()) Surface(Modifier.fillMaxWidth().testTag("saved_audio_empty"), color = Color.White, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline)) { Text(t("No audio recorded yet. Drag this down and record to add one.", "अभी कोई ऑडियो रिकॉर्ड नहीं हुआ। इसे नीचे खींचें और रिकॉर्ड करके जोड़ें।"), fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk, modifier = Modifier.padding(16.dp)) }
        LazyColumn(Modifier.weight(1f).testTag("saved_audio_list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(rows.size, key = { rows[it].id }) { i ->
                val r = rows[i]; val checked = r.id in selected; val playing = r.id == nowPlaying
                Surface(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("saved_audio_row_${r.id}"), color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(if (playing) 2.dp else 1.dp, if (playing) DoctorStageTerracotta else DoctorHairline)) {
                    Row(Modifier.padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).semantics { role = Role.Checkbox }.clickable(role = Role.Checkbox) { onSelectedChange(savedAudioToggle(selected, r.id)) }.testTag("saved_audio_check_${r.id}"), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(if (checked) DoctorStageTerracotta else Color.White).border(2.dp, DoctorStageTerracotta, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) { if (checked) Icon(Icons.Default.Check, t("Selected", "चुना हुआ"), tint = Color.White, modifier = Modifier.size(18.dp)) }
                        }
                        Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button) { onPlay(r) }.padding(start = 6.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.Center) {
                            Text(labels[i], fontSize = 16.sp, lineHeight = 22.sp, color = DoctorCardInk, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("saved_audio_label_${r.id}"))
                            Text(savedAudioFormatTime(r.recordedAt), fontSize = 12.sp, lineHeight = 16.sp, color = DoctorCardFaint, modifier = Modifier.testTag("saved_audio_time_${r.id}"))
                        }
                    }
                }
            }
        }
        if (current >= 0) {
            Spacer(Modifier.height(12.dp))
            Surface(Modifier.fillMaxWidth().testTag("saved_audio_player"), color = DoctorStageTerracotta, shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(labels[current], fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 8.dp).testTag("saved_audio_player_label"))
                    IconButton(onClick = { onSeek(-SavedAudioSeekMs) }, modifier = Modifier.size(48.dp).testTag("saved_audio_back10")) { Icon(Icons.Default.Replay10, t("Back 10 seconds", "10 सेकंड पीछे"), tint = Color.White) }
                    IconButton(onClick = { if (isPlaying) onPause() else rows.getOrNull(current)?.let(onPlay) }, modifier = Modifier.size(48.dp).testTag("saved_audio_play_pause")) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) t("Pause", "रोकें") else t("Play", "चलाएँ"), tint = Color.White, modifier = Modifier.size(32.dp)) }
                    IconButton(onClick = { onSeek(SavedAudioSeekMs) }, modifier = Modifier.size(48.dp).testTag("saved_audio_forward10")) { Icon(Icons.Default.Forward10, t("Forward 10 seconds", "10 सेकंड आगे"), tint = Color.White) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onContinue, enabled = savedAudioContinueEnabled(selected.intersect(rows.map { it.id }.toSet())), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("saved_audio_continue"), colors = ButtonDefaults.buttonColors(containerColor = DoctorStageTerracotta, contentColor = Color.White)) { Text(t("Continue to transcript →", "लिखाई पर आगे बढ़ें →"), fontSize = 16.sp) }
    }
}
