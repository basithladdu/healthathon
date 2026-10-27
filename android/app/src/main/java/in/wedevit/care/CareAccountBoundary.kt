package `in`.wedevit.care

/** Cloud refreshes can never target guest storage or a different account's cache. */
internal fun careCloudAccountMatches(capturedAccount: String, liveAccount: String?): Boolean =
    capturedAccount.isNotBlank() && capturedAccount == liveAccount

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
    val t = { en: String, hi: String -> if (hindi) hi else en }
    return when (careAuthProblem(raw)) {
        CareAuthProblem.WRONG_PASSWORD -> t("That email and password don't match an account. Check them, or use Forgot password.", "यह ईमेल और पासवर्ड किसी खाते से मेल नहीं खाते। दोबारा देखें, या “पासवर्ड भूल गए?” दबाएँ।")
        CareAuthProblem.NOT_CONFIRMED -> t("This email isn't confirmed yet. Open the link in the confirmation email on this phone, then sign in. No email? Tap Resend confirmation email.", "यह ईमेल अभी पक्का नहीं हुआ है। पुष्टि वाले ईमेल का लिंक इसी फ़ोन पर खोलें, फिर साइन इन करें। ईमेल नहीं आया? “पुष्टि ईमेल फिर भेजें” दबाएँ।")
        CareAuthProblem.ALREADY_REGISTERED -> t("This email already has a Sahara account. Sign in instead, or use Forgot password.", "इस ईमेल से Sahara खाता पहले से बना है। साइन इन करें, या “पासवर्ड भूल गए?” दबाएँ।")
        CareAuthProblem.WEAK_PASSWORD -> t("Choose a longer password: at least 10 characters.", "लंबा पासवर्ड चुनें: कम से कम 10 अक्षर।")
        CareAuthProblem.MAIL_NOT_SENT -> t("The server couldn't send the email. Sahara's email service isn't fully set up yet, so this is not your mistake. Write to workwithdevit@gmail.com and the team will help.", "सर्वर ईमेल नहीं भेज सका। Sahara की ईमेल सेवा अभी पूरी तरह चालू नहीं है, इसमें आपकी ग़लती नहीं है। workwithdevit@gmail.com पर लिखें, टीम मदद करेगी।")
        CareAuthProblem.EMAIL_LIMIT -> t("The server has sent as many emails as it can for now. Try again in an hour.", "सर्वर अभी जितने ईमेल भेज सकता था, भेज चुका है। एक घंटे बाद फिर कोशिश करें।")
        CareAuthProblem.WAIT -> t("Please wait a minute and try again.", "एक मिनट रुककर फिर कोशिश करें।")
        CareAuthProblem.NO_CONNECTION -> t("No connection right now. Check your internet and try again.", "अभी इंटरनेट नहीं मिल रहा। इंटरनेट देखकर फिर कोशिश करें।")
        CareAuthProblem.OTHER -> raw?.takeIf { it.isNotBlank() && !hindi } ?: t("Couldn't finish. Please try again.", "काम पूरा नहीं हो सका। कृपया फिर कोशिश करें।")
    }
}
