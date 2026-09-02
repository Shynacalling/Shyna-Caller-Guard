package com.example.callruleblocker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM rules ORDER BY simSlotIndex, id")
    fun observeAll(): Flow<List<Rule>>

    @Query("SELECT * FROM rules WHERE simSlotIndex = :simSlotIndex AND enabled = 1")
    suspend fun rulesForSim(simSlotIndex: Int): List<Rule>

    @Insert
    suspend fun insert(rule: Rule): Long

    @Update
    suspend fun update(rule: Rule)

    @Delete
    suspend fun delete(rule: Rule)
}
