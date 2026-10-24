package `in`.wedevit.care

/** Cloud refreshes can never target guest storage or a different account's cache. */
internal fun careCloudAccountMatches(capturedAccount: String, liveAccount: String?): Boolean =
    capturedAccount.isNotBlank() && capturedAccount == liveAccount
