package `in`.wedevit.care

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.RenderingEngine
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Every mapped centre, clustered. Tapping a pin selects the centres under it; tapping a cluster zooms in, or lists it when it is all one place. */
@Composable fun CentreMap(centres: List<Centre>, onSelect: (List<Centre>) -> Unit) {
    val context = LocalContext.current; val t = careText()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentCentres by rememberUpdatedState(centres)
    val select by rememberUpdatedState(onSelect)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var latitude by rememberSaveable { mutableDoubleStateOf(13.00) }
    var longitude by rememberSaveable { mutableDoubleStateOf(77.63) }
    var zoom by rememberSaveable { mutableDoubleStateOf(9.5) }
    var filterSignature by rememberSaveable { mutableStateOf("") }
    val view = remember(retry) {
        // Some Android 17 emulators advertise Vulkan but cannot initialize it.
        MapLibre.getInstance(context, null, WellKnownTileServer.MapLibre, RenderingEngine.Type.OPENGL)
        MapView(context, MapLibreMapOptions.createFromAttributes(context)).apply {
            onCreate(null)
            setOnTouchListener { view, event ->
                view.parent?.requestDisallowInterceptTouchEvent(event.actionMasked != android.view.MotionEvent.ACTION_UP && event.actionMasked != android.view.MotionEvent.ACTION_CANCEL)
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP) view.performClick()
                false
            }
            addOnDidFailLoadingMapListener { failed = true }
            getMapAsync { ready ->
                map = ready
                ready.uiSettings.isLogoEnabled = false
                ready.uiSettings.isAttributionEnabled = true
                ready.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoom))
                ready.addOnCameraIdleListener {
                    ready.cameraPosition.target?.let { latitude = it.latitude; longitude = it.longitude }
                    zoom = ready.cameraPosition.zoom
                }
                ready.addOnMapClickListener { point ->
                    val screen = ready.projection.toScreenLocation(point)
                    val area = android.graphics.RectF(screen.x - 22, screen.y - 22, screen.x + 22, screen.y + 22)
                    val cluster = ready.queryRenderedFeatures(area, "care-clusters").firstOrNull()
                    val source = ready.style?.getSourceAs<GeoJsonSource>("care-centres")
                    if (cluster != null && source != null) {
                        val leaves = source.getClusterLeaves(cluster, cluster.getNumberProperty("point_count")?.toLong() ?: 0L, 0L).features().orEmpty()
                        val spots = leaves.mapNotNull { (it.geometry() as? Point)?.let { p -> p.latitude() to p.longitude() } }
                        val target = (cluster.geometry() as? Point)?.let { LatLng(it.latitude(), it.longitude()) }
                        val zoomTo = CentreLocation.clusterTapZoom(ready.cameraPosition.zoom, source.getClusterExpansionZoom(cluster), spots)
                        if (zoomTo != null && target != null) {
                            val update = CameraUpdateFactory.newLatLngZoom(target, zoomTo)
                            if (ValueAnimator.areAnimatorsEnabled()) ready.animateCamera(update, 350) else ready.moveCamera(update)
                        } else {
                            val ids = leaves.mapNotNull { it.getStringProperty("id") }.toSet()
                            select(currentCentres.filter { it.id in ids })
                        }
                        return@addOnMapClickListener true
                    }
                    val ids = ready.queryRenderedFeatures(area, "care-pins", "care-pins-approx").mapNotNull { it.getStringProperty("id") }.toSet()
                    val hits = currentCentres.filter { it.id in ids }
                    if (hits.isNotEmpty()) select(hits)
                    hits.isNotEmpty()
                }
                ready.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                    style.addSource(GeoJsonSource("care-centres", points(currentCentres), GeoJsonOptions().withCluster(true)
                        .withClusterRadius(CentreLocation.CLUSTER_RADIUS).withClusterMaxZoom(CentreLocation.CLUSTER_MAX_ZOOM)))
                    val single = not(has("point_count"))
                    style.addLayer(CircleLayer("care-clusters", "care-centres").withFilter(has("point_count")).withProperties(circleColor("#B9472C"),
                        circleRadius(step(get("point_count"), literal(15f), stop(10, 19f), stop(100, 24f))), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
                    style.addLayer(SymbolLayer("care-cluster-count", "care-centres").withFilter(has("point_count")).withProperties(
                        textField(Expression.toString(get("point_count_abbreviated"))), textFont(arrayOf("Noto Sans Bold")), textSize(13f), textColor("#FFFFFF"),
                        textAllowOverlap(true), textIgnorePlacement(true)))
                    // Approximate pins (PIN, town or district area) are hollow; exact ones stay solid.
                    style.addLayer(CircleLayer("care-pins-approx", "care-centres").withFilter(all(single, eq(get("approx"), literal(true))))
                        .withProperties(circleColor("#FFFFFF"), circleRadius(7f), circleStrokeColor("#B9472C"), circleStrokeWidth(3f)))
                    style.addLayer(CircleLayer("care-pins", "care-centres").withFilter(all(single, eq(get("approx"), literal(false))))
                        .withProperties(circleColor("#B9472C"), circleRadius(8f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
                    loaded = true; failed = false
                }
            }
        }
    }
    DisposableEffect(view, lifecycle) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> view.onStart()
            Lifecycle.Event.ON_RESUME -> view.onResume()
            Lifecycle.Event.ON_PAUSE -> view.onPause()
            Lifecycle.Event.ON_STOP -> view.onStop()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); view.onPause(); view.onStop(); view.onDestroy() }
    }
    LaunchedEffect(centres, loaded) {
        if (loaded) {
            map?.style?.getSourceAs<GeoJsonSource>("care-centres")?.setGeoJson(points(centres))
            val signature = centres.joinToString { it.id }
            if (signature != filterSignature) {
                filterSignature = signature
                val located = centres.filter { it.latitude != null && it.longitude != null }
                if (located.isNotEmpty()) map?.moveCamera(bounds(located))
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(260.dp).testTag("centre-map").semantics { stateDescription = if (failed) t("Map unavailable", "नक्शा उपलब्ध नहीं") else if (loaded) t("Map ready", "नक्शा तैयार") else t("Loading map", "नक्शा खुल रहा है") }) {
        key(retry) { AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) }
        if (!loaded && !failed) CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.Center))
        if (failed) Surface(Modifier.align(Alignment.Center).padding(8.dp), color = Paper) { Column(Modifier.padding(12.dp)) { Text(t("Couldn't load the map.", "नक्शा नहीं खुल सका।"), style = MaterialTheme.typography.bodySmall); TextButton(onClick = { failed = false; loaded = false; retry++ }) { Text(t("Try again", "फिर कोशिश करें")) } } }
        val located = centres.filter { it.latitude != null && it.longitude != null }
        if (loaded && located.isNotEmpty()) FilledTonalButton(onClick = {
            val update = bounds(located)
            if (ValueAnimator.areAnimatorsEnabled()) map?.animateCamera(update, 350) else map?.moveCamera(update)
        }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Text(t("Show all", "सभी दिखाएँ")) }
    }
}

private fun bounds(centres: List<Centre>) = if (centres.size == 1) CameraUpdateFactory.newLatLngZoom(LatLng(centres[0].latitude!!, centres[0].longitude!!), 13.0)
else CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(centres.map { LatLng(it.latitude!!, it.longitude!!) }).build(), 48)

private fun points(centres: List<Centre>) = FeatureCollection.fromFeatures(CentreLocation.pins(centres).map { pin ->
    Feature.fromGeometry(Point.fromLngLat(pin.longitude, pin.latitude)).apply { addStringProperty("id", pin.id); addBooleanProperty("approx", pin.approximate) }
})
