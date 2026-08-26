package com.aeris.autovpn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AutoVpnApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_MIN notifications are hidden from the shade entirely on some
            // OEM launchers, which made it look like the service wasn't running when it
            // actually was. LOW still makes no sound and doesn't show as a heads-up popup,
            // but reliably stays visible so its presence/absence is a trustworthy signal.
            val channel = NotificationChannel(
                MONITOR_CHANNEL_ID,
                "VPN automation monitor",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps the app-open/close watcher running in the background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val MONITOR_CHANNEL_ID = "vpn_automation_monitor"
        const val MONITOR_NOTIFICATION_ID = 1
    }
}
