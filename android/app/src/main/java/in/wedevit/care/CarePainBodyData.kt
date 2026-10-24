package `in`.wedevit.care

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    val sites = linkedMapOf(
        "head" to ("Head" to "सिर"), "neck" to ("Neck" to "गर्दन"), "chest" to ("Chest" to "छाती"),
        "abdomen" to ("Abdomen" to "पेट"), "upper-back" to ("Upper back" to "पीठ का ऊपरी हिस्सा"),
        "lower-back" to ("Lower back" to "पीठ का निचला हिस्सा"), "left-shoulder" to ("Left shoulder" to "बायाँ कंधा"),
        "right-shoulder" to ("Right shoulder" to "दायाँ कंधा"), "left-arm" to ("Left arm" to "बायाँ हाथ"),
        "right-arm" to ("Right arm" to "दायाँ हाथ"), "left-leg" to ("Left leg" to "बायाँ पैर"), "right-leg" to ("Right leg" to "दायाँ पैर"),
    )
    fun label(id: String, hindi: Boolean): String = sites[id]?.let { if (hindi) it.second else it.first } ?: id
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
    fun toggled(selected: List<String>, id: String): List<String> = if (id !in sites) selected else
        sites.keys.filter { it in selected.toSet().let { values -> if (id in values) values - id else values + id } }
}
