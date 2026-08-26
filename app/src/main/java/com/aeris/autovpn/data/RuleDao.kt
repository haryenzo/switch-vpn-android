package com.aeris.autovpn.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM automation_rules ORDER BY id DESC")
    fun observeAll(): Flow<List<AutomationRule>>

    @Query("SELECT * FROM automation_rules WHERE enabled = 1")
    suspend fun getEnabled(): List<AutomationRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AutomationRule): Long

    @Update
    suspend fun update(rule: AutomationRule)

    @Delete
    suspend fun delete(rule: AutomationRule)
}
