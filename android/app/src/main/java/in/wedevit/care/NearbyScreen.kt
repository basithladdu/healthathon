package `in`.wedevit.care

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** How many centres the Nearby list and its map show at first, and how many each "Show more" tap adds. */
internal const val NEARBY_PAGE = 10

/**
 * Nearby (3 Nov 2026 review): the map first, framed on the person's location when the phone shares it; one "Call 112" bar;
 * searchable state, district and service pickers; coloured service tags on each card; the source and fine print behind info icons.
 * The location is read on the phone only, to frame the map and order the list. It is never saved or sent.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun NearbyScreen(centres: List<Centre>, vm: CareModel? = null) {
    var search by rememberSaveable { mutableStateOf("") }; var service by rememberSaveable { mutableStateOf(NearbyHelp.ALL_SERVICES) }
    val allLayers = NearbyHelp.layerOptions.joinToString(",")
    var layerKey by rememberSaveable { mutableStateOf(allLayers) }; val layers = layerKey.split(",").toSet()
    var suggesting by rememberSaveable { mutableStateOf(false) }
    val suggestions = vm?.centreSuggestions?.collectAsStateWithLifecycle()?.value.orEmpty()
    LaunchedEffect(vm) { vm?.loadCentreSuggestions() }
    var state by rememberSaveable { mutableStateOf(NearbyHelp.ALL_INDIA) }
    var district by rememberSaveable { mutableStateOf("") }
    var chosen by remember { mutableStateOf<List<Centre>>(emptyList()) }
    val focus = LocalFocusManager.current; val t = careText(); val context = LocalContext.current
    val location = nearbyRememberLocation()
    val optionLabel = nearbyOptionLabel()
    val states = remember(centres) { centres.map { it.state }.filter(String::isNotBlank).distinct().sorted() }
    // Build each centre's search text once, not for all 1,600 centres on every typed letter.
    val searchText = remember(centres) { centres.map(NearbyHelp::searchKey) }
    val districts = remember(centres, state) { NearbyHelp.districts(centres, state) }
    val filtered = remember(centres, search, service, layerKey, state, district) { NearbyHelp.visible(centres, searchText, search, state, district, service, layers) }
    val here = location.value
    // Closest first when the phone shares where it is, unless a district was chosen (its own order puts listed matches first).
    val visible = remember(filtered, here, district) { if (here != null && district.isBlank()) NearbyHelp.byDistance(filtered, here.first, here.second) else filtered }
    // Only the first page is composed and drawn on the map; "Show more" adds a page. The page size resets when a filter changes.
    var shown by rememberSaveable(search, service, state, district, layerKey) { mutableStateOf(NEARBY_PAGE) }
    val listed = remember(visible, shown) { visible.take(shown) }
    val clear = { search = ""; service = NearbyHelp.ALL_SERVICES; state = NearbyHelp.ALL_INDIA; district = ""; layerKey = allLayers }
    val filtersOn = search.isNotBlank() || service != NearbyHelp.ALL_SERVICES || state != NearbyHelp.ALL_INDIA || district.isNotBlank() || layerKey != allLayers
    LazyColumn(Modifier.fillMaxSize().testTag("nearby-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading(t("Find care", "देखभाल केंद्र खोजें"), 26)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(listOf(optionLabel(state), district).filter(String::isNotBlank).joinToString(" · "), color = RustText, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    // The count is a badge, not part of the sentence.
                    Surface(shape = RoundedCornerShape(50), color = SaharaSoftGold, contentColor = DoctorCardInk, modifier = Modifier.testTag("nearby-count")) {
                        Text(t("${visible.size} ${if (visible.size == 1) "centre" else "centres"}", "${visible.size} केंद्र"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                    NearbyInfoButton(t("About this list", "इस सूची के बारे में"), Modifier.testTag("nearby-about")) {
                        Text(t("Directory listings. Call to confirm services, availability and cost.", "यह सूची से ली गई जानकारी है। सेवा, उपलब्धता और ख़र्च फ़ोन करके पक्का करें।"))
                        val coverage = CentreLocation.coverage(listed)
                        if (coverage.approximate > 0) Text(t("Approximate locations © GeoNames (CC BY 4.0)", "अनुमानित जगहें © GeoNames (CC BY 4.0)"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            AnimatedVisibility(filtersOn, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
                TextButton(onClick = clear, modifier = Modifier.heightIn(min = 48.dp).testTag("nearby-clear-filters")) { Text(t("Clear filters", "सभी चुनाव हटाएँ")) }
            }
        } }
        // The map comes first. When nothing in the results has a map spot, the list below still has Directions.
        if (listed.any { it.latitude != null && it.longitude != null }) item(key = "nearby-map") {
            Column(Modifier.clip(RoundedCornerShape(20.dp))) { CentreMap(listed, userLocation = here, height = 300.dp, onLocate = location.ask) { chosen = it } }
        }
        item(key = "nearby-call") { NearbyCallBar() }
        item { OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text(t("Name, place or PIN", "नाम, जगह या पिन")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, t("Clear search", "खोज मिटाएँ")) } }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper, focusedBorderColor = Rust, cursorColor = Rust),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() })) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NearbyPicker(t("State", "राज्य"), state, listOf(NearbyHelp.ALL_INDIA) + states, optionLabel, "nearby-state-picker", Modifier.weight(1f)) { if (state != it) district = ""; state = it }
            NearbyPicker(t("Service", "सेवा"), service, NearbyHelp.serviceOptions, optionLabel, "nearby-service-picker", Modifier.weight(1f)) { service = it }
        } }
        if (districts.isNotEmpty()) item { NearbyPicker(t("District", "ज़िला"), district, listOf("") + districts, { if (it.isBlank()) t("All districts", "सभी ज़िले") else it }, "nearby-district-picker", Modifier.fillMaxWidth()) { district = it } }
        // Layer chips double as the legend: each chip's dot is that layer's pin colour on the map and tag colour on the cards.
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("nearby-layers")) {
            NearbyHelp.layerOptions.forEach { layer -> FilterChip(selected = layer in layers, onClick = { layerKey = NearbyHelp.layerOptions.filter { it in NearbyHelp.toggleLayer(layers, layer) }.joinToString(",") },
                label = { Text(if (layer == NearbyHelp.LAYER_MORPHINE) t("Morphine listed", "मॉर्फ़ीन सूची में") else t("Palliative care", "पैलिएटिव देखभाल")) },
                leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(NearbyHelp.layerColor(layer)))) }, shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(containerColor = Paper, labelColor = DoctorCardInk, selectedContainerColor = Mint, selectedLabelColor = DoctorCardInk),
                modifier = Modifier.heightIn(min = 48.dp).testTag("nearby-layer-$layer")) }
        } }
        // Empty results: one icon, one line, one way out.
        if (visible.isEmpty()) item(key = "nearby-empty") { SurfaceCard {
            Column(Modifier.fillMaxWidth().testTag("nearby-empty"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.SearchOff, null, Modifier.size(32.dp), tint = DoctorCardSub)
                Text(t("No centres found", "कोई केंद्र नहीं मिला"), fontSize = 16.sp, color = DoctorCardInk)
                if (filtersOn) OutlinedButton(onClick = clear, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Clear filters", "सभी चुनाव हटाएँ"), color = RustText) }
            }
        } }
        items(listed, key = { it.id }) { c -> Box(Modifier.animateItem()) { NearbyCentreCard(c, here) } }
        if (visible.size > listed.size) item(key = "nearby-show-more") {
            OutlinedButton(onClick = { shown += NEARBY_PAGE }, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("nearby-show-more")) {
                Text(t("Show more", "और दिखाएँ"), color = RustText, fontWeight = FontWeight.SemiBold)
            }
        }
        if (vm != null) item(key = "nearby-suggest") {
            OutlinedButton(onClick = { suggesting = true }, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("nearby-suggest-centre")) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = RustText); Spacer(Modifier.width(8.dp)); Text(t("Suggest a centre", "केंद्र सुझाएँ"), color = RustText)
            }
        }
        if (suggestions.isNotEmpty()) item { CentreSuggestionList(suggestions) }
    }
    if (chosen.isNotEmpty()) ModalBottomSheet(onDismissRequest = { chosen = emptyList() }, containerColor = Cream) {
        // Several centres can share one approximate area pin; the sheet lists all of them.
        LazyColumn(Modifier.padding(start = 16.dp, end = 16.dp), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (chosen.size > 1) item { Text(t("${chosen.size} centres here", "यहाँ ${chosen.size} केंद्र"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading()) }
            items(chosen, key = { it.id }) { NearbyCentreCard(it, here) }
        }
    }
    if (suggesting && vm != null) CentreSuggestionSheet(vm, if (state == NearbyHelp.ALL_INDIA) "" else state) { suggesting = false }
}

/** Filter values stay English (they match the directory data); only the labels follow the language choice. */
@Composable internal fun nearbyOptionLabel(): (String) -> String {
    val t = careText()
    return { o: String -> when (o) {
        NearbyHelp.ALL_INDIA -> t("All India", "पूरा भारत"); NearbyHelp.ALL_SERVICES -> t("All services", "सभी सेवाएँ")
        "Home care" -> t("Home care", "घर पर देखभाल"); "Clinic visits" -> t("Clinic visits", "क्लिनिक में दिखाना"); "Inpatient care" -> t("Inpatient care", "भर्ती करके देखभाल")
        "Day care" -> t("Day care", "दिन में देखभाल"); "Physiotherapy" -> t("Physiotherapy", "फ़िज़ियोथेरेपी"); NearbyHelp.HOSPICE -> t("Hospice", "हॉस्पिस")
        NearbyHelp.LAYER_MORPHINE -> t("Morphine listed", "मॉर्फ़ीन सूची में"); NearbyHelp.LAYER_PALLIATIVE -> t("Palliative care", "पैलिएटिव देखभाल")
        else -> o
    } }
}

/** One slim bar: tapping it opens the phone's dialler with 112 filled in (ACTION_DIAL). The person presses call; the app never calls. */
@Composable fun NearbyCallBar() {
    val t = careText(); val context = LocalContext.current
    Surface(onClick = { open(context, Intent(Intent.ACTION_DIAL, Uri.parse(NearbyHelp.dialUri(NearbyHelp.EMERGENCY_CALL)))) }, shape = RoundedCornerShape(14.dp), color = Peach,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("emergency-112")) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Rust, contentColor = Color.White, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Call, null, Modifier.size(20.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Text(t("Call 112", "112 पर फ़ोन करें"), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink, modifier = Modifier.weight(1f))
        }
    }
}

/** A small info icon that opens [content] in a dialog: for fine print that matters but should not sit on the screen. */
@Composable fun NearbyInfoButton(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val t = careText(); var open by rememberSaveable(title) { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(48.dp)) { Icon(Icons.Outlined.Info, title, tint = DoctorAccent, modifier = Modifier.size(22.dp)) }
    if (open) AlertDialog(onDismissRequest = { open = false }, containerColor = Paper, title = { Text(title, fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content) },
        confirmButton = { TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Close", "बंद करें"), color = RustText) } })
}

/** A field that opens a searchable list in a bottom sheet: a long list of states fits without sideways scrolling. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NearbyPicker(label: String, selected: String, options: List<String>, optionLabel: (String) -> String, tag: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val t = careText(); var open by rememberSaveable(tag) { mutableStateOf(false) }
    Surface(onClick = { open = true }, shape = RoundedCornerShape(14.dp), color = Paper, border = androidx.compose.foundation.BorderStroke(1.dp, DoctorHairline),
        modifier = modifier.heightIn(min = 56.dp).testTag(tag).semantics { contentDescription = "$label: ${optionLabel(selected)}" }) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = DoctorCardSub)
                Text(optionLabel(selected), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ArrowDropDown, null, tint = DoctorAccent)
        }
    }
    if (open) {
        var query by rememberSaveable(tag + "-query") { mutableStateOf("") }
        val shown = remember(options, query, optionLabel) { NearbyHelp.pick(options.map { it to optionLabel(it) }, query) }
        ModalBottomSheet(onDismissRequest = { open = false; query = "" }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
            Column(Modifier.padding(horizontal = 16.dp).testTag("$tag-sheet")) {
                Text(label, fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = DoctorCardInk, modifier = Modifier.a11yHeading())
                Spacer(Modifier.height(12.dp))
                if (options.size > 8) OutlinedTextField(query, { query = it }, placeholder = { Text(t("Search", "खोजें")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper, focusedBorderColor = Rust, cursorColor = Rust),
                    modifier = Modifier.fillMaxWidth().testTag("$tag-search"))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(shown, key = { it.first }) { (value, shownLabel) ->
                        // The chosen row is tinted as well as ticked, so it is easy to spot in a long list.
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(if (value == selected) Mint else Color.Transparent)
                            .clickable(role = Role.Button) { onSelect(value); open = false; query = "" }.padding(horizontal = 12.dp).testTag("nearby-option-$value"),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(shownLabel, Modifier.weight(1f), fontSize = 16.sp, color = DoctorCardInk, fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Normal)
                            if (value == selected) Icon(Icons.Default.Check, t("Selected", "चुना हुआ"), tint = RustText)
                        }
                    }
                    if (shown.isEmpty()) item { Row(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(20.dp), tint = DoctorCardSub); Spacer(Modifier.width(8.dp))
                        Text(t("Nothing matches.", "कुछ नहीं मिला।"), color = DoctorCardSub)
                    } }
                }
            }
        }
    }
}

/** A coloured service or layer tag on a centre card. */
@Composable fun NearbyTag(tag: String, label: String) {
    val (bg, ink) = NearbyHelp.tagColors(tag)
    Surface(shape = RoundedCornerShape(50), color = Color(bg), modifier = Modifier.testTag("centre-tag-$tag")) {
        Text(label, color = Color(ink), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

/** A centre: name, place, coloured tags and the three actions. Address, source and location notes sit behind the info icon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun NearbyCentreCard(c: Centre, here: Pair<Double, Double>? = null) {
    val context = LocalContext.current; val t = careText(); val optionLabel = nearbyOptionLabel()
    val layer = NearbyHelp.layerOf(c)
    SurfaceCard {
        Row(verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = Color(NearbyHelp.tagColors(layer).first), modifier = Modifier.size(40.dp).testTag("centre-layer-$layer")) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.LocalHospital, null, tint = Color(NearbyHelp.layerColor(layer)), modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp, color = DoctorCardInk)
                val km = here?.let { NearbyHelp.distanceKm(c, it.first, it.second) }
                val place = listOf(c.city, c.state).filter(String::isNotBlank).distinct().joinToString(" · ")
                Text(listOfNotNull(place.ifBlank { null }, km?.let { k -> val n = Math.round(k).coerceAtLeast(1); if (CentreLocation.approximate(c)) t("about $n km", "लगभग $n किमी") else t("$n km", "$n किमी") }).joinToString(" · "),
                    color = RustText, style = MaterialTheme.typography.bodyMedium)
            }
            NearbyInfoButton(t("About this centre", "इस केंद्र के बारे में"), Modifier.testTag("centre-info-${c.id}")) {
                if (c.address.isNotBlank()) Text(c.address)
                if (c.sourceListedMorphine == "available") Text(t("Directory lists morphine · call to confirm", "सूची में मॉर्फ़ीन लिखा है · फ़ोन करके पक्का करें"), color = Color(NearbyHelp.layerColor(NearbyHelp.LAYER_MORPHINE)))
                Text(listOf(c.sourceName, c.checkedOn.takeIf { it.isNotBlank() }?.let { t("Source retrieved $it", "जानकारी ली गई $it") }).filterNotNull().filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                CentreLocation.note(c)?.let { (en, hi) -> Text(t(en, hi), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub, modifier = Modifier.testTag("centre-approximate-location")) }
                TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(c.url))) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.testTag("centre-source-${c.id}")) { Text(t("Open the source listing", "मूल सूची खोलें")) }
            }
        }
        val tags = listOf(layer) + NearbyHelp.serviceTags(c)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { tags.forEach { NearbyTag(it, optionLabel(it)) } }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (c.phone.any(Char::isDigit)) FilledTonalButton(onClick = { open(context, Intent(Intent.ACTION_DIAL, Uri.parse(NearbyHelp.dialUri(c.phone)))) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Mint, contentColor = Ink),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("centre-call-${c.id}")) { Icon(Icons.Default.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Call", "फ़ोन करें"), fontWeight = FontWeight.SemiBold) }
            FilledTonalButton(onClick = { nearbyOpenDirections(context, c) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = SaharaSoftGold, contentColor = Ink),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("centre-directions-${c.id}")) { Icon(Icons.Default.Directions, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Directions", "रास्ता"), fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, NearbyHelp.shareText(c)), t("Share centre", "केंद्र भेजें"))) },
                modifier = Modifier.size(48.dp).testTag("centre-share-${c.id}")) { Icon(Icons.Default.Share, t("Share", "भेजें"), tint = DoctorCardSub) }
        }
    }
}

/** Opens Google Maps at the listed centre; any other maps app or the browser if Google Maps is not installed. */
internal fun nearbyOpenDirections(context: Context, c: Centre) {
    val uri = Uri.parse(NearbyHelp.directionsUrl(c))
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")) }
    catch (_: android.content.ActivityNotFoundException) { open(context, Intent(Intent.ACTION_VIEW, uri)) }
}

/** The phone's location for Nearby, plus a way to ask for it again. Coarse location only; null until the person allows it. */
class NearbyLocation(val value: Pair<Double, Double>?, val ask: () -> Unit)

@Composable fun nearbyRememberLocation(): NearbyLocation {
    val context = LocalContext.current
    var here by rememberSaveable { mutableStateOf<Pair<Double, Double>?>(null) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val granted = { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val read = { nearbyReadLocation(context) { here = it } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) read() }
    LaunchedEffect(Unit) {
        if (granted()) read() else if (!asked) { asked = true; launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    }
    return NearbyLocation(here) { if (granted()) read() else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
}

/** The last known fix right away, then a fresh one where Android offers it. Nothing is stored or sent. */
@SuppressLint("MissingPermission")
internal fun nearbyReadLocation(context: Context, onFix: (Pair<Double, Double>) -> Unit) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
    try {
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER).filter { it in manager.allProviders }
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }?.let { onFix(it.latitude to it.longitude) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val live = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER && runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return
            manager.getCurrentLocation(live, null, ContextCompat.getMainExecutor(context)) { fix -> if (fix != null) onFix(fix.latitude to fix.longitude) }
        }
    } catch (_: SecurityException) { }
}
