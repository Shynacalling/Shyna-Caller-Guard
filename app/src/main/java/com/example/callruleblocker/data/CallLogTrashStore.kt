package com.example.callruleblocker.data

import android.content.ContentValues
import android.content.Context
import android.provider.CallLog
import org.json.JSONArray
import org.json.JSONObject

private const val CALL_TRASH_PREFS = "call_log_recycle_bin"
private const val CALL_TRASH_KEY = "items"
private const val CALL_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L

data class TrashedCallEntry(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val durationSeconds: Long,
    val phoneAccountId: String?,
    val features: Int
)

data class TrashedCallGroup(
    val trashId: Long,
    val deletedAt: Long,
    val displayNumber: String,
    val entries: List<TrashedCallEntry>
)

class CallLogTrashStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(CALL_TRASH_PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun add(displayNumber: String, entries: List<TrashedCallEntry>) {
        if (entries.isEmpty()) return
        purgeExpired()
        val now = System.currentTimeMillis()
        val items = readMutable()
        items += TrashedCallGroup(now, now, displayNumber, entries)
        write(items)
    }

    @Synchronized
    fun list(): List<TrashedCallGroup> {
        purgeExpired()
        return readMutable().sortedByDescending { it.deletedAt }
    }

    @Synchronized
    fun restore(item: TrashedCallGroup): Int {
        var restored = 0
        item.entries.forEach { entry ->
            val values = ContentValues().apply {
                put(CallLog.Calls.NUMBER, entry.number)
                put(CallLog.Calls.CACHED_NAME, entry.name)
                put(CallLog.Calls.TYPE, entry.type)
                put(CallLog.Calls.DATE, entry.date)
                put(CallLog.Calls.DURATION, entry.durationSeconds)
                put(CallLog.Calls.PHONE_ACCOUNT_ID, entry.phoneAccountId)
                put(CallLog.Calls.FEATURES, entry.features)
                put(CallLog.Calls.NEW, 0)
            }
            if (context.contentResolver.insert(CallLog.Calls.CONTENT_URI, values) != null) restored++
        }
        if (restored == item.entries.size) remove(item.trashId)
        return restored
    }

    @Synchronized fun remove(trashId: Long) = write(readMutable().filterNot { it.trashId == trashId })
    @Synchronized fun clear() = prefs.edit().remove(CALL_TRASH_KEY).apply()

    @Synchronized
    fun purgeExpired(now: Long = System.currentTimeMillis()) {
        write(readMutable().filter { now - it.deletedAt < CALL_RETENTION_MS })
    }

    private fun readMutable(): MutableList<TrashedCallGroup> {
        val raw = prefs.getString(CALL_TRASH_KEY, null) ?: return mutableListOf()
        return runCatching {
            val array = JSONArray(raw)
            MutableList(array.length()) { index ->
                val item = array.getJSONObject(index)
                val callsJson = item.getJSONArray("entries")
                val calls = List(callsJson.length()) { callIndex ->
                    val c = callsJson.getJSONObject(callIndex)
                    TrashedCallEntry(
                        number = c.optString("number", ""),
                        name = c.optString("name", "").takeIf { it.isNotBlank() },
                        type = c.optInt("type", CallLog.Calls.INCOMING_TYPE),
                        date = c.optLong("date", 0L),
                        durationSeconds = c.optLong("duration", 0L),
                        phoneAccountId = c.optString("account", "").takeIf { it.isNotBlank() },
                        features = c.optInt("features", 0)
                    )
                }
                TrashedCallGroup(
                    trashId = item.getLong("trashId"),
                    deletedAt = item.getLong("deletedAt"),
                    displayNumber = item.optString("displayNumber", calls.firstOrNull()?.number.orEmpty()),
                    entries = calls
                )
            }
        }.getOrElse { mutableListOf() }
    }

    private fun write(items: List<TrashedCallGroup>) {
        val array = JSONArray()
        items.forEach { item ->
            val calls = JSONArray()
            item.entries.forEach { entry ->
                calls.put(JSONObject()
                    .put("number", entry.number)
                    .put("name", entry.name ?: "")
                    .put("type", entry.type)
                    .put("date", entry.date)
                    .put("duration", entry.durationSeconds)
                    .put("account", entry.phoneAccountId ?: "")
                    .put("features", entry.features))
            }
            array.put(JSONObject()
                .put("trashId", item.trashId)
                .put("deletedAt", item.deletedAt)
                .put("displayNumber", item.displayNumber)
                .put("entries", calls))
        }
        prefs.edit().putString(CALL_TRASH_KEY, array.toString()).apply()
    }
}
