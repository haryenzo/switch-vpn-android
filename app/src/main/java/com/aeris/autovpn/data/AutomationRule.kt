package com.aeris.autovpn.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation_rules")
data class AutomationRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val watchedPackage: String,
    val watchedAppLabel: String,
    val targetAppId: String,
    val targetAppLabel: String,
    val triggerOnOpen: Boolean,
    val triggerOnClose: Boolean,
    val openAction: String,   // VpnAction.name, used when triggerOnOpen
    val closeAction: String,  // VpnAction.name, used when triggerOnClose
    val enabled: Boolean = true,
)
