package com.aeris.autovpn.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AutomationRule::class, CustomVpnTarget::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun customVpnTargetDao(): CustomVpnTargetDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autovpn.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
