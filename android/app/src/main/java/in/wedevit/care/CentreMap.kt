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
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

@Composable fun CentreMap(centres: List<Centre>, onSelect: (Centre) -> Unit) {
    val context = LocalContext.current
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
        MapLibre.getInstance(context)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
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
                    val hit = ready.queryRenderedFeatures(area, "care-pins").firstOrNull()
                    currentCentres.firstOrNull { it.id == hit?.getStringProperty("id") }?.let(select)
                    hit != null
                }
                ready.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                    style.addSource(GeoJsonSource("care-centres", points(currentCentres)))
                    style.addLayer(CircleLayer("care-pins", "care-centres").withProperties(circleColor("#B9472C"), circleRadius(8f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
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
    Box(Modifier.fillMaxWidth().height(260.dp).testTag("centre-map").semantics { stateDescription = if (failed) "Map unavailable" else if (loaded) "Map ready" else "Loading map" }) {
        key(retry) { AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) }
        if (!loaded && !failed) CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.Center))
        if (failed) Surface(Modifier.align(Alignment.Center).padding(8.dp), color = Paper) { Column(Modifier.padding(12.dp)) { Text("Couldn't load the map.", style = MaterialTheme.typography.bodySmall); TextButton(onClick = { failed = false; loaded = false; retry++ }) { Text("Try again") } } }
        val located = centres.filter { it.latitude != null && it.longitude != null }
        if (loaded && located.isNotEmpty()) FilledTonalButton(onClick = {
            val update = bounds(located)
            if (ValueAnimator.areAnimatorsEnabled()) map?.animateCamera(update, 350) else map?.moveCamera(update)
        }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Text("Show all") }
    }
}

private fun bounds(centres: List<Centre>) = if (centres.size == 1) CameraUpdateFactory.newLatLngZoom(LatLng(centres[0].latitude!!, centres[0].longitude!!), 13.0)
else CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(centres.map { LatLng(it.latitude!!, it.longitude!!) }).build(), 48)

private fun points(centres: List<Centre>) = FeatureCollection.fromFeatures(centres.mapNotNull { centre ->
    if (centre.latitude == null || centre.longitude == null) null else Feature.fromGeometry(Point.fromLngLat(centre.longitude, centre.latitude)).apply { addStringProperty("id", centre.id) }
})
