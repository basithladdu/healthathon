package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareCentreDirectoryTest {
    @Test fun nationalListingsPreserveStateAndSourceWithoutInventingStockOrCoordinates() {
        val value = CareCentreDirectory.decode("""[{"id":"first","name":"Published Centre","state":"Delhi","directoryUrl":"https://example.org/delhi","phone":"123","services":[],"morphine":"yes","sourceListedMorphine":"available","latitude":99,"longitude":80}]""").single()
        assertEquals("Delhi", value.state); assertEquals("123", value.phone)
        assertEquals("unknown", value.morphine); assertNull(value.latitude); assertNull(value.longitude)
        assertEquals("directory_listed", value.verification)
        assertEquals("available", value.sourceListedMorphine)
    }
    @Test fun duplicateIdsCannotSilentlyCollapseSeparateFacilities() {
        val one = """{"id":"same","name":"Centre","state":"Kerala","directoryUrl":"https://example.org/kerala","services":[]}"""
        try { CareCentreDirectory.decode("[$one,$one]"); fail("Duplicate IDs accepted") } catch (_: IllegalArgumentException) { }
    }
}
