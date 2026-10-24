package `in`.wedevit.care

/** A real shared person and a synthetic test record are never interchangeable. */
internal enum class DoctorPatientSource { SHARED, DEVICE, TEST }
internal data class DoctorPatientSelection(val accountId: String, val source: DoctorPatientSource, val id: String) {
    val stateKey: String get() = "$accountId:${source.name}:$id"
}

internal fun doctorSharedPatientReady(selection: DoctorPatientSelection?, accountId: String, permittedIds: Set<String>, selectedId: String, draftPersonId: String): Boolean =
    accountId.isNotBlank() && selection != null && selection.accountId == accountId && selection.source == DoctorPatientSource.SHARED &&
        selection.id in permittedIds && selection.id == selectedId && selection.id == draftPersonId
