package com.example.callruleblocker.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Keeps a short local audit of calls rejected by this app's block rules. */
class BlockedCallStore(context: Context) {
    private val prefs = context.getSharedPreferences("blocked_call_audit", Context.MODE_PRIVATE)

    data class Entry(val number: String, val time: Long, val simSlot: Int)

    @Synchronized
    fun record(number: String, simSlot: Int, time: Long = System.currentTimeMillis()) {
        val normalized = normalize(number)
        val entries = read().filter { time - it.time < RETENTION_MS }.toMutableList()
        
        // De-duplicate: If a block was already recorded for this number within 15 seconds, skip
        if (entries.any { it.number == normalized && kotlin.math.abs(it.time - time) < 15000 }) {
            return
        }

        entries.add(0, Entry(normalized, time, simSlot))
        val array = JSONArray()
        entries.take(MAX_ENTRIES).forEach { entry ->
            array.put(JSONObject().apply {
                put("number", entry.number)
                put("time", entry.time)
                put("simSlot", entry.simSlot)
            })
        }
        prefs.edit().putString("entries", array.toString()).apply()
    }

    fun find(number: String, callTime: Long, toleranceMs: Long = 20_000L): Entry? {
        val normalized = normalize(number)
        return read().firstOrNull { it.number == normalized && kotlin.math.abs(it.time - callTime) <= toleranceMs }
    }

    fun getAll(): List<Entry> = read()

    private fun read(): List<Entry> = runCatching {
        val array = JSONArray(prefs.getString("entries", "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(Entry(item.optString("number"), item.optLong("time"), item.optInt("simSlot", -1)))
            }
        }
    }.getOrDefault(emptyList())

    private fun normalize(value: String) = value.filter(Char::isDigit).takeLast(10)

    companion object {
        private const val MAX_ENTRIES = 500
        private const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
    }
}
