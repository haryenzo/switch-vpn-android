package com.aeris.autovpn.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomVpnTargetDao {
    @Query("SELECT * FROM custom_vpn_targets ORDER BY id DESC")
    fun observeAll(): Flow<List<CustomVpnTarget>>

    @Query("SELECT * FROM custom_vpn_targets")
    suspend fun getAll(): List<CustomVpnTarget>

    @Insert
    suspend fun insert(target: CustomVpnTarget): Long

    @Delete
    suspend fun delete(target: CustomVpnTarget)
}
