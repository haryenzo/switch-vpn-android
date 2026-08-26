package com.aeris.autovpn.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aeris.autovpn.data.RuleRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ForegroundAppMonitorService.ensureRunningIfRulesExist(context, RuleRepository(context))
    }
}
