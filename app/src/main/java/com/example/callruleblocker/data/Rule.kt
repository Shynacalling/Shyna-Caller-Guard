package com.example.callruleblocker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One rule = for a given SIM slot, how to treat calls that match [matchType]/[matchValue].
 *
 * simSlotIndex: 0 = SIM 1, 1 = SIM 2 (matches Android's SubscriptionInfo.simSlotIndex)
 * matchType:    "FAMILY_CONTACTS" | "UNKNOWN" | "SPECIFIC_NUMBER"
 * matchValue:   phone number when matchType == SPECIFIC_NUMBER, else unused
 * action:       "ALLOW" | "BLOCK"
 */
@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val simSlotIndex: Int,
    val matchType: String,
    val matchValue: String = "",
    val action: String,
    val enabled: Boolean = true
)
