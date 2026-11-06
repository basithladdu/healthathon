package `in`.wedevit.care

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Small dialog for results that matter (an error, a confirmation, something the person must see), in place of a toast.
 * Icon in the tone's chip, a short title, at most a line or two of text, one solid button; dismissible in one tap.
 * A quiet "Saved" stays inline or a toast; do not use a dialog where it only adds a tap.
 */
@Composable fun SaharaDialog(title: String, onDismiss: () -> Unit, text: String? = null, tone: SaharaTone = SaharaTone.Info, icon: ImageVector? = null,
    confirmLabel: String? = null, onConfirm: (() -> Unit)? = null, dismissLabel: String? = null, modifier: Modifier = Modifier) {
    val t = careText()
    val glyph = icon ?: when (tone) { SaharaTone.Success -> Icons.Default.CheckCircle; SaharaTone.Error -> Icons.Default.ErrorOutline; SaharaTone.Warning -> Icons.Default.WarningAmber; else -> Icons.Default.Info }
    AlertDialog(onDismissRequest = onDismiss, modifier = modifier.testTag("sahara-dialog"), containerColor = SaharaColors.Surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(SaharaRadius.Hero),
        icon = { if (tone == SaharaTone.Success) SaharaSuccessTick(size = 48) else SaharaIconChip(glyph, tone, size = 48) },
        title = { Text(title, Modifier.a11yHeading(), style = MaterialTheme.typography.titleLarge, color = SaharaColors.Ink, textAlign = TextAlign.Center) },
        text = text?.let { { Text(it, Modifier.announce(), style = MaterialTheme.typography.bodyLarge, color = SaharaColors.InkSub, textAlign = TextAlign.Center) } },
        confirmButton = {
            SaharaPrimaryButton(confirmLabel ?: t("OK", "ठीक है"), onClick = { (onConfirm ?: onDismiss)() }, modifier = Modifier.testTag("sahara-dialog-confirm"))
        },
        dismissButton = dismissLabel?.let { { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("sahara-dialog-dismiss"), colors = ButtonDefaults.textButtonColors(contentColor = SaharaColors.InkSub)) { Text(it, fontWeight = FontWeight.SemiBold) } } })
}

/** The runtime permissions Sahara asks for, each with its icon, its section colour and the one-line reason shown before Android asks. */
enum class SaharaPermission(val permission: String?, val icon: ImageVector, val tone: SaharaTone) {
    Microphone(Manifest.permission.RECORD_AUDIO, Icons.Default.Mic, SaharaTone.Rose),
    Camera(Manifest.permission.CAMERA, Icons.Default.PhotoCamera, SaharaTone.Teal),
    Location(Manifest.permission.ACCESS_COARSE_LOCATION, Icons.Default.Place, SaharaTone.Sky),
    Notifications(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null, Icons.Default.NotificationsActive, SaharaTone.Marigold);

    fun title(t: (String, String) -> String) = when (this) {
        Microphone -> t("Use the microphone?", "माइक्रोफ़ोन इस्तेमाल करें?")
        Camera -> t("Use the camera?", "कैमरा इस्तेमाल करें?")
        Location -> t("Use your location?", "अपनी जगह इस्तेमाल करें?")
        Notifications -> t("Allow reminders?", "रिमाइंडर की अनुमति दें?")
    }
    fun why(t: (String, String) -> String) = when (this) {
        Microphone -> t("Sahara records the conversation only while you press record.", "सहारा बातचीत सिर्फ़ तभी रिकॉर्ड करता है जब आप रिकॉर्ड दबाते हैं।")
        Camera -> t("To photograph a prescription or report. Photos stay on this phone until you save them.", "पर्चे या रिपोर्ट की फ़ोटो लेने के लिए। सहेजने तक फ़ोटो इसी फ़ोन पर रहती हैं।")
        Location -> t("To show care centres near you. Your location is not saved.", "आपके पास के देखभाल केंद्र दिखाने के लिए। आपकी जगह सहेजी नहीं जाती।")
        Notifications -> t("To remind you about medicines and visits on time.", "दवाओं और मुलाक़ातों की समय पर याद दिलाने के लिए।")
    }
    fun blocked(t: (String, String) -> String) = when (this) {
        Microphone -> t("The microphone is turned off for Sahara. Turn it on in Settings, under Permissions.", "सहारा के लिए माइक्रोफ़ोन बंद है। इसे सेटिंग्स में, अनुमतियों में चालू करें।")
        Camera -> t("The camera is turned off for Sahara. Turn it on in Settings, under Permissions.", "सहारा के लिए कैमरा बंद है। इसे सेटिंग्स में, अनुमतियों में चालू करें।")
        Location -> t("Location is turned off for Sahara. Turn it on in Settings, under Permissions.", "सहारा के लिए जगह बंद है। इसे सेटिंग्स में, अनुमतियों में चालू करें।")
        Notifications -> t("Notifications are turned off for Sahara. Turn them on in Settings.", "सहारा के लिए सूचनाएँ बंद हैं। इन्हें सेटिंग्स में चालू करें।")
    }

    fun granted(context: Context): Boolean = permission == null || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/** Whether to show the explanation, ask Android, or send the person to Settings. Pure, so it is unit-tested. */
internal object SaharaPermissionRules {
    enum class Step { Granted, Explain, Settings }
    /** [askedBefore]: Android was asked at least once. [rationale]: Android's shouldShowRequestPermissionRationale. */
    fun next(granted: Boolean, askedBefore: Boolean, rationale: Boolean): Step = when {
        granted -> Step.Granted
        askedBefore && !rationale -> Step.Settings // "Don't ask again", or denied twice: Android will not show its prompt.
        else -> Step.Explain
    }
}

/** Hands out a permission check: call [request]; [onGranted] runs at once if allowed, otherwise after the person allows it. */
class SaharaPermissionRequest internal constructor(private val start: (() -> Unit) -> Unit) { fun request(onGranted: () -> Unit) = start(onGranted) }

/**
 * Before Android's own prompt, Sahara explains in one line why it needs [kind], with Allow / Not now.
 * If the person has turned it off for good, the dialog instead offers Open Settings.
 */
@Composable fun rememberSaharaPermission(kind: SaharaPermission): SaharaPermissionRequest {
    val context = LocalContext.current; val t = careText()
    val prefs = remember(context) { context.applicationContext.getSharedPreferences("sahara-permissions", Context.MODE_PRIVATE) }
    var step by remember { mutableStateOf<SaharaPermissionRules.Step?>(null) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        prefs.edit().putBoolean(kind.name, true).apply()
        if (ok) pending?.invoke()
        else if (SaharaPermissionRules.next(false, true, rationale(context, kind)) == SaharaPermissionRules.Step.Settings) step = SaharaPermissionRules.Step.Settings
        pending = null
    }
    val request = remember(kind, context) { SaharaPermissionRequest { onGranted ->
        val next = SaharaPermissionRules.next(kind.granted(context), prefs.getBoolean(kind.name, false), rationale(context, kind))
        if (next == SaharaPermissionRules.Step.Granted) onGranted() else { pending = onGranted; step = next }
    } }
    when (step) {
        SaharaPermissionRules.Step.Explain -> SaharaPermissionDialog(kind, onAllow = { step = null; kind.permission?.let { launcher.launch(it) } ?: pending?.invoke() }, onNotNow = { step = null; pending = null })
        SaharaPermissionRules.Step.Settings -> SaharaDialog(kind.title(t), onDismiss = { step = null; pending = null }, text = kind.blocked(t), tone = kind.tone, icon = kind.icon,
            confirmLabel = t("Open Settings", "सेटिंग्स खोलें"), onConfirm = { step = null; pending = null; openAppSettings(context) }, dismissLabel = t("Not now", "अभी नहीं"),
            modifier = Modifier.testTag("sahara-permission-settings"))
        else -> Unit
    }
    return request
}

/** The pre-permission dialog itself: icon, one-line why, Allow / Not now. */
@Composable fun SaharaPermissionDialog(kind: SaharaPermission, onAllow: () -> Unit, onNotNow: () -> Unit) {
    val t = careText()
    SaharaDialog(kind.title(t), onDismiss = onNotNow, text = kind.why(t), tone = kind.tone, icon = kind.icon, confirmLabel = t("Allow", "अनुमति दें"), onConfirm = onAllow,
        dismissLabel = t("Not now", "अभी नहीं"), modifier = Modifier.testTag("sahara-permission-${kind.name.lowercase()}"))
}

private fun rationale(context: Context, kind: SaharaPermission): Boolean {
    val permission = kind.permission ?: return false
    var owner = context
    while (owner is ContextWrapper && owner !is Activity) owner = owner.baseContext
    return (owner as? Activity)?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, permission) } ?: false
}

private fun openAppSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
