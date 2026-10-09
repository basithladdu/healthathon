package `in`.wedevit.care

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Test

class CareCloudTest {
    @Test fun authenticatedRecordsAndSharingRoundTrip() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val ownerEmail = args.getString("careTestOwner")
        val readerEmail = args.getString("careTestReader")
        val password = args.getString("careTestPassword")
        assumeNotNull(ownerEmail, readerEmail, password)
        val cloud = CareCloud(ApplicationProvider.getApplicationContext<Context>())
        if (cloud.session.value?.email in listOf(ownerEmail, readerEmail)) cloud.signOut()
        // Never replace a person's signed-in account during instrumentation.
        assumeTrue(cloud.session.value == null)
        val person = Person(name = "Cloud Test Person")
        try {
            cloud.signIn(ownerEmail!!, password!!)
            cloud.addPerson(person)
            val entry = cloud.saveEntry(CareItem(personId = person.id, kind = "task", title = "Bring questions"))
            assertEquals(1L, entry.revision)
            val changed = cloud.saveEntry(entry.copy(title = "Bring the prescription"))
            assertEquals(2L, changed.revision)
            assertTrue(runCatching { cloud.saveEntry(entry.copy(title = "Old change")) }.isFailure)
            val draft = NoteRules.organise(Conversation(person.id, source = "What matters: Time with family.\nStill to discuss: I have not decided about hospital care."))
            val note = NoteRules.publish(person, draft, "Test Doctor", true, 1)
            cloud.saveNote(note)
            val saved = cloud.load()
            assertEquals(changed, saved.entries.single { it.id == changed.id })
            assertEquals(note, saved.notes.single { it.id == note.id })
            val invite = cloud.invite(person.id, false)
            cloud.signOut()
            cloud.signIn(readerEmail!!, password)
            cloud.join(invite)
            val shared = cloud.load()
            assertTrue(shared.people.any { it.id == person.id })
            assertFalse(person.id in shared.editable)
            assertTrue(runCatching { cloud.saveEntry(changed.copy(title = "Forbidden")) }.isFailure)
            assertTrue(runCatching { cloud.join(invite) }.isFailure)
            cloud.confirm(person.id, NoteRules.acknowledge(note, note, "Test Family", "Family / carer", true))
            cloud.signOut()
            cloud.signIn(ownerEmail, password)
            assertEquals("Test Family", cloud.load().confirmations.single { it.versionId == note.id }.name)
            val member = cloud.members(person.id).single()
            assertEquals(readerEmail, member.email)
            cloud.removeMember(person.id, member.userId)
            cloud.removeEntry(changed)
            assertTrue(cloud.load().entries.none { it.id == changed.id })
            cloud.signOut()
            cloud.signIn(readerEmail, password)
            assertTrue(cloud.load().people.none { it.id == person.id })
        } finally {
            runCatching { cloud.signOut() }
            runCatching { cloud.signIn(ownerEmail!!, password!!); cloud.removePerson(person.id) }
            runCatching { cloud.signOut() }
            assertNull(CareCloud(ApplicationProvider.getApplicationContext<Context>()).session.value)
        }
    }
}
