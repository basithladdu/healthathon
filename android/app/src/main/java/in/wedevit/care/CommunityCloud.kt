package `in`.wedevit.care

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Community calls (20261106120000_community.sql). Every one runs as the signed-in member; nothing is public. */
internal suspend fun CareCloud.communityHome(): CommunityHome =
    CommunityJson.home(JSONObject(request("POST", "/rest/v1/rpc/sahara_community_home", JSONObject())))

internal suspend fun CareCloud.communityFeed(groupId: String): CommunityFeed =
    CommunityJson.feed(JSONObject(request("POST", "/rest/v1/rpc/sahara_community_feed", JSONObject().put("p_group", groupId))))

internal suspend fun CareCloud.communitySetName(name: String): String =
    JSONObject(request("POST", "/rest/v1/rpc/sahara_community_set_name", JSONObject().put("p_name", name.trim()))).getString("my_name")

/** Idempotent on the item's id, so an outbox retry after a lost reply never posts twice. */
internal suspend fun CareCloud.communitySend(item: CommunityPending) {
    val body = if (item.kind == CommunityPending.POST) JSONObject().put("p_id", item.id).put("p_group", item.target).put("p_body", item.body)
        else JSONObject().put("p_id", item.id).put("p_post", item.target).put("p_body", item.body)
    val reply = JSONObject(request("POST", "/rest/v1/rpc/sahara_community_${item.kind}", body, expectedAccountId = item.accountId))
    require(reply.optString("id") == item.id && reply.optString("status") == "received") { "Couldn't reach the community." }
}

internal suspend fun CareCloud.communityReact(postId: String, kind: String, on: Boolean) {
    request("POST", "/rest/v1/rpc/sahara_community_react", JSONObject().put("p_post", postId).put("p_kind", kind).put("p_on", on))
}

internal suspend fun CareCloud.communityReport(postId: String?, replyId: String?, reason: String) {
    request("POST", "/rest/v1/rpc/sahara_community_report", JSONObject().put("p_post", postId ?: JSONObject.NULL).put("p_reply", replyId ?: JSONObject.NULL).put("p_reason", reason).put("p_note", ""))
}

internal suspend fun CareCloud.communityRemoveMine(kind: String, id: String) {
    request("POST", "/rest/v1/rpc/sahara_community_remove_mine", JSONObject().put("p_kind", kind).put("p_id", id))
}

internal suspend fun CareCloud.communityModerate(kind: String, id: String, hide: Boolean) {
    request("POST", "/rest/v1/rpc/sahara_community_moderate", JSONObject().put("p_kind", kind).put("p_id", id).put("p_hide", hide))
}

/** Posts and replies written while offline, kept on this phone per account until the server accepts them. Community text only, never care records. */
internal class CommunityStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("sahara-community-v1", Context.MODE_PRIVATE)
    @Synchronized private fun all(): List<CommunityPending> = runCatching { CommunityOutbox.decode(preferences.getString("pending", null)) }.getOrDefault(emptyList())
    fun pending(accountId: String): List<CommunityPending> = all().filter { it.accountId == accountId }
    /** False when the outbox is full or the phone could not save it. */
    @Synchronized fun add(item: CommunityPending): Boolean {
        val next = CommunityOutbox.add(all(), item) ?: return false
        return preferences.edit().putString("pending", CommunityOutbox.encode(next)).commit()
    }
    @Synchronized fun remove(id: String): Boolean = preferences.edit().putString("pending", CommunityOutbox.encode(all().filterNot { it.id == id })).commit()
    suspend fun flush(accountId: String, send: suspend (CommunityPending) -> Unit): Throwable? = sending.withLock {
        CommunityOutbox.flush(all(), accountId, send) { remaining -> synchronized(this) { preferences.edit().putString("pending", CommunityOutbox.encode(remaining)).commit() } }
    }
    /** The built-in community and what this phone added to it (used while the server can't be). Unreadable data starts fresh. */
    @Synchronized fun local(): CommunityLocalState = runCatching { CommunityLocal.decode(preferences.getString("local", null)) }.getOrDefault(CommunityLocalState())
    @Synchronized fun saveLocal(state: CommunityLocalState): Boolean = preferences.edit().putString("local", CommunityLocal.encode(state)).commit()
    private companion object { val sending = Mutex() }
}
