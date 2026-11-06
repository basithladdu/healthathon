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
import androidx.compose.animation.animateColorAsState
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
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val filterCount = listOf(service != NearbyHelp.ALL_SERVICES, state != NearbyHelp.ALL_INDIA, district.isNotBlank(), layerKey != allLayers).count { it }
    // First screen at 360 x 800: title, Call 112, search with one Filters button, a short map, and the first centre. Filters live in one sheet.
    LazyColumn(Modifier.fillMaxSize().saharaScreenEnter().testTag("nearby-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading(t("Find care", "देखभाल केंद्र खोजें"), 26)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(listOf(optionLabel(state), district).filter(String::isNotBlank).joinToString(" · "), color = RustText, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    // The count is a badge, not part of the sentence.
                    FamilyPill(t("${visible.size} ${if (visible.size == 1) "centre" else "centres"}", "${visible.size} केंद्र"), if (visible.isEmpty()) FamilyTone.NEUTRAL else FamilyTone.INFO, Modifier.testTag("nearby-count"))
                    NearbyInfoButton(t("About this list", "इस सूची के बारे में"), Modifier.testTag("nearby-about")) {
                        Text(t("Directory listings. Call to confirm services, availability and cost.", "यह सूची से ली गई जानकारी है। सेवा, उपलब्धता और ख़र्च फ़ोन करके पक्का करें।"))
                        val coverage = CentreLocation.coverage(listed)
                        if (coverage.approximate > 0) Text(t("Approximate locations © GeoNames (CC BY 4.0)", "अनुमानित जगहें © GeoNames (CC BY 4.0)"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } }
        item(key = "nearby-call") { NearbyCallBar() }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text(t("Name, place or PIN", "नाम, जगह या पिन")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, t("Clear search", "खोज मिटाएँ")) } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).heightIn(min = 56.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper, focusedBorderColor = Rust, cursorColor = Rust),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }))
            Spacer(Modifier.width(8.dp))
            // State, district, service and the two layers: one named button, with how many are on.
            val filterPress = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Surface(onClick = { focus.clearFocus(); filtersOpen = true }, shape = RoundedCornerShape(12.dp), color = if (filterCount > 0) SaharaTone.Indigo.solid else Paper, contentColor = if (filterCount > 0) Color.White else SaharaTone.Indigo.ink,
                border = androidx.compose.foundation.BorderStroke(2.dp, SaharaTone.Indigo.solid), interactionSource = filterPress, modifier = Modifier.heightIn(min = 56.dp).saharaPress(filterPress).testTag("nearby-filters")) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (filterCount > 0) t("Filters · $filterCount", "चुनाव · $filterCount") else t("Filters", "चुनाव"), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        } }
        // The map, short enough that the first centre shows below it. When nothing has a map spot, the list still has Directions.
        if (listed.any { it.latitude != null && it.longitude != null }) item(key = "nearby-map") {
            Column(Modifier.clip(RoundedCornerShape(12.dp))) { CentreMap(listed, userLocation = here, height = 220.dp, onLocate = location.ask) { chosen = it } }
        }
        // Empty results: one icon, one line, one way out.
        if (visible.isEmpty()) item(key = "nearby-empty") { SurfaceCard {
            Column(Modifier.fillMaxWidth().testTag("nearby-empty"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SaharaLottie(SaharaLottieKind.MapPin)
                Text(t("No centres found", "कोई केंद्र नहीं मिला"), fontSize = 16.sp, color = DoctorCardInk)
                if (filtersOn) OutlinedButton(onClick = clear, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Clear filters", "सभी चुनाव हटाएँ"), color = RustText) }
            }
        } }
        items(listed, key = { it.id }) { c -> Box(Modifier.animateItem()) { NearbyCentreCard(c, here) } }
        if (visible.size > listed.size) item(key = "nearby-show-more") {
            OutlinedButton(onClick = { shown += NEARBY_PAGE }, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("nearby-show-more")) {
                Text(t("Show more", "और दिखाएँ"), color = RustText, fontWeight = FontWeight.SemiBold)
            }
        }
        // Suggesting needs an account; signed out, the button would only lead to a "sign in" dead end, so it is not shown.
        if (vm?.cloud != null) item(key = "nearby-suggest") {
            SaharaSecondaryButton(t("Suggest a centre", "केंद्र सुझाएँ"), onClick = { suggesting = true }, icon = Icons.Default.AddLocationAlt, tone = SaharaTone.Marigold, modifier = Modifier.fillMaxWidth().testTag("nearby-suggest-centre"))
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
    if (filtersOpen) NearbyFilterSheet(state, district, service, layers, states, districts, optionLabel, visible.size, filtersOn,
        onState = { if (state != it) district = ""; state = it }, onDistrict = { district = it }, onService = { service = it },
        onLayer = { layer -> layerKey = NearbyHelp.layerOptions.filter { it in NearbyHelp.toggleLayer(layers, layer) }.joinToString(",") }, onClear = clear, onDismiss = { filtersOpen = false })
    if (suggesting && vm != null) CentreSuggestionSheet(vm, if (state == NearbyHelp.ALL_INDIA) "" else state) { suggesting = false }
}

/** All Nearby filters in one sheet: state, district, service, and the two layers (which are also the map legend). One button shows the results. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NearbyFilterSheet(state: String, district: String, service: String, layers: Set<String>, states: List<String>, districts: List<String>, optionLabel: (String) -> String,
    count: Int, filtersOn: Boolean, onState: (String) -> Unit, onDistrict: (String) -> Unit, onService: (String) -> Unit, onLayer: (String) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val t = careText()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).testTag("nearby-filter-sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Filters", "चुनाव"), Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 22.sp, color = DoctorCardInk)
                if (filtersOn) TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp).testTag("nearby-clear-filters")) { Text(t("Clear filters", "सभी चुनाव हटाएँ"), color = RustText, fontWeight = FontWeight.SemiBold) }
            }
            NearbyPicker(t("State", "राज्य"), state, listOf(NearbyHelp.ALL_INDIA) + states, optionLabel, "nearby-state-picker", Modifier.fillMaxWidth(), onState)
            if (districts.isNotEmpty()) NearbyPicker(t("District", "ज़िला"), district, listOf("") + districts, { if (it.isBlank()) t("All districts", "सभी ज़िले") else it }, "nearby-district-picker", Modifier.fillMaxWidth(), onDistrict)
            NearbyPicker(t("Service", "सेवा"), service, NearbyHelp.serviceOptions, optionLabel, "nearby-service-picker", Modifier.fillMaxWidth(), onService)
            // The two layers are the legend too: each tile's colour is that layer's pin on the map and tag on the cards. A filled tile is on.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("nearby-layers"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NearbyHelp.layerOptions.forEach { layer ->
                    val on = layer in layers; val accent = Color(NearbyHelp.layerColor(layer))
                    val fill by animateColorAsState(if (on) accent else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "nearby-layer")
                    val ink = if (on) readableOn(accent) else DoctorCardInk
                    Surface(selected = on, onClick = { onLayer(layer) }, shape = RoundedCornerShape(12.dp), color = fill, contentColor = ink,
                        border = androidx.compose.foundation.BorderStroke(if (on) 0.dp else 1.5.dp, accent), modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 56.dp).testTag("nearby-layer-$layer")) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (on) Icons.Default.CheckCircle else if (layer == NearbyHelp.LAYER_MORPHINE) Icons.Default.Medication else Icons.Default.LocalHospital, null, Modifier.size(20.dp), tint = if (on) ink else accent)
                            Spacer(Modifier.width(8.dp))
                            Text(if (layer == NearbyHelp.LAYER_MORPHINE) t("Morphine listed", "मॉर्फ़ीन सूची में") else t("Palliative care", "पैलिएटिव देखभाल"), fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp,
                                style = LocalTextStyle.current.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading, hyphens = androidx.compose.ui.text.style.Hyphens.None))
                        }
                    }
                }
            }
            SaharaPrimaryButton(if (count == 1) t("Show 1 centre", "1 केंद्र दिखाएँ") else t("Show $count centres", "$count केंद्र दिखाएँ"), onClick = onDismiss, tone = SaharaTone.Indigo, modifier = Modifier.fillMaxWidth().testTag("nearby-filters-done"))
        }
    }
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
    Surface(onClick = { open(context, Intent(Intent.ACTION_DIAL, Uri.parse(NearbyHelp.dialUri(NearbyHelp.EMERGENCY_CALL)))) }, shape = RoundedCornerShape(12.dp), color = Peach,
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
    Surface(onClick = { open = true }, shape = RoundedCornerShape(12.dp), color = Paper, border = androidx.compose.foundation.BorderStroke(1.dp, DoctorHairline),
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
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Paper, focusedContainerColor = Paper, focusedBorderColor = Rust, cursorColor = Rust),
                    modifier = Modifier.fillMaxWidth().testTag("$tag-search"))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(shown, key = { it.first }) { (value, shownLabel) ->
                        // The chosen row is tinted as well as ticked, so it is easy to spot in a long list.
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(if (value == selected) Mint else Color.Transparent)
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
            if (c.phone.any(Char::isDigit)) Button(onClick = { open(context, Intent(Intent.ACTION_DIAL, Uri.parse(NearbyHelp.dialUri(c.phone)))) }, colors = ButtonDefaults.buttonColors(containerColor = DoctorStatusGood, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("centre-call-${c.id}")) { Icon(Icons.Default.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Call", "फ़ोन करें"), fontWeight = FontWeight.SemiBold) }
            OutlinedButton(onClick = { nearbyOpenDirections(context, c) }, colors = ButtonDefaults.outlinedButtonColors(containerColor = Paper, contentColor = Ink), border = androidx.compose.foundation.BorderStroke(1.5.dp, Ink),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("centre-directions-${c.id}")) { Icon(Icons.Default.Directions, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Directions", "रास्ता"), fontWeight = FontWeight.SemiBold) }
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
    // "Not now" is remembered on this phone, so Nearby does not ask again every time it opens; the map's locate button still offers it.
    val prefs = remember { context.getSharedPreferences("nearby_location", Context.MODE_PRIVATE) }
    var dialog by rememberSaveable { mutableStateOf("") } // "" | "why" | "settings"
    val granted = { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val read = { nearbyReadLocation(context) { here = it } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) read()
        else {
            prefs.edit().putBoolean("declined", true).apply()
            // Android stops showing its own prompt after "Don't allow" twice; then only Settings can turn location on.
            val activity = nearbyActivity(context)
            if (activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) prefs.edit().putBoolean("blocked", true).apply()
        }
    }
    LaunchedEffect(Unit) {
        if (granted()) read() else if (!prefs.getBoolean("declined", false)) dialog = "why"
    }
    val t = careText()
    if (dialog == "why") FamilyPermissionDialog(Icons.Default.MyLocation, t("Show centres near you?", "आपके पास के केंद्र दिखाएँ?"),
        t("Your location puts the closest centres first. It stays on this phone.", "आपकी जगह से सबसे पास के केंद्र पहले दिखते हैं। यह जानकारी इसी फ़ोन पर रहती है।"),
        t("Allow", "अनुमति दें"), onConfirm = { dialog = ""; launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
        onDismiss = { dialog = ""; prefs.edit().putBoolean("declined", true).apply() })
    if (dialog == "settings") FamilyPermissionDialog(Icons.Default.LocationOff, t("Location is off for Sahara", "Sahara के लिए जगह बंद है"),
        t("Turn on Location in Settings to see the closest centres first.", "सबसे पास के केंद्र पहले देखने के लिए सेटिंग में जगह (Location) चालू करें।"),
        t("Open Settings", "सेटिंग खोलें"), onConfirm = { dialog = ""; open(context, Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) },
        onDismiss = { dialog = "" })
    return NearbyLocation(here) { if (granted()) read() else if (prefs.getBoolean("blocked", false)) dialog = "settings" else dialog = "why" }
}

internal tailrec fun nearbyActivity(context: Context): android.app.Activity? = when (context) {
    is android.app.Activity -> context
    is android.content.ContextWrapper -> nearbyActivity(context.baseContext)
    else -> null
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
