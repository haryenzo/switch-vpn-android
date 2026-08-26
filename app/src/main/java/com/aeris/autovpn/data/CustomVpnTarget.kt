package com.aeris.autovpn.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A VPN app the user has wired up themselves — same shape as VpnAppTarget, but entered by
 * hand instead of hardcoded, since we can't inspect every third-party VPN client's manifest
 * in advance the way BUILTIN_VPN_TARGETS was built for Happ/Incy.
 */
@Entity(tableName = "custom_vpn_targets")
data class CustomVpnTarget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val packageName: String,
    val scheme: String? = null,
    val connectPath: String = "connect",
    val disconnectPath: String = "disconnect",
    val togglePath: String = "toggle",
    val broadcastConnectAction: String? = null,
    val broadcastDisconnectAction: String? = null,
    val broadcastToggleAction: String? = null,
)

// Prefixed so a custom target's id can never collide with a BUILTIN_VPN_TARGETS id.
fun CustomVpnTarget.toVpnAppTarget() = VpnAppTarget(
    id = "custom_$id",
    label = label,
    packageName = packageName,
    scheme = scheme,
    connectPath = connectPath,
    disconnectPath = disconnectPath,
    togglePath = togglePath,
    broadcastConnectAction = broadcastConnectAction,
    broadcastDisconnectAction = broadcastDisconnectAction,
    broadcastToggleAction = broadcastToggleAction,
)
