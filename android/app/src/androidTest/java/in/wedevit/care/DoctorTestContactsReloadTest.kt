package `in`.wedevit.care

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Fresh UUID preference namespace only; never opens default or actual test-workspace stores. */
class DoctorTestContactsReloadTest {
    @Test fun explicitReloadReadsEachPersonsContactsAndPreservesEditsAndRemoval() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val namespace=UUID.randomUUID().toString()
        val store=LocalCareContacts(context,namespace)
        val fixtures=DoctorTestProfiles.profiles.map {DoctorTestProfiles.fixture(it,1_792_747_200_000)}
        try {
            fixtures.forEach {fixture->fixture.contacts.forEach(store::save)}
            val reloaded=LocalCareContacts(context,namespace)
            fixtures.forEach {f->assertEquals(3,reloaded.list(f.person.id).size);assertTrue(reloaded.list(f.person.id).all {it.personId==f.person.id})}
            val rahul=fixtures.first().person.id;val arjun=fixtures.last().person.id
            val arjunBefore=reloaded.list(arjun)
            val original=reloaded.list(rahul).first()
            reloaded.save(original.copy(relationship="User-edited relationship"))
            val edited=reloaded.list(rahul).first {it.id==original.id}
            assertEquals("User-edited relationship",edited.relationship)
            reloaded.remove(edited)
            assertEquals(2,LocalCareContacts(context,namespace).list(rahul).size)
            assertEquals(arjunBefore,LocalCareContacts(context,namespace).list(arjun))
        } finally {fixtures.forEach {store.removePerson(it.person.id)}}
    }
}
