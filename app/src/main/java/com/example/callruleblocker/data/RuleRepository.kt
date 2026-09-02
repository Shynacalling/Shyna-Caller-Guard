package com.example.callruleblocker.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

class RuleRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).ruleDao()
    private val trashStore = RuleTrashStore(context)

    fun observeAll() = dao.observeAll()

    /** Exact numbers that are currently enabled as BLOCK rules on any SIM. */
    suspend fun blockedSpecificNumbers(): Set<String> = buildSet {
        listOf(0, 1).forEach { slot ->
            dao.rulesForSim(slot)
                .asSequence()
                .filter { it.enabled && it.action == "BLOCK" && it.matchType == "SPECIFIC_NUMBER" }
                .map { it.matchValue.filter(Char::isDigit).takeLast(10) }
                .filter { it.isNotBlank() }
                .forEach(::add)
        }
    }

    suspend fun addRule(rule: Rule) = dao.insert(rule)
    suspend fun deleteRule(rule: Rule) {
        trashStore.add(rule)
        dao.delete(rule)
    }

    fun trashedRules(): List<TrashedRule> = trashStore.list()

    suspend fun restoreRule(item: TrashedRule) {
        dao.insert(item.rule.copy(id = 0))
        trashStore.remove(item.trashId)
    }

    fun permanentlyDelete(item: TrashedRule) = trashStore.remove(item.trashId)
    fun emptyTrash() = trashStore.clear()
    suspend fun updateRule(rule: Rule) = dao.update(rule)

    /**
     * Fast incoming-call decision path. Rule semantics are unchanged:
     * specific number > family > unknown > allow.
     *
     * The old implementation queried Contacts before checking a specific-number
     * rule, and then queried PhoneLookup a second time for Family. On some OEMs
     * that made the incoming UI wait noticeably. This version checks the cheap
     * exact rule first and resolves the contact id only once when a contact-based
     * rule actually exists.
     */
    suspend fun decide(number: String, simSlotIndex: Int): String {
        val rules = dao.rulesForSim(simSlotIndex)
        if (rules.isEmpty()) return "ALLOW"

        rules.firstOrNull { it.matchType == "SPECIFIC_NUMBER" && sameNumber(it.matchValue, number) }
            ?.let { return it.action }

        val familyRule = rules.firstOrNull { it.matchType == "FAMILY_CONTACTS" }
        val unknownRule = rules.firstOrNull { it.matchType == "UNKNOWN" }
        if (familyRule == null && unknownRule == null) return "ALLOW"

        val contactId = findContactId(number)
        if (contactId == null) return unknownRule?.action ?: "ALLOW"

        if (familyRule != null && isInFamilyGroup(contactId)) return familyRule.action
        return "ALLOW"
    }

    private fun sameNumber(a: String, b: String) =
        a.filter { it.isDigit() }.takeLast(10) == b.filter { it.isDigit() }.takeLast(10)

    private fun findContactId(number: String): Long? = runCatching {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon().appendPath(number).build()
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.CONTACT_ID),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
    }.getOrNull()

    /**
     * "Family" = contact belongs to a Contacts group literally named "Family".
     * Optimized to use fewer queries.
     */
    private fun isInFamilyGroup(contactId: Long): Boolean = runCatching {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false
        val familyGroupIds = context.contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups._ID),
            "${ContactsContract.Groups.TITLE} = ? OR ${ContactsContract.Groups.TITLE} = ?",
            arrayOf("Family", "family"),
            null
        )?.use { cursor ->
            val ids = mutableListOf<String>()
            while (cursor.moveToNext()) ids.add(cursor.getLong(0).toString())
            ids
        } ?: emptyList()

        if (familyGroupIds.isEmpty()) return false

        val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND " +
                "${ContactsContract.Data.MIMETYPE} = ? AND " +
                "${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} IN (${familyGroupIds.joinToString(",") { "?" }})"
        val args = arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE) + familyGroupIds.toTypedArray()

        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            selection,
            args,
            null
        )?.use { it.count > 0 } ?: false
    }.getOrDefault(false)
}
