package `in`.wedevit.care

/** Cloud refreshes can never target guest storage or a different account's cache. */
internal fun careCloudAccountMatches(capturedAccount: String, liveAccount: String?): Boolean =
    capturedAccount.isNotBlank() && capturedAccount == liveAccount

/** Date pattern for join and access dates on the account screen. */
internal const val CARE_SHORT_DATE = "d MMM"
/** The server's wording for an email that already has an account (also used when the sign-up reply reveals it). */
internal const val CARE_ALREADY_REGISTERED = "already registered"

/** What a sign-in, sign-up or email request ran into, read from the server's message. */
internal enum class CareAuthProblem { WRONG_PASSWORD, NOT_CONFIRMED, ALREADY_REGISTERED, WEAK_PASSWORD, MAIL_NOT_SENT, EMAIL_LIMIT, WAIT, NO_CONNECTION, OTHER }

internal fun careAuthProblem(raw: String?): CareAuthProblem = raw.orEmpty().lowercase().let { m -> when {
    "invalid login credentials" in m -> CareAuthProblem.WRONG_PASSWORD
    "email not confirmed" in m -> CareAuthProblem.NOT_CONFIRMED
    "already registered" in m || "already been registered" in m || "already exists" in m -> CareAuthProblem.ALREADY_REGISTERED
    "password should" in m || "weak password" in m || "at least 10 characters" in m -> CareAuthProblem.WEAK_PASSWORD
    "error sending" in m || "not authorized" in m -> CareAuthProblem.MAIL_NOT_SENT
    "email rate limit" in m -> CareAuthProblem.EMAIL_LIMIT
    "security purposes" in m || "wait a moment" in m || "rate limit" in m -> CareAuthProblem.WAIT
    m.startsWith("couldn't reach") -> CareAuthProblem.NO_CONNECTION
    else -> CareAuthProblem.OTHER
} }

/** Plain English/Hindi for account problems. Mail problems say plainly that the server's email setup is the cause. */
internal fun careAuthMessage(raw: String?, hindi: Boolean): String {
    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
    return when (careAuthProblem(raw)) {
        CareAuthProblem.WRONG_PASSWORD -> t("Wrong email or password.", "ईमेल या पासवर्ड ग़लत है।")
        CareAuthProblem.NOT_CONFIRMED -> t("Confirm your email first.", "पहले अपना ईमेल पक्का करें।")
        CareAuthProblem.ALREADY_REGISTERED -> t("This email already has an account. Sign in.", "इस ईमेल से खाता पहले से है। साइन इन करें।")
        CareAuthProblem.WEAK_PASSWORD -> t("Use at least 10 characters.", "कम से कम 10 अक्षर रखें।")
        CareAuthProblem.MAIL_NOT_SENT -> t("Email couldn't be sent. Write to workwithdevit@gmail.com.", "ईमेल नहीं भेजा जा सका। workwithdevit@gmail.com पर लिखें।")
        CareAuthProblem.EMAIL_LIMIT -> t("Too many emails. Try again in an hour.", "बहुत ईमेल भेजे गए। एक घंटे बाद कोशिश करें।")
        CareAuthProblem.WAIT -> t("Please wait a minute and try again.", "एक मिनट रुककर फिर कोशिश करें।")
        CareAuthProblem.NO_CONNECTION -> t("No internet. Try again.", "इंटरनेट नहीं है। फिर कोशिश करें।")
        CareAuthProblem.OTHER -> raw?.takeIf { it.isNotBlank() && !hindi } ?: t("Couldn't finish. Please try again.", "काम पूरा नहीं हो सका। कृपया फिर कोशिश करें।")
    }
}
