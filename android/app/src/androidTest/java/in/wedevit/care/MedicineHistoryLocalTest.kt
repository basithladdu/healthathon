package `in`.wedevit.care

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Unique private namespaces only; no real account, Room database, medicine or network request. */
class MedicineHistoryLocalTest {
    @Test fun reportsAndCorrectionsRemainWhenCourseMovesToPastAndBack()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val account="qa-medicine-${UUID.randomUUID()}";val person="qa-person-${UUID.randomUUID()}"
        val repo=MedicineHistoryRepo(context,null,account);val item=CareItem("qa-medicine",person,"medicine","Synthetic only","Old directions")
        val event=MedicineIntakeEvent(UUID.randomUUID().toString(),item.id,item.title,"taken",loggedAt=System.currentTimeMillis(),reporter="Synthetic patient report")
        repo.append(person,event);repo.append(person,event)
        assertEquals(1,repo.load(person).events.size)
        repo.setPast(person,item,true,"Synthetic course ended")
        assertTrue(item.id in repo.load(person).pastIds)
        assertEquals(2,repo.load(person).events.size)
        repo.setPast(person,item,false,"Synthetic course restarted")
        assertTrue(repo.load(person).pastIds.isEmpty())
        appendLocalMedicineCheckoff(context,account,item,"","2026-10-23")
        appendLocalMedicineCheckoff(context,account,item,"2026-10-23","")
        val result=repo.load(person)
        assertEquals(5,result.events.size)
        assertTrue(result.events.filter{it.source=="daily-checkoff"}.all{it.reportedTakenAt==null&&it.plannedAt==null})
        assertTrue(result.events.any{it.outcome=="unmarked"})
        assertTrue(MedicineHistoryRepo(context,null,"different-$account").load(person).events.isEmpty())
        assertTrue(repo.load("different-$person").events.isEmpty())
    }
}
