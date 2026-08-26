package com.aeris.autovpn.data

/**
 * A VPN client app this tool can drive without touching its tunnel directly.
 * We never manage the tunnel ourselves; the target app owns the actual connection.
 *
 * Two mechanisms, in order of preference:
 * 1. Explicit broadcast to a dedicated receiver the app exposes (broadcast*Action fields).
 *    This never shows any UI and is NOT subject to Android's background-activity-start
 *    restrictions, because sending a broadcast never launches an Activity.
 * 2. Deep link via Intent.ACTION_VIEW on the app's own URL scheme (the scheme and *Path fields).
 *    This opens an Activity, which Android may block from a background service unless the
 *    "display over other apps" permission is granted; the *WithoutUiPath variants (where an
 *    app documents/implements them) instruct the target app's own activity to close itself
 *    immediately, but it is still, technically, a very briefly started Activity.
 */
data class VpnAppTarget(
    val id: String,
    val label: String,
    val packageName: String,
    // Some apps ship under a different applicationId per distribution channel (Play Store vs.
    // a GitHub-released APK, for example) — list any other known real package ids here so
    // foreground-flash detection matches regardless of which build the user installed.
    val packageNameAliases: List<String> = emptyList(),
    val scheme: String? = null,
    val connectPath: String = "connect",
    val disconnectPath: String = "disconnect",
    val togglePath: String = "toggle",
    val connectWithoutUiPath: String? = null,
    val disconnectWithoutUiPath: String? = null,
    val toggleWithoutUiPath: String? = null,
    val broadcastConnectAction: String? = null,
    val broadcastDisconnectAction: String? = null,
    val broadcastToggleAction: String? = null,
    // Bundled drawable resource id shown when the target app isn't installed on the phone, so
    // the picker doesn't fall back to a bare letter for an app the user hasn't installed yet.
    val fallbackIconRes: Int? = null,
) {
    fun broadcastActionFor(action: VpnAction): String? = when (action) {
        VpnAction.CONNECT -> broadcastConnectAction
        VpnAction.DISCONNECT -> broadcastDisconnectAction
        VpnAction.TOGGLE -> broadcastToggleAction
    }
}

enum class VpnAction { CONNECT, DISCONNECT, TOGGLE }

/**
 * Built-in targets.
 *
 * Incy exposes a silent broadcast receiver (no UI, not subject to background-activity-start
 * restrictions) as its primary control path, with a `incy://connect`-style deep link as a
 * fallback. Happ and v2RayTun only expose a deep link handled by an Activity, so they're
 * subject to background-activity-start restrictions — hence this app requesting "display over
 * other apps". Happ additionally supports "without UI" variants of each action. v2RayTun has
 * no real "flip current state" action, so TOGGLE is mapped to its restart action as the closest
 * approximation, not a true toggle.
 *
 * Happ ships under more than one package id depending on distribution channel; both are listed
 * so foreground-flash detection matches regardless of which build the user installed.
 */
val BUILTIN_VPN_TARGETS = listOf(
    VpnAppTarget(
        id = "incy",
        label = "Incy",
        packageName = "llc.itdev.incy",
        scheme = "incy",
        broadcastConnectAction = "llc.itdev.incy.CONNECT",
        broadcastDisconnectAction = "llc.itdev.incy.DISCONNECT",
        broadcastToggleAction = "llc.itdev.incy.TOGGLE",
    ),
    VpnAppTarget(
        id = "happ",
        label = "Happ",
        packageName = "su.happ.proxyutility",
        packageNameAliases = listOf("com.happproxy"),
        scheme = "happ",
        connectWithoutUiPath = "connect_without_ui",
        disconnectWithoutUiPath = "disconnect_without_ui",
        toggleWithoutUiPath = "toggle_without_ui",
    ),
    VpnAppTarget(
        id = "v2raytun",
        label = "v2RayTun",
        packageName = "com.v2raytun.android",
        scheme = "v2raytun",
        connectPath = "control/start",
        disconnectPath = "control/stop",
        togglePath = "control/restart",
        fallbackIconRes = com.aeris.autovpn.R.drawable.vpn_icon_v2raytun,
    ),
)

fun vpnActionPath(target: VpnAppTarget, action: VpnAction, preferSilent: Boolean): String {
    val silent = if (preferSilent) when (action) {
        VpnAction.CONNECT -> target.connectWithoutUiPath
        VpnAction.DISCONNECT -> target.disconnectWithoutUiPath
        VpnAction.TOGGLE -> target.toggleWithoutUiPath
    } else null
    return silent ?: when (action) {
        VpnAction.CONNECT -> target.connectPath
        VpnAction.DISCONNECT -> target.disconnectPath
        VpnAction.TOGGLE -> target.togglePath
    }
}
