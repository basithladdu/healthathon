package `in`.wedevit.care

/** 26 October 2026: sign-up and reset are shown again for the Saanthvana project.
 * Supabase's built-in mailer only reaches the project team's own addresses, so
 * other people's confirmation mail needs custom SMTP (or confirmation turned off
 * for testing) in the dashboard. A signup HTTP 200 still does not mean mail arrived.
 */
internal object CareEmailAvailability {
    const val ready = true
}
