package `in`.wedevit.care

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/*
 * The privacy policy, read inside the app (25 Nov redesign: it used to leave the app for the browser).
 * res/raw/privacy_policy_en.txt is copied word for word from the published page (prototype/app/privacy/page.tsx, the
 * source of https://sahara.wedevit.in/privacy, updated 21 November 2026): "# " lines are its headings, "• " lines its list items.
 * There is no published Hindi policy yet, so every other language shows the English text with a one-line note
 * (see [InAppDocumentPage]). When a Hindi policy is published, add res/raw/privacy_policy_hi.txt and pass R.raw.privacy_policy_hi.
 */

/** The policy as a pushed page: back arrow, title left, the text, and "Open on the website" at the very end. */
@Composable internal fun PrivacyPolicyPage(onBack: () -> Unit) {
    val t = careText()
    InAppDocumentPage(t("Privacy policy", "निजता नीति"), en = R.raw.privacy_policy_en, hi = null, onBack = onBack, webUrl = stringResource(R.string.privacy_url))
}

/** The same page over a sheet or dialog that is not part of the app shell (the account form). */
@Composable internal fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        PrivacyPolicyPage(onBack = onDismiss)
    }
}
