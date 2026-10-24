package `in`.wedevit.care

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.UUID

class ConnectedDoctorPlannerTest {
    @Test fun separateAccountsKeepTheirLocalVisitsWithoutTouchingWalkthroughOrAccount() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val accounts = listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        fun prefsName(account: String) = "doctor-connected-test-planner-" + MessageDigest.getInstance("SHA-256").digest(account.toByteArray()).joinToString("") { "%02x".format(it) }
        val existingNames = listOf("doctor-demo", "doctor-workflow", "care-account-cghlkqeqiokjqdionrkc", "care-account-kjnhtakqzboayqorrlue")
        val before = existingNames.associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        try {
            val patient = UUID.randomUUID().toString()
            val visit = DoctorDemoAppointment(UUID.randomUUID().toString(), patient, 1_792_700_000_123L, 45, "Phone", "Scheduled", "Synthetic local appointment")
            val first = ConnectedDoctorPlanner(context, accounts[0])
            val second = ConnectedDoctorPlanner(context, accounts[1])
            assertTrue(first.visits().isEmpty())
            first.save(listOf(visit), "{\"synthetic-request\":\"accepted\"}")
            assertEquals(listOf(visit), ConnectedDoctorPlanner(context, accounts[0]).visits())
            assertTrue(second.visits().isEmpty())
            assertEquals("{}", second.requests())
            val changed = visit.copy(startMillis = visit.startMillis + 60000, status = "Cancelled")
            first.save(listOf(changed), first.requests())
            assertEquals(listOf(changed), first.visits())
            assertTrue(second.visits().isEmpty())
            existingNames.forEach { assertEquals(before[it], context.getSharedPreferences(it, Context.MODE_PRIVATE).all) }
        } finally {
            // Only unique synthetic test-account namespaces are removed.
            accounts.forEach { context.getSharedPreferences(prefsName(it), Context.MODE_PRIVATE).edit().clear().commit() }
        }
    }

    @Test fun invalidRequestJsonDoesNotPartiallyOverwriteSavedVisits() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val account = UUID.randomUUID().toString()
        val name = "doctor-connected-test-planner-" + MessageDigest.getInstance("SHA-256").digest(account.toByteArray()).joinToString("") { "%02x".format(it) }
        try {
            val store = ConnectedDoctorPlanner(context, account)
            val visit = DoctorDemoAppointment(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1_792_700_000_000L, 30, "In person")
            store.save(listOf(visit), "{}")
            assertThrows(Exception::class.java) { store.save(emptyList(), "not-json") }
            assertEquals(listOf(visit), store.visits())
            assertEquals("{}", store.requests())
        } finally { context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
    }
}
