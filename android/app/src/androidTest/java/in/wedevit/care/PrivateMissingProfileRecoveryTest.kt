package `in`.wedevit.care

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Manual, argument-gated missing-row recovery. No fixture/private content is compiled here. */
class PrivateMissingProfileRecoveryTest {
    @Test fun restoreOnlyAbsentProfileAndDraft() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("restoreMissingPrivateProfile") == "explicitly-authorized")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val payload = File(context.cacheDir, "authorized-missing-profile.json")
        val bytes = payload.readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(hash == args.getString("privatePayloadSha256")) { "Recovery payload integrity failed" }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        val person = root.getJSONObject("Person")
        val draft = root.getJSONObject("Conversation")
        val id = person.getString("id")
        UUID.fromString(id)
        check(draft.getString("personId") == id) { "Recovery rows must match" }
        SQLiteDatabase.openDatabase(context.getDatabasePath("care.db").path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.beginTransaction()
            try {
                fun absent(table: String, key: String) = db.rawQuery("SELECT count(*) FROM $table WHERE $key = ?", arrayOf(id)).use { it.moveToFirst(); it.getLong(0) == 0L }
                check(absent("Person", "id") && absent("Conversation", "personId")) { "Recovery aborted: existing target row" }
                fun insert(table: String, row: JSONObject) {
                    val columns = db.rawQuery("PRAGMA table_info($table)", null).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) } }
                    check(row.keys().asSequence().toSet() == columns) { "Recovery schema mismatch" }
                    val values = ContentValues()
                    columns.forEach { key -> when (val value = row.get(key)) {
                        JSONObject.NULL -> values.putNull(key)
                        is Number -> values.put(key, value.toLong())
                        is String -> values.put(key, value)
                        else -> error("Unsupported recovery value")
                    } }
                    db.insertOrThrow(table, null, values)
                }
                insert("Person", person)
                insert("Conversation", draft)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        val preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        if (preferences.getString("person", "").isNullOrBlank()) check(preferences.edit().putString("person", id).commit())
    }
}
