@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package `in`.wedevit.care

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The map and visible region buttons update the same selection. No anatomical precision or diagnosis is inferred. */
@Composable fun CarePainBodyMap(selected: List<String>, hindi: Boolean, onChange: (List<String>) -> Unit) {
    var back by rememberSaveable { mutableStateOf(false) }
    val latestSelected by rememberUpdatedState(selected)
    val latestOnChange by rememberUpdatedState(onChange)
    val t = { en: String, hi: String -> if (hindi) hi else en }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Where is the pain? (optional)", "दर्द कहाँ है? (वैकल्पिक)"), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!back, { back = false }, label = { Text(t("Front", "सामने")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("pain-body-front"))
            FilterChip(back, { back = true }, label = { Text(t("Back", "पीछे")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("pain-body-back"))
            if (selected.isNotEmpty()) TextButton(onClick = { onChange(emptyList()) }, modifier = Modifier.heightIn(min = 48.dp).testTag("pain-body-clear")) { Text(t("Clear", "हटाएँ")) }
        }
        Text(t("Tap a region or choose below. Left and right mean the patient's sides.", "शरीर पर छुएँ या नीचे चुनें। बायाँ और दायाँ मरीज़ की तरफ़ से हैं।"), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(if (back) t("Left", "बायाँ") else t("Right", "दायाँ"), style = MaterialTheme.typography.labelMedium)
            Canvas(Modifier.height(300.dp).width(150.dp).testTag("pain-body-map")
                .semantics { contentDescription = t("Pain location body map. Use the region buttons below for an accessible selection.", "दर्द की जगह का शरीर का नक्शा। नीचे जगह के बटन से भी चुन सकते हैं।") }
                .pointerInput(back) {
                    detectTapGestures { point ->
                        PainBodyMapRules.hit(back, point.x * 100f / size.width, point.y * 200f / size.height)?.let { id ->
                            latestOnChange(PainBodyMapRules.toggled(latestSelected, id))
                        }
                    }
                }) {
                PainBodyMapRules.regions(back).forEach { region ->
                    val path = Path().apply {
                        region.outline.forEachIndexed { index, point ->
                            val x = point.x * size.width / 100f; val y = point.y * size.height / 200f
                            if (index == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawPath(path, if (region.id in selected) colors.primary else colors.surfaceVariant)
                    drawPath(path, colors.outline, style = Stroke(width = 1.dp.toPx()))
                }
            }
            Text(if (back) t("Right", "दायाँ") else t("Left", "बायाँ"), style = MaterialTheme.typography.labelMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PainBodyMapRules.sites.forEach { (id, _) ->
                FilterChip(id in selected, { onChange(PainBodyMapRules.toggled(selected, id)) }, label = { Text(PainBodyMapRules.label(id, hindi)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("pain-body-site-$id"))
            }
        }
        if (selected.isNotEmpty()) Text(selected.joinToString { PainBodyMapRules.label(it, hindi) }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("pain-body-selected"))
    }
}
