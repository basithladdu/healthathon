package `in`.wedevit.care

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToLong
import org.json.JSONArray
import org.json.JSONObject

/** Shared renderer coordinates: patient's left +x, feet-to-head y, front +z. */
data class PainBodyPoint3d(val site: String, val x: Double, val y: Double, val z: Double)

data class PainBodyPoint(val x: Float, val y: Float)
data class PainBodyRegion(val id: String, val outline: List<PainBodyPoint>) {
    fun contains(point: PainBodyPoint): Boolean {
        var inside = false
        var previous = outline.last()
        outline.forEach { current ->
            if ((current.y > point.y) != (previous.y > point.y) &&
                point.x < (previous.x - current.x) * (point.y - current.y) / (previous.y - current.y) + current.x) inside = !inside
            previous = current
        }
        return inside
    }
}

/** Original coarse body regions, in a 100 by 200 coordinate space. Sides always refer to the patient. */
object PainBodyMapRules {
    const val MODEL = "sahara-body-v1"
    /** Wong-Baker style solid blocks as opaque ARGB: green, light green, yellow, orange, orange-red, red for ratings 0-1, 2-3, 4-5, 6-7, 8-9, 10. The 3D viewer uses the same colours. */
    val heatStops = listOf(0xFF4CA64CL, 0xFF8CC152L, 0xFFF6D743L, 0xFFF2A33AL, 0xFFE8693AL, 0xFFD9363EL)
    /** Rating 0 carries no heat colour: it is this neutral grey (thumb, dot), never green. Green starts at 1. */
    const val NEUTRAL = 0xFFB9B0A6L
    fun heatColor(rating: Int): Long = if (rating <= 0) NEUTRAL else heatStops[minOf(heatStops.size - 1, rating.coerceIn(0, 10) / 2)]
    /** The note's overall score from per-spot ratings: the highest rated spot, or null when no spot is rated. */
    fun overall(ratings: Map<String, Int>): Int? = ratings.values.maxOrNull()
    /** Per-spot ratings (site id to 0-10) as a compact saveable string "head:7|neck:4", and back; unknown ids and bad values are dropped. */
    fun ratingsKey(ratings: Map<String, Int>): String = sites.keys.filter { it in ratings }.joinToString("|") { "$it:${ratings.getValue(it)}" }
    fun parseRatingsKey(text: String): Map<String, Int> = text.split('|').mapNotNull { part -> part.split(':').takeIf { it.size == 2 && it[0] in sites }?.let { p -> p[1].toIntOrNull()?.takeIf { it in 0..10 }?.let { p[0] to it } } }.toMap()
    fun encodeRatings(ratings: Map<String, Int>): JSONObject = JSONObject().also { o -> sites.keys.filter { it in ratings }.forEach { o.put(it, ratings.getValue(it)) } }
    fun decodeRatings(value: JSONObject?): Map<String, Int> = value?.let { o -> o.keys().asSequence().associateWith { o.getInt(it) } }.orEmpty()
    /** Plain-word band for the pain strength slider; 0 None, 1-3 Mild, 4-6 Moderate, 7-9 Severe, 10 Worst. */
    fun strengthWord(rating: Int, hindi: Boolean): String = when (rating.coerceIn(0, 10)) {
        0 -> if (hindi) "कोई नहीं" else "None"; in 1..3 -> if (hindi) "हल्का" else "Mild"; in 4..6 -> if (hindi) "मध्यम" else "Moderate"
        in 7..9 -> if (hindi) "तेज़" else "Severe"; else -> if (hindi) "सबसे ज़्यादा" else "Worst"
    }
    val sites = linkedMapOf(
        "head" to ("Head" to "सिर"), "neck" to ("Neck" to "गर्दन"), "chest" to ("Chest" to "छाती"),
        "abdomen" to ("Abdomen" to "पेट"), "upper-back" to ("Upper back" to "पीठ का ऊपरी हिस्सा"),
        "lower-back" to ("Lower back" to "पीठ का निचला हिस्सा"), "left-shoulder" to ("Left shoulder" to "बायाँ कंधा"),
        "right-shoulder" to ("Right shoulder" to "दायाँ कंधा"), "left-arm" to ("Left arm / hand" to "बायाँ हाथ"),
        "right-arm" to ("Right arm / hand" to "दायाँ हाथ"), "left-leg" to ("Left leg / foot" to "बायाँ पैर"), "right-leg" to ("Right leg / foot" to "दायाँ पैर"),
    )
    private val frontOnly = setOf("chest", "abdomen")
    private val backOnly = setOf("upper-back", "lower-back")
    /** True when the region can be seen from that side of the 3D model (shared regions appear on both). */
    fun visibleFrom(id: String, back: Boolean): Boolean = id in sites && if (back) id !in frontOnly else id !in backOnly
    /** Region ids offered as zoom targets for one side, in body order top to bottom. */
    fun sitesFor(back: Boolean): List<String> = sites.keys.filter { visibleFrom(it, back) }
    /** Label for the zoom list; the head and neck read differently when viewed from behind. */
    fun focusLabel(id: String, back: Boolean, hindi: Boolean): String = when {
        back && id == "head" -> if (hindi) "सिर का पिछला हिस्सा" else "Back of head"
        back && id == "neck" -> if (hindi) "गर्दन (पीछे)" else "Neck (back)"
        else -> label(id, hindi)
    }
    fun label(id: String, hindi: Boolean): String = sites[id]?.let { if (hindi) it.second else it.first } ?: id
    /** A spot's score as TalkBack reads it: "7 of 10, Severe" / "10 में से 7, तेज़", or "not rated". */
    fun spokenScore(rating: Int?, hindi: Boolean): String = when {
        rating == null -> if (hindi) "अंक नहीं" else "not rated"
        hindi -> "10 में से ${rating.coerceIn(0, 10)}, ${strengthWord(rating, true)}"
        else -> "${rating.coerceIn(0, 10)} of 10, ${strengthWord(rating, false)}"
    }
    /** What TalkBack says for the body map: every marked part by name with its score, in body order. */
    fun spokenSummary(selected: List<String>, ratings: Map<String, Int>, hindi: Boolean): String {
        val parts = sites.keys.filter { it in selected }
        if (parts.isEmpty()) return if (hindi) "अभी दर्द की कोई जगह चिह्नित नहीं" else "No pain areas marked yet"
        return (if (hindi) "चिह्नित दर्द: " else "Pain marked: ") + parts.joinToString("; ") { "${label(it, hindi)}, ${spokenScore(ratings[it], hindi)}" }
    }
    fun validPoint(point: PainBodyPoint3d): Boolean = point.site in sites && point.x.isFinite() && point.y.isFinite() && point.z.isFinite() && point.x in -1.0..1.0 && point.y in 0.0..1.0 && point.z in -1.0..1.0
    fun pointContext(points: List<PainBodyPoint3d>) = points.sortedBy { it.site }.map { "${it.site}:${(it.x * 1000).roundToLong()}:${(it.y * 1000).roundToLong()}:${(it.z * 1000).roundToLong()}" }
    fun encodePoints(points: List<PainBodyPoint3d>): JSONArray = JSONArray(points.sortedBy { it.site }.map { JSONObject().put("site", it.site).put("x", it.x).put("y", it.y).put("z", it.z) })
    fun decodePoints(values: JSONArray?): List<PainBodyPoint3d> = values?.let { list -> (0 until list.length()).map { index ->
        val point = list.getJSONObject(index)
        PainBodyPoint3d(point.getString("site"), point.getDouble("x"), point.getDouble("y"), point.getDouble("z"))
    } }.orEmpty()
    private fun polygon(id: String, vararg xy: Float) = PainBodyRegion(id, xy.toList().chunked(2).map { PainBodyPoint(it[0], it[1]) })
    private fun mirrored(region: PainBodyRegion, id: String) = PainBodyRegion(id, region.outline.map { PainBodyPoint(100f - it.x, it.y) })
    fun regions(back: Boolean): List<PainBodyRegion> {
        val viewerLeftSide = if (back) "left" else "right"
        val viewerRightSide = if (back) "right" else "left"
        val shoulder = polygon("$viewerLeftSide-shoulder", 39f, 34f, 30f, 37f, 26f, 48f, 36f, 52f, 40f, 43f)
        val arm = polygon("$viewerLeftSide-arm", 26f, 49f, 36f, 53f, 31f, 82f, 25f, 103f, 19f, 109f, 15f, 104f, 21f, 81f)
        val leg = polygon("$viewerLeftSide-leg", 36f, 104f, 49f, 104f, 48f, 148f, 47f, 193f, 31f, 193f, 30f, 188f, 37f, 183f, 37f, 147f)
        val head = PainBodyRegion("head", (0 until 24).map { step ->
            val angle = step * 2.0 * PI / 24
            PainBodyPoint(50f + (9f * cos(angle)).toFloat(), 15f + (12f * sin(angle)).toFloat())
        })
        return listOf(head, polygon("neck", 45f, 28f, 55f, 28f, 56f, 35f, 44f, 35f), shoulder,
            mirrored(shoulder, "$viewerRightSide-shoulder"), arm, mirrored(arm, "$viewerRightSide-arm"),
            polygon(if (back) "upper-back" else "chest", 40f, 35f, 60f, 35f, 64f, 68f, 36f, 68f),
            polygon(if (back) "lower-back" else "abdomen", 36f, 69f, 64f, 69f, 65f, 102f, 35f, 102f),
            leg, mirrored(leg, "$viewerRightSide-leg"))
    }
    fun hit(back: Boolean, x: Float, y: Float): String? = regions(back).firstOrNull { it.contains(PainBodyPoint(x, y)) }?.id
    /** Adds one area without ever removing it (the "Mark whole part" action); unknown ids change nothing. */
    fun marked(selected: List<String>, id: String): List<String> = if (id !in sites) selected else sites.keys.filter { it in selected || it == id }
    /** Removes one area (the "Remove" action on the zoomed part). */
    fun unmarked(selected: List<String>, id: String): List<String> = selected.filter { it != id }
    fun toggled(selected: List<String>, id: String): List<String> {
        if (id !in sites) return selected
        val next = selected.toMutableSet()
        if (!next.add(id)) next.remove(id)
        return sites.keys.filter { it in next }
    }
}
