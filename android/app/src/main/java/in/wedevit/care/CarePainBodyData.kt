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
    val sites = linkedMapOf(
        "head" to ("Head" to "सिर"), "neck" to ("Neck" to "गर्दन"), "chest" to ("Chest" to "छाती"),
        "abdomen" to ("Abdomen" to "पेट"), "upper-back" to ("Upper back" to "पीठ का ऊपरी हिस्सा"),
        "lower-back" to ("Lower back" to "पीठ का निचला हिस्सा"), "left-shoulder" to ("Left shoulder" to "बायाँ कंधा"),
        "right-shoulder" to ("Right shoulder" to "दायाँ कंधा"), "left-arm" to ("Left arm / hand" to "बायाँ हाथ"),
        "right-arm" to ("Right arm / hand" to "दायाँ हाथ"), "left-leg" to ("Left leg / foot" to "बायाँ पैर"), "right-leg" to ("Right leg / foot" to "दायाँ पैर"),
    )
    fun label(id: String, hindi: Boolean): String = sites[id]?.let { if (hindi) it.second else it.first } ?: id
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
    fun toggled(selected: List<String>, id: String): List<String> {
        if (id !in sites) return selected
        val next = selected.toMutableSet()
        if (!next.add(id)) next.remove(id)
        return sites.keys.filter { it in next }
    }
}
