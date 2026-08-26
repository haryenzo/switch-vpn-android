package com.aeris.autovpn.automation

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aeris.autovpn.AutoVpnApp
import com.aeris.autovpn.data.AutomationRule
import com.aeris.autovpn.data.RuleRepository
import com.aeris.autovpn.data.VpnAction
import com.aeris.autovpn.data.VpnAppTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that polls UsageStatsManager for foreground-app switches and fires the
 * matching automation rule's VPN action. Polling (not AccessibilityService) is deliberate:
 * Accessibility gets flagged by Play policy for this kind of use, UsageStatsManager doesn't.
 */
class ForegroundAppMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var repository: RuleRepository
    private var pollJob: Job? = null

    // "current" is the last app we treated as a genuine, settled switch (see checkForegroundChange
    // kdoc for why this needs a settle window). "pendingCandidate"/"pendingSince" track a package
    // that has taken the foreground but hasn't been foreground long enough yet to count.
    private var current: String? = null
    private var pendingCandidate: String? = null
    private var pendingSince: Long = 0L

    override fun onCreate() {
        super.onCreate()
        repository = RuleRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(AutoVpnApp.MONITOR_NOTIFICATION_ID, buildNotification())
        // start() is called again on every rule save even while the service is already
        // running (so a brand-new rule gets picked up without a restart) — without this guard
        // that would launch a second, third, ... concurrent pollLoop on the same mutable
        // current/pendingCandidate state, racing each other and firing every action multiple
        // times over the service's life.
        if (pollJob?.isActive != true) {
            pollJob = scope.launch { pollLoop() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[Job]?.cancel()
    }

    private suspend fun pollLoop() {
        primeCurrentForegroundApp()
        while (true) {
            val rules = repository.getEnabled()
            if (rules.isEmpty()) {
                stopSelf()
                return
            }
            // Re-resolved every cycle (cheap local DB read) so a VPN target the user just added
            // in the editor is picked up without needing to restart the service.
            val allTargets = repository.allVpnTargets()
            checkForegroundChange(rules, allTargets)
            delay(POLL_INTERVAL_MS)
        }
    }

    /**
     * Fires open rules for whatever app is already in the foreground the moment this service
     * (re)starts, instead of only reacting to a future switch INTO that app. Without this, a
     * rule for an app the user is already sitting in — right after the OS kills this service
     * (battery management, low memory) and something like MainActivity's
     * ensureRunningIfRulesExist resurrects it later, or after a reboot — stays dormant until
     * the user happens to leave that app and come back, since an app that's already frontmost
     * never generates a fresh MOVE_TO_FOREGROUND event.
     *
     * Android has no direct "what's in front right now" query (by design, for privacy); the
     * standard proxy is UsageStatsManager.queryUsageStats — the package with the most recent
     * lastTimeUsed is whatever was last brought to the front.
     */
    private suspend fun primeCurrentForegroundApp() {
        val rules = repository.getEnabled()
        if (rules.isEmpty()) return
        val allTargets = repository.allVpnTargets()
        val ignoredPackages = allTargets
            .flatMap { listOf(it.packageName) + it.packageNameAliases }
            .toSet() + packageName

        val pkg = currentForegroundPackage() ?: return
        if (pkg in ignoredPackages || pkg == current) return

        current = pkg
        rules.filter { it.watchedPackage == pkg && it.triggerOnOpen }
            .forEach { fire(it, it.openAction, allTargets) }
    }

    private fun currentForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - LOOKBACK_FOR_CURRENT_MS, now)
            ?.filter { it.lastTimeUsed > 0 }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    /**
     * Two different things can make the watched app look like it "closed" when the user never
     * actually left it:
     *
     * 1. Firing a rule launches the VPN app itself when it has no silent broadcast receiver
     *    (Happ's deep link opens an Activity). That flash generates its own foreground event.
     *    Filtered out below via ignoredPackages.
     *
     * 2. The watched app itself can hand focus to an auxiliary component that Android reports
     *    as a *different* package — e.g. Telegram opening a link internally briefly surfaces as
     *    something like "org.telegram.messenger.web" before handing off to a browser, confirmed
     *    by on-device testing (a debug toast logging every detected switch showed exactly this
     *    sequence: real app -> mystery component -> another app -> back to the real app, all
     *    within a couple of seconds). There's no way to enumerate every such companion package
     *    in advance — a rule could point at literally any app that itself opens web links,
     *    previews, share sheets, etc.
     *
     * Either way the symptom is the same: a package shows up in front for a second or two and
     * then the original app resurfaces, and naive "switch = close" logic fires a close (and
     * later a spurious re-open) for something that, from the user's point of view, never
     * stopped being what they were using. So a raw "did the foreground package change" check —
     * even one that special-cases our own known VPN packages — isn't enough; case 2 can involve
     * an arbitrary, unpredictable package.
     *
     * The fix that covers both: require a new foreground package to stay in front, uncontested,
     * for SETTLE_MS before it's treated as a genuine switch. A flash (case 1) or a brief
     * companion hop (case 2) gets superseded before the timer elapses and is discarded outright;
     * only an app the user is actually still looking at after a few seconds commits, which is
     * when we fire the old app's close rule and the new app's open rule.
     */
    private suspend fun checkForegroundChange(rules: List<AutomationRule>, allTargets: List<VpnAppTarget>) {
        val ignoredPackages = allTargets
            .flatMap { listOf(it.packageName) + it.packageNameAliases }
            .toSet() + packageName

        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - LOOKBACK_MS, now)
        val event = UsageEvents.Event()

        var candidate = pendingCandidate
        var candidateSince = pendingSince

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
            val pkg = event.packageName
            if (pkg in ignoredPackages) continue // Happ/Incy/us flashing up — never a candidate
            when (pkg) {
                current -> candidate = null // back to the known app; drop whatever was pending
                candidate -> { /* same candidate still fronting; keep its original since-time */ }
                else -> {
                    // a different package just took over — replaces any prior candidate, which
                    // by definition didn't settle in time
                    candidate = pkg
                    candidateSince = event.timeStamp
                }
            }
        }

        val opened = mutableListOf<String>()
        val closed = mutableListOf<String>()
        if (candidate != null && candidate != current && now - candidateSince >= SETTLE_MS) {
            current?.let { closed += it }
            opened += candidate!!
            current = candidate
            candidate = null
        }

        // If the app being opened uses the very same VPN target as a close rule about to fire,
        // that client is staying connected the whole time the user was switching apps — there
        // was never a real handoff. Firing the close anyway would drop and immediately
        // re-establish the *same* tunnel for no reason (and needlessly wait out the handoff
        // delay below). Skip only that target's close; unrelated closes in the same batch
        // still fire normally.
        val openedTargetIds = opened.flatMap { pkg ->
            rules.filter { it.watchedPackage == pkg && it.triggerOnOpen }.map { it.targetAppId }
        }.toSet()

        // Close fires before open, with a short head start, when both happen in the same
        // switch. Android only keeps one VpnService interface active system-wide; if the new
        // app's VPN client calls VpnService.prepare() while the old one still owns the
        // interface, Android can require an interactive "Connection Request" consent dialog
        // instead of silently handing the interface over — the old app already having been
        // granted permission once doesn't skip this when a DIFFERENT app is asking to take
        // over. Giving the disconnect a moment to actually land first means the new app's
        // prepare() call is far more likely to see no competing VPN and proceed silently,
        // instead of the connect looking like it's stuck waiting on the disconnect.
        var closedSomething = false
        closed.forEach { pkg ->
            rules.filter { it.watchedPackage == pkg && it.triggerOnClose && it.targetAppId !in openedTargetIds }
                .forEach {
                    fire(it, it.closeAction, allTargets)
                    closedSomething = true
                }
        }
        if (closedSomething && opened.isNotEmpty()) {
            delay(HANDOFF_DELAY_MS)
        }
        opened.forEach { pkg ->
            rules.filter { it.watchedPackage == pkg && it.triggerOnOpen }
                .forEach { fire(it, it.openAction, allTargets) }
        }

        pendingCandidate = candidate
        pendingSince = candidateSince
    }

    private fun fire(rule: AutomationRule, actionName: String, allTargets: List<VpnAppTarget>) {
        val action = runCatching { VpnAction.valueOf(actionName) }.getOrNull() ?: return
        val target = allTargets.find { it.id == rule.targetAppId } ?: return
        DeepLinkController.fire(applicationContext, target, action)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, AutoVpnApp.MONITOR_CHANNEL_ID)
            .setContentTitle(getString(com.aeris.autovpn.R.string.notification_title))
            .setContentText(getString(com.aeris.autovpn.R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    companion object {
        private const val POLL_INTERVAL_MS = 1500L
        private const val LOOKBACK_MS = 10_000L

        // How long a new foreground app must stay uncontested before we treat it as a genuine
        // switch. Short enough to still feel responsive, long enough to outlast both an
        // Activity-based VPN deep link flash and the kind of brief internal web-view hop
        // described above.
        private const val SETTLE_MS = 3000L

        // Head start given to a disconnect before the paired connect fires when switching
        // between two different VPN targets — see checkForegroundChange's kdoc above the
        // close/open block for why.
        private const val HANDOFF_DELAY_MS = 700L

        // How far back to look for currentForegroundPackage()'s "most recently used app" query
        // at service startup — wide enough to find something even if the phone has just been
        // sitting in one app for a while with no recent switches.
        private const val LOOKBACK_FOR_CURRENT_MS = 24 * 60 * 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, ForegroundAppMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun ensureRunningIfRulesExist(context: Context, repository: RuleRepository) {
            CoroutineScope(Dispatchers.Default).launch {
                if (repository.getEnabled().isNotEmpty()) start(context)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ForegroundAppMonitorService::class.java))
        }
    }
}
