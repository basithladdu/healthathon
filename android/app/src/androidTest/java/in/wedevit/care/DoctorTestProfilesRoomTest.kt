package `in`.wedevit.care

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Unique in-memory database only; no CareModel or persisted application namespace. */
class DoctorTestProfilesRoomTest {
    @Test fun additiveSeedPreservesLegacyEditsAndIntentionalRemoval()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),CareDatabase::class.java).build()
        try {
            val dao=db.care();val now=Instant.parse("2026-10-23T10:00:00Z").toEpochMilli()
            val legacy=DoctorCareDemoData.fixture(now)
            db.withTransaction {DoctorTestProfiles.insertRecords(dao,legacy)}
            dao.saveDraft(legacy.draft.copy(source="User-edited legacy words",topics="Do not overwrite"))
            suspend fun snapshot(person:String):List<Any?> = listOf(dao.people().first().find {it.id==person},dao.draft(person),dao.versions(person).first(),dao.items(person).first(),dao.reports(person).first())
            val before=snapshot(legacy.person.id)
            val completed=mutableSetOf<String>()
            suspend fun seed() {DoctorTestProfiles.profiles.forEach {p->
                db.withTransaction {if(DoctorTestProfiles.needsSeed(p,dao.people().first().map {it.id}.toSet(),p.key in completed))DoctorTestProfiles.insertRecords(dao,DoctorTestProfiles.fixture(p,now))}
                completed+=p.key
            }}
            seed();assertEquals(before,snapshot(legacy.person.id))
            val rahul=DoctorTestProfiles.personId(DoctorTestProfiles.profiles[0]);val arjun=DoctorTestProfiles.personId(DoctorTestProfiles.profiles[1])
            dao.saveDraft(dao.draft(rahul)!!.copy(topics="Rahul user edit"))
            val rahulBefore=snapshot(rahul);val arjunBefore=snapshot(arjun)
            seed();assertEquals(rahulBefore,snapshot(rahul));assertEquals(arjunBefore,snapshot(arjun));assertEquals(before,snapshot(legacy.person.id))
            dao.deletePersonRow(rahul);seed();assertNull(dao.people().first().find {it.id==rahul})
            assertEquals(arjunBefore,snapshot(arjun));assertEquals(before,snapshot(legacy.person.id))
        } finally {db.close()}
    }
}
