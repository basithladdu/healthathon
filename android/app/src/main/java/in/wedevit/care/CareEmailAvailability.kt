package `in`.wedevit.care

/** Operationally verified 19 October 2026: no custom SMTP or allowed recovery URL.
 * Keep confirmation on. Enable only after delivery and the exact callback have
 * actually been configured and verified; never equate a signup HTTP 200 with mail.
 */
internal object CareEmailAvailability {
    const val ready = false
}
