package com.aeris.autovpn.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.aeris.autovpn.data.VpnAction
import com.aeris.autovpn.data.VpnAppTarget
import com.aeris.autovpn.data.vpnActionPath

/**
 * Drives the target VPN app without touching any tunnel ourselves. Two paths, in order of
 * preference — see VpnAppTarget's kdoc for why:
 *
 * 1. Explicit broadcast to the target's own control receiver, when it exposes one (Incy).
 *    Never shows UI, never subject to background-activity-start restrictions.
 * 2. Deep link via Intent.ACTION_VIEW on the app's URL scheme (Happ). This launches an
 *    Activity, which Android 10+ can silently drop if the caller (a background Service, in
 *    our case) has no background-activity-start exemption — holding SYSTEM_ALERT_WINDOW is
 *    one such exemption, which is why the app requests it. Toast calls are unaffected by that
 *    restriction; only failure/warning cases raise one, so a normal connect/disconnect stays
 *    silent instead of popping a toast on every app switch.
 */
object DeepLinkController {
    private const val TAG = "DeepLinkController"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun fire(context: Context, target: VpnAppTarget, action: VpnAction): Boolean {
        val broadcastAction = target.broadcastActionFor(action)
        if (broadcastAction != null) {
            return try {
                val intent = Intent(broadcastAction).setPackage(target.packageName)
                context.sendBroadcast(intent)
                Log.i(TAG, "sent broadcast $broadcastAction to ${target.packageName}")
                true
            } catch (e: Exception) {
                Log.w(TAG, "failed to send broadcast $broadcastAction", e)
                toast(context, "Не удалось отправить $broadcastAction — ${target.label} установлен?")
                false
            }
        }

        val scheme = target.scheme ?: run {
            toast(context, "У ${target.label} нет ни broadcast, ни deep-link механизма")
            return false
        }
        val uri = Uri.parse("$scheme://${vpnActionPath(target, action, preferSilent = true)}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w(TAG, "missing overlay permission, $uri will likely be dropped by the system")
            toast(context, "Нет разрешения \"Поверх других приложений\" — Android заблокирует запуск ${target.label}")
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "fired $uri for ${target.label}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "failed to fire $uri", e)
            toast(context, "Не удалось открыть $uri — ${target.label} установлен?")
            false
        }
    }

    private fun toast(context: Context, message: String) {
        mainHandler.post { Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show() }
    }
}
