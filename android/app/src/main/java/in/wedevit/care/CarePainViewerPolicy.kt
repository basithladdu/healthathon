package `in`.wedevit.care

import org.json.JSONObject
import java.net.URI

/** Public bundled rendering files only. No network destination, patient text or native command is accepted. */
object PainViewerPolicy {
    const val ORIGIN = "https://appassets.androidplatform.net"
    const val PAGE = "$ORIGIN/assets/pain-body-viewer/index.html"
    fun localResource(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "appassets.androidplatform.net" && uri.port in setOf(-1, 443) &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            uri.path.matches(Regex("/assets/pain-body-viewer/[A-Za-z0-9_-][A-Za-z0-9_.-]*")) && !uri.path.contains("..")
    }.getOrDefault(false)
    fun trustedOrigin(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "appassets.androidplatform.net" && uri.port in setOf(-1, 443) && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.orEmpty() in setOf("", "/")
    }.getOrDefault(false)
    /** True once a touch on the 3D model should stay with the model: a second finger (pinch) or a mostly sideways drag. Up/down drags return false so the page scrolls. */
    fun viewerKeepsGesture(dx: Float, dy: Float, pointerCount: Int, touchSlop: Float): Boolean =
        pointerCount > 1 || (kotlin.math.abs(dx) >= touchSlop / 2 && kotlin.math.abs(dx) > kotlin.math.abs(dy))
    fun toggledSite(message: String): String? = if (message.length > 180) null else runCatching {
        val json = JSONObject(message)
        json.optString("site").takeIf { json.optString("type") == "toggle" && it in PainBodyMapRules.sites }
    }.getOrNull()
    fun pickedPoint(message: String): PainBodyPoint3d? = if (message.length > 180) null else runCatching {
        val json = JSONObject(message); val point = json.getJSONObject("point")
        PainBodyPoint3d(json.getString("site"), point.getDouble("x"), point.getDouble("y"), point.getDouble("z")).takeIf { json.optString("type") == "pick" && PainBodyMapRules.validPoint(it) }
    }.getOrNull()
}
