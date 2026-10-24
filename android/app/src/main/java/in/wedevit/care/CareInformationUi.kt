package `in`.wedevit.care

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable internal fun CareSettingsRow(title: String, icon: ImageVector, onClick: () -> Unit, tag: String = "", detail: String? = null) {
    Surface(shape = RoundedCornerShape(18.dp), color = Paper, modifier = Modifier.fillMaxWidth().testTag(tag).clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = Mint) { Icon(icon, null, Modifier.padding(10.dp).size(22.dp), tint = Rust) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(19.dp), tint = Rust)
        }
    }
}

@Composable internal fun CareInformationScreen(page: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val privacy = page == "privacy"
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().testTag("care-information-$page"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Account") }
            Heading(if (privacy) "Privacy" else "About Sahara", 28)
        } }
        if (privacy) {
            item { InformationCard("Your care record", Icons.Default.Lock, listOf(
                "You choose which care notes to share and with whom.",
                "Changing your display role does not grant access to anyone's records."
            )) }
            item { InformationCard("Files and recordings", Icons.Default.Folder, listOf(
                "Reports imported into personal Documents stay on this phone. They do not sync with your care team; Share sends a separate copy.",
                "Files and recordings explicitly uploaded in the doctor's test tools are separate protected uploads.",
                "Prescription-photo reading, downloaded Hindi/English speech tools, translation and read-aloud run on this phone. Check the original and every medicine before saving."
            )) }
            item { InformationCard("Sharing and deletion", Icons.Default.ManageAccounts, listOf(
                "You can remove a recording or a person's records in the app. Keep originals or export anything you need first.",
                "Removing a local record does not recall copies already shared. Protected test uploads may need a separate verified support-removal request.",
                "Keep original reports before removing the app."
            )) }
            item { Button(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_url)))) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.OpenInNew, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("Read full privacy policy")
            } }
        } else {
            item { SurfaceCard(Mint) {
                Icon(Icons.Default.Favorite, null, Modifier.size(36.dp), tint = Rust)
                Spacer(Modifier.height(12.dp)); Heading("Sahara", 30)
                Text("A Palliative Care Companion", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp)); Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge, color = Rust)
            } }
            item { InformationCard("Made for daily care", Icons.Default.MonitorHeart, listOf(
                "Care conversations, medicines, visits and personal symptom notes in one place.",
                "Your doctor guides treatment. The symptom diary is not a diagnosis or a validated ESAS-r/IPOS assessment."
            )) }
            item { InformationCard("Your care team", Icons.Default.MedicalServices, listOf(
                "Choosing Patient or Doctor is a sign-in choice, not identity verification.",
                "Only independently approved, invited accounts can publish verified clinical reviews online."
            )) }
            item { InformationCard("Tools on your phone", Icons.Default.PhoneAndroid, listOf(
                "Dictate a speaker's turn or transcribe a recording using the downloaded Hindi/English pack, then check the words.",
                "Hindi/English reading uses Google Translate on-device models. Read aloud uses a downloaded voice."
            )) }
            item { CareSettingsRow("Privacy", Icons.Default.PrivacyTip, { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_url)))) }, detail = "Read the full policy") }
        }
    }
}

@Composable private fun InformationCard(title: String, icon: ImageVector, lines: List<String>) {
    SurfaceCard(Paper) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = Rust)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        lines.forEach { line -> Spacer(Modifier.height(12.dp)); Text(line, style = MaterialTheme.typography.bodyMedium) }
    }
}
