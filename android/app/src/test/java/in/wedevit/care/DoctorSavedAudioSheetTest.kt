package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class DoctorSavedAudioSheetTest {
    private fun row(id: String, label: String = "") = SavedAudioRow(id, "/audio/$id.m4a", 0L, label)

    @Test fun labelsFollowPositionOldestFirst() { assertEquals(listOf("Audio 1", "Audio 2", "Audio 3"), savedAudioLabels(listOf(row("a"), row("b"), row("c")))) }
    @Test fun explicitLabelWinsAndPositionStillCounts() { assertEquals(listOf("Audio 1", "Ward round", "Audio 3"), savedAudioLabels(listOf(row("a"), row("b", "Ward round"), row("c", "  ")))) }
    @Test fun emptyListHasNoLabels() { assertTrue(savedAudioLabels(emptyList()).isEmpty()) }
    @Test fun continueNeedsAtLeastOneSelected() { assertFalse(savedAudioContinueEnabled(emptySet())); assertTrue(savedAudioContinueEnabled(setOf("a"))); assertTrue(savedAudioContinueEnabled(setOf("a", "b"))) }
    @Test fun toggleAddsThenRemoves() { val on = savedAudioToggle(emptySet(), "a"); assertEquals(setOf("a"), on); assertEquals(setOf("a", "b"), savedAudioToggle(on, "b")); assertEquals(emptySet<String>(), savedAudioToggle(on, "a")) }
    @Test fun appendingARecordingKeepsEarlierLabels() { val two = listOf(row("a"), row("b")); assertEquals(savedAudioLabels(two), savedAudioLabels(two + row("c")).take(2)) }
    @Test fun handleShowsCountOnlyWhenPresent() { assertEquals("Saved audio", savedAudioHandleText(0)); assertEquals("Saved audio · 2", savedAudioHandleText(2)) }
    @Test fun stageStatusFollowsState() { assertEquals("Ready to record", doctorStageStatus(false, false, 0)); assertEquals("Saving…", doctorStageStatus(false, true, 0)); assertEquals("Recording 1:05", doctorStageStatus(true, false, 65)) }
    @Test fun timestampIsReadable() { val utc = ZoneId.of("UTC"); assertEquals("24 Oct 2026 · 11:12 AM", savedAudioFormatTime(1792840320000L, utc)); assertEquals("24 Oct 2026 · 4:05 PM", savedAudioFormatTime(1792857900000L, utc)) }
}
