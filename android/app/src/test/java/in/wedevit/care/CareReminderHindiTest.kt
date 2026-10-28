package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CareReminderHindiTest {
    private val dir = listOf("src/main/java/in/wedevit/care", "app/src/main/java/in/wedevit/care", "android/app/src/main/java/in/wedevit/care").map(::File).first { it.isDirectory }

    @Test fun everyReminderCheckAndAlertTextHasHindi() {
        val rules = Regex("\\) \\{ \"([^\"]+)\" \\}").findAll(File(dir, "CareReminderRules.kt").readText()).map { it.groupValues[1] }.toSet()
        val alerts = Regex("reminderText\\(\"([^\"]+)\"\\)").findAll(File(dir, "CareReminders.kt").readText()).map { it.groupValues[1] }.toSet()
        assertTrue(rules.size >= 5); assertEquals(4, alerts.size)
        assertEquals(emptySet<String>(), rules + alerts - ReminderTextHindi.keys)
        ReminderTextHindi.values.forEach { assertTrue(it, HindiScan.hasHindi(it)) }
    }
}
