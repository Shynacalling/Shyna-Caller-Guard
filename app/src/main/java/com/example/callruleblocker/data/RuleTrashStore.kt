package com.example.callruleblocker.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val TRASH_PREFS = "rule_recycle_bin"
private const val TRASH_KEY = "items"
private const val RETENTION_MS = 30L * 24L * 60L * 60L * 1000L

data class TrashedRule(
    val trashId: Long,
    val deletedAt: Long,
    val rule: Rule
)

class RuleTrashStore(context: Context) {
    private val prefs = context.getSharedPreferences(TRASH_PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun add(rule: Rule) {
        purgeExpired()
        val items = readMutable()
        items += TrashedRule(
            trashId = System.currentTimeMillis(),
            deletedAt = System.currentTimeMillis(),
            rule = rule
        )
        write(items)
    }

    @Synchronized
    fun list(): List<TrashedRule> {
        purgeExpired()
        return readMutable().sortedByDescending { it.deletedAt }
    }

    @Synchronized
    fun remove(trashId: Long) {
        write(readMutable().filterNot { it.trashId == trashId })
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(TRASH_KEY).apply()
    }

    @Synchronized
    fun purgeExpired(now: Long = System.currentTimeMillis()) {
        val remaining = readMutable().filter { now - it.deletedAt < RETENTION_MS }
        write(remaining)
    }

    private fun readMutable(): MutableList<TrashedRule> {
        val raw = prefs.getString(TRASH_KEY, null) ?: return mutableListOf()
        return runCatching {
            val array = JSONArray(raw)
            MutableList(array.length()) { index ->
                val item = array.getJSONObject(index)
                val ruleJson = item.getJSONObject("rule")
                TrashedRule(
                    trashId = item.getLong("trashId"),
                    deletedAt = item.getLong("deletedAt"),
                    rule = Rule(
                        id = ruleJson.optLong("id", 0L),
                        simSlotIndex = ruleJson.optInt("simSlotIndex", 0),
                        matchType = ruleJson.optString("matchType", "SPECIFIC_NUMBER"),
                        matchValue = ruleJson.optString("matchValue", ""),
                        action = ruleJson.optString("action", "BLOCK"),
                        enabled = ruleJson.optBoolean("enabled", true)
                    )
                )
            }
        }.getOrElse { mutableListOf() }
    }

    private fun write(items: List<TrashedRule>) {
        val array = JSONArray()
        items.forEach { item ->
            val rule = JSONObject()
                .put("id", item.rule.id)
                .put("simSlotIndex", item.rule.simSlotIndex)
                .put("matchType", item.rule.matchType)
                .put("matchValue", item.rule.matchValue)
                .put("action", item.rule.action)
                .put("enabled", item.rule.enabled)
            array.put(
                JSONObject()
                    .put("trashId", item.trashId)
                    .put("deletedAt", item.deletedAt)
                    .put("rule", rule)
            )
        }
        prefs.edit().putString(TRASH_KEY, array.toString()).apply()
    }
}
