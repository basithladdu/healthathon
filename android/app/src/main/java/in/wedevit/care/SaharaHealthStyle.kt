package `in`.wedevit.care

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS Health look for the patient and family side (27 Nov): a tinted grouped ground with a warm peach wash at the top, white
 * rounded cards with a very soft shadow and no hairline, a coloured category label row at the top of each card, and inset grouped
 * lists with a coloured emoji per row. Emoji are decorative (hidden from TalkBack); the label beside them carries the meaning.
 */
object HealthStyle {
    val Peach = Color(0xFFFBE3D3)
    val CardRadius = SaharaRadius.Xl
}

/** One colour and emoji per care category. [ink] is the step used for text on white or on a light tint (>= 4.5:1). */
enum class HealthCategory(val emoji: String, val color: Color, val ink: Color = color) {
    Medicines("💊", Color(0xFF7A3E73)),
    Symptoms("🩹", Color(0xFFC62828)),
    Visits("📅", SaharaColors.Info, Color(0xFF2F5A85)),
    CheckIn("☀️", Color(0xFFB26A00)),
    Journal("📝", SaharaColors.Primary, SaharaColors.PrimaryPressed),
    CareTeam("👥", Color(0xFF3F4F85)),
    Emergency("🆘", Color(0xFFC62828)),
    Documents("📄", Color(0xFF4F6577)),
    Weight("⚖️", Color(0xFF3A6337)),
    Labs("🧪", SaharaColors.Info, Color(0xFF2F5A85)),
    Calendar("🗓️", SaharaColors.Info, Color(0xFF2F5A85)),
    ToDo("✅", Color(0xFF3A6337)),
    Tools("🧰", SaharaColors.Primary, SaharaColors.PrimaryPressed),
    Plans("📋", Color(0xFF3F4F85)),
    Devices("🩺", Color(0xFFC62828)),
    Community("🤝", Color(0xFF8A3F55)),
    Settings("⚙️", Color(0xFF5E5650)),
    Language("🌐", SaharaColors.Info, Color(0xFF2F5A85)),
    Privacy("🔒", Color(0xFF4F6577)),
    Reminders("🔔", Color(0xFFB26A00)),
    Feedback("💬", Color(0xFF3F4F85)),
    Help("🛟", Color(0xFF2C5F59)),
}

/** The screen ground: [SaharaColors.GroundWarm] with a peach wash fading out over the first ~300 dp (iOS Summary). Ink stays >= 7:1 on every stop. */
fun Modifier.saharaHealthBackground(): Modifier = drawBehind {
    drawRect(Color(0xFFF4F8F5))
    drawRect(Brush.verticalGradient(listOf(Color(0xFFDCEDE4), Color(0xFFEAF4EE), Color(0xFFF4F8F5)), startY = 0f, endY = 320.dp.toPx()))
}

/** A decorative emoji: drawn by the system colour emoji font, hidden from screen readers. */
@Composable fun HealthEmoji(emoji: String, modifier: Modifier = Modifier, size: TextUnit = 18.sp) {
    Text(emoji, modifier.clearAndSetSemantics {}, fontSize = size, lineHeight = (size.value + 4f).sp)
}

/** The white card: 20 dp corners, no border, a very soft shadow. Pass [modifier] for tags and width. */
@Composable fun HealthCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(HealthStyle.CardRadius), color = SaharaColors.Surface, shadowElevation = 2.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD9C7B8))) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** The coloured category row at the top of a card: emoji, label in the category colour (SemiBold 15 sp) and, when [onClick] is set, a chevron (48 dp tall). */
@Composable fun HealthCardLabel(category: HealthCategory, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().then(if (onClick != null) Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick) else Modifier), verticalAlignment = Alignment.CenterVertically) {
        HealthEmoji(category.emoji, size = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(label, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = category.ink, maxLines = 1)
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(22.dp), tint = SaharaColors.LineStrong)
    }
}

/** A tinted pill button (light tint, coloured text), like iOS "Take Questionnaire". 48 dp tall. */
@Composable fun HealthTintButton(label: String, category: HealthCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(50), color = category.color.copy(alpha = .16f).compositeOverWhite(), contentColor = category.ink, border = androidx.compose.foundation.BorderStroke(1.5.dp, category.color.copy(alpha = .5f))) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

private fun Color.compositeOverWhite(): Color = this.compositeOver(Color.White)
