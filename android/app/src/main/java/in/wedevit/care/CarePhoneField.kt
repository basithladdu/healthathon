package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phone numbers for people (24 Nov clinical meeting): a country code picked with a tap (India first) and the number on a phone
 * keyboard, stored together as one plain string such as "+91 98765 43210" so every older screen and the dialler still read it.
 * Numbers saved before this (no code, "98765-43210", "098765 43210", "919876543210") read as India (+91).
 */
object CarePhone {
    const val DEFAULT_CODE = "+91"
    /** The short list the team agreed: India, then the Gulf and the countries families most often live in. */
    val CODES = listOf("+91", "+971", "+966", "+1", "+44", "+65", "+61", "+974", "+968", "+965")
    /** Longest digits allowed in the number part (ITU E.164 allows 15 in all, code included). */
    const val MAX_DIGITS = 15

    data class Parts(val code: String, val number: String)

    /** The digits of a typed number: everything else (spaces, dashes, letters) is dropped, and it is cut at [MAX_DIGITS]. */
    fun digits(number: String): String = number.filter(Char::isDigit).take(MAX_DIGITS)

    /** "+" and the code's digits; blank or digit-less becomes [DEFAULT_CODE]. */
    fun normaliseCode(code: String): String = code.filter(Char::isDigit).take(4).let { if (it.isEmpty()) DEFAULT_CODE else "+$it" }

    /**
     * One stored string from a code and a number: "" when the number has no digits (the code alone is not a phone number).
     * An Indian mobile (10 digits starting 6-9) is written "+91 98765 43210"; anything else is the code, a space and the digits.
     */
    fun combine(code: String, number: String): String {
        val d = digits(number)
        if (d.isEmpty()) return ""
        val c = normaliseCode(code)
        return if (c == "+91" && d.length == 10 && d[0] in '6'..'9') "$c ${d.take(5)} ${d.drop(5)}" else "$c $d"
    }

    /**
     * A stored or old free-typed number split back into code and digits:
     * - "+971 50 123 4567" (a code, then a space): that code, even one not in [CODES];
     * - "+919876543210" or "00971501234567" (no space): the longest code in [CODES] it starts with, else its first two digits;
     * - no code at all: India. A leading trunk 0 ("098765 43210", "080 2345 6789") and a bare "91" before ten digits are dropped.
     */
    fun split(stored: String): Parts {
        val s = stored.trim()
        if (s.none(Char::isDigit)) return Parts(DEFAULT_CODE, "")
        val international = s.startsWith("+") || s.startsWith("00")
        if (international) {
            val body = if (s.startsWith("+")) s.drop(1).trimStart() else s.drop(2)
            val token = body.takeWhile(Char::isDigit)
            val rest = body.drop(token.length)
            if (token.length in 1..4) return Parts("+$token", digits(rest))
            val all = body.filter(Char::isDigit)
            val known = CODES.sortedByDescending { it.length }.firstOrNull { all.startsWith(it.drop(1)) && all.length > it.length - 1 }
            if (known != null) return Parts(known, digits(all.drop(known.length - 1)))
            return if (all.length > 2) Parts("+" + all.take(2), digits(all.drop(2))) else Parts(DEFAULT_CODE, digits(all))
        }
        val d = s.filter(Char::isDigit)
        return when {
            d.length == 12 && d.startsWith("91") -> Parts(DEFAULT_CODE, d.drop(2))
            d.length == 11 && d.startsWith("0") -> Parts(DEFAULT_CODE, d.drop(1))
            else -> Parts(DEFAULT_CODE, digits(d))
        }
    }

    /** An old free-typed number in today's stored form ("98765-43210" becomes "+91 98765 43210"); blank stays blank. */
    fun normalise(stored: String): String = split(stored).let { combine(it.code, it.number) }
}

/** The country's name beside its code in the picker; [t] is careText(). An unlisted code shows alone. */
internal fun carePhoneCountry(code: String, t: (String, String) -> String): String = when (code) {
    "+91" -> t("India", "भारत")
    "+971" -> t("UAE", "यूएई")
    "+966" -> t("Saudi Arabia", "सऊदी अरब")
    "+1" -> t("US / Canada", "अमेरिका / कनाडा")
    "+44" -> t("UK", "ब्रिटेन")
    "+65" -> t("Singapore", "सिंगापुर")
    "+61" -> t("Australia", "ऑस्ट्रेलिया")
    "+974" -> t("Qatar", "क़तर")
    "+968" -> t("Oman", "ओमान")
    "+965" -> t("Kuwait", "कुवैत")
    else -> ""
}

/**
 * One phone number: a country-code button (one tap opens the short list, India first) and the number on the phone keyboard.
 * [value] and [onValueChange] carry the stored string ([CarePhone.combine]); an older number without a code shows under +91.
 * The number field carries [tag]; the code button is "$tag-code" and each list row "$tag-code-91" and so on.
 * [large] is the onboarding size (22 sp, 64 dp); otherwise it matches the profile editor's fields.
 */
@Composable fun CarePhoneField(value: String, onValueChange: (String) -> Unit, label: String, tag: String, modifier: Modifier = Modifier,
    large: Boolean = false, leadingIcon: ImageVector? = null, container: Color = Paper, enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done, focusRequester: FocusRequester? = null, placeholder: String? = null, onSubmit: () -> Unit = {}) {
    val t = careText()
    val parts = remember(value) { CarePhone.split(value) }
    // The code is kept here too, so a code picked before any digit is typed is not lost (a code alone stores as "").
    var code by rememberSaveable { mutableStateOf(parts.code) }
    LaunchedEffect(value) { if (value.isNotBlank() && parts.code != code) code = parts.code }
    val number = if (value.isBlank()) "" else parts.number
    var open by remember { mutableStateOf(false) }
    val tick = rememberSaharaTick()
    val height = if (large) 64.dp else 56.dp
    val country = carePhoneCountry(code, t)
    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            Surface(onClick = { open = true }, enabled = enabled, shape = RoundedCornerShape(if (large) SaharaRadius.Md else 12.dp), color = container,
                border = BorderStroke(1.dp, SaharaColors.Edge),
                modifier = Modifier.padding(top = 8.dp).height(height).widthIn(min = 88.dp).testTag("$tag-code")
                    .semantics { contentDescription = t("Country code", "देश का कोड") + ": " + listOf(code, country).filter(String::isNotBlank).joinToString(" ") }) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(code, fontSize = if (large) 20.sp else 16.sp, fontWeight = FontWeight.SemiBold, color = SaharaColors.Ink, maxLines = 1, softWrap = false)
                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(24.dp), tint = SaharaColors.InkSub)
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Paper, modifier = Modifier.testTag("$tag-code-menu")) {
                CarePhone.CODES.forEach { option ->
                    val on = option == code
                    DropdownMenuItem(text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(option, Modifier.widthIn(min = 52.dp), fontWeight = FontWeight.SemiBold, color = SaharaColors.Ink)
                            Text(carePhoneCountry(option, t), color = SaharaColors.InkSub)
                        }
                    }, trailingIcon = if (on) { { Icon(Icons.Default.Check, t("Selected", "चुना हुआ"), tint = RustText) } } else null,
                        onClick = { tick(); open = false; code = option; if (number.isNotEmpty()) onValueChange(CarePhone.combine(option, number)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-code-" + option.drop(1)))
                }
            }
        }
        val own = remember { FocusRequester() }
        OutlinedTextField(number, { typed -> onValueChange(CarePhone.combine(code, typed)) }, Modifier.weight(1f).heightIn(min = height).focusRequester(focusRequester ?: own).testTag(tag),
            enabled = enabled, singleLine = true, label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = SaharaColors.InkFaint) } },
            leadingIcon = leadingIcon?.let { { Icon(it, null, Modifier.size(20.dp), tint = DoctorAccent) } },
            textStyle = if (large) MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, color = SaharaColors.Ink) else LocalTextStyle.current,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = imeAction),
            keyboardActions = KeyboardActions(onNext = { onSubmit() }, onDone = { onSubmit() }),
            shape = RoundedCornerShape(if (large) SaharaRadius.Md else 12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = container, unfocusedContainerColor = container, focusedBorderColor = SaharaColors.Primary, focusedLabelColor = RustText))
    }
}
