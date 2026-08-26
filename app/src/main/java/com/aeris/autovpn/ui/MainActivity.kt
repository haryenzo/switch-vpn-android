package com.aeris.autovpn.ui

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeris.autovpn.R
import com.aeris.autovpn.data.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: RuleRepository

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Reads the language code we stored ourselves (see ui/Language.kt) and wraps the base
    // context with a Configuration forcing that locale — this has to happen here,
    // synchronously, before any resource lookup in this Activity happens.
    override fun attachBaseContext(newBase: Context) {
        val code = storedLanguageCode(newBase)
        if (code.isNotEmpty()) {
            val locale = java.util.Locale(code)
            val config = android.content.res.Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = RuleRepository(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // Safety net: if the system killed the monitor service (battery management on some
        // OEMs, low memory, etc.), simply opening the app resurrects it instead of requiring
        // the user to re-save a rule or reboot the phone.
        com.aeris.autovpn.automation.ForegroundAppMonitorService.ensureRunningIfRulesExist(this, repository)

        setContent {
            val settings = remember { AppSettings(applicationContext) }
            val themeMode by settings.themeMode.collectAsState(initial = "system")
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            AutoVpnTheme(darkTheme = darkTheme) {
                val view = androidx.compose.ui.platform.LocalView.current
                val background = MaterialTheme.colorScheme.background
                val useDarkIcons = background.luminance() > 0.5f
                SideEffect {
                    @Suppress("DEPRECATION")
                    window.statusBarColor = background.toArgb()
                    androidx.core.view.WindowCompat.getInsetsController(window, view)
                        .isAppearanceLightStatusBars = useDarkIcons
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    val hasChosenLanguage by settings.hasChosenLanguage.collectAsState(initial = null)
                    val scope = rememberCoroutineScope()

                    when (hasChosenLanguage) {
                        null -> Unit // still loading the flag; render nothing rather than flash the main UI
                        false -> LanguagePickerScreen(
                            initialCode = currentLanguageCode(applicationContext),
                            onConfirm = { code ->
                                scope.launch {
                                    // Persist "picker done" before applying — applying triggers
                                    // an Activity recreate, which would otherwise race the
                                    // DataStore write and could re-show the picker.
                                    settings.setHasChosenLanguage(true)
                                    applyLanguage(this@MainActivity, code)
                                }
                            },
                        )
                        true -> AppRoot(repository = repository, activity = this@MainActivity, settings = settings)
                    }
                }
            }
        }
    }
}

fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        "android:get_usage_stats",
        android.os.Process.myUid(),
        context.packageName,
    )
    return mode == android.app.AppOpsManager.MODE_ALLOWED
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
fun AppRoot(repository: RuleRepository, activity: ComponentActivity, settings: AppSettings) {
    var tab by remember { mutableStateOf(0) }
    var editingRule by remember { mutableStateOf<AutomationRule?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val rules by repository.observeAll().collectAsState(initial = emptyList())
    val customTargets by repository.observeCustomTargets().collectAsState(initial = emptyList())
    val allTargets = remember(customTargets) { BUILTIN_VPN_TARGETS + customTargets.map { it.toVpnAppTarget() } }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = showEditor || showSettings) {
        if (showSettings) showSettings = false else showEditor = false
    }

    if (showSettings) {
        SettingsScreen(settings = settings, onBack = { showSettings = false })
        return
    }

    if (showEditor) {
        AutomationEditScreen(
            initial = editingRule,
            existingRules = rules,
            repository = repository,
            onSave = { rule ->
                scope.launch {
                    repository.save(rule)
                    com.aeris.autovpn.automation.ForegroundAppMonitorService.start(activity)
                }
                showEditor = false
            },
            onCancel = { showEditor = false },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(tab = tab, onTabChange = { tab = it }, onOpenSettings = { showSettings = true })
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> AutomationScreen(
                    rules = rules,
                    allTargets = allTargets,
                    onAdd = { editingRule = null; showEditor = true },
                    onEdit = { editingRule = it; showEditor = true },
                    onToggle = { rule, enabled -> scope.launch { repository.setEnabled(rule, enabled) } },
                    onDelete = { rule -> scope.launch { repository.delete(rule) } },
                )
                1 -> PermissionsScreen(activity = activity)
            }
        }
        AppFooter()
    }
}

@Composable
private fun AppFooter() {
    var legalDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }

    legalDialog?.let { (title, body) ->
        SimpleInfoDialog(title = title, body = body) { legalDialog = null }
    }
    pendingExternalUrl?.let { url ->
        ExternalLinkConfirmDialog(url = url) { pendingExternalUrl = null }
    }

    val appRulesTitle = stringResource(R.string.footer_app_rules)
    val appRulesBody = stringResource(R.string.legal_app_rules_body)
    val privacyTitle = stringResource(R.string.footer_privacy_policy)
    val privacyBody = stringResource(R.string.legal_privacy_policy_body)

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FooterLink(stringResource(R.string.footer_help)) {
                pendingExternalUrl = "https://t.me/aeris_support"
            }
            FooterLink(appRulesTitle) { legalDialog = appRulesTitle to appRulesBody }
            FooterLink(privacyTitle) { legalDialog = privacyTitle to privacyBody }
        }
        FooterLink(stringResource(R.string.footer_support_project)) {
            pendingExternalUrl = "https://t.me/aeris_vpnbot"
        }
        Text(
            "2026 Ⓒ Aeris VPN",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ExternalLinkConfirmDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.external_link_warning_title)) },
        text = { Text(stringResource(R.string.external_link_warning_body)) },
        confirmButton = {
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                onDismiss()
            }) { Text(stringResource(R.string.external_link_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SimpleInfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_ok)) }
        },
    )
}

@Composable
private fun AppHeader(tab: Int, onTabChange: (Int) -> Unit, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text("Switch VPN", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .align(Alignment.Top)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MockAccent)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        "BETA",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
        ) {
            PillTab(stringResource(R.string.tab_automation), tab == 0, Modifier.weight(1f)) { onTabChange(0) }
            PillTab(stringResource(R.string.tab_permissions), tab == 1, Modifier.weight(1f)) { onTabChange(1) }
        }
    }
}

@Composable
private fun PillTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) MockInk else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun AutomationScreen(
    rules: List<AutomationRule>,
    allTargets: List<VpnAppTarget>,
    onAdd: () -> Unit,
    onEdit: (AutomationRule) -> Unit,
    onToggle: (AutomationRule, Boolean) -> Unit,
    onDelete: (AutomationRule) -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                shape = RoundedCornerShape(20.dp),
                containerColor = MockInk,
                contentColor = Color.White,
                modifier = Modifier.size(58.dp),
            ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_rule_cd)) }
        }
    ) { padding ->
        if (rules.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.rules_empty))
            }
            return@Scaffold
        }
        LazyColumnRules(rules, allTargets, padding, onEdit, onToggle, onDelete)
    }
}

@Composable
private fun LazyColumnRules(
    rules: List<AutomationRule>,
    allTargets: List<VpnAppTarget>,
    padding: PaddingValues,
    onEdit: (AutomationRule) -> Unit,
    onToggle: (AutomationRule, Boolean) -> Unit,
    onDelete: (AutomationRule) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().padding(padding)) {
        items(rules, key = { it.id }) { rule ->
            RuleRow(rule, allTargets, onEdit, onToggle, onDelete)
            Divider()
        }
    }
}

@Composable
private fun RuleRow(
    rule: AutomationRule,
    allTargets: List<VpnAppTarget>,
    onEdit: (AutomationRule) -> Unit,
    onToggle: (AutomationRule, Boolean) -> Unit,
    onDelete: (AutomationRule) -> Unit,
) {
    val watchedIcon = rememberAppIcon(rule.watchedPackage)
    val target = remember(rule.targetAppId, allTargets) { allTargets.find { it.id == rule.targetAppId } }
    val targetIcon = target?.let { rememberAppIcon(it) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onEdit(rule) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp)) {
            Box(
                Modifier.size(42.dp).align(Alignment.TopStart).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (watchedIcon != null) {
                    Image(
                        watchedIcon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                    )
                } else {
                    Text(rule.watchedAppLabel.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (targetIcon != null) {
                Image(
                    targetIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(5.dp)),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(rule.watchedAppLabel, style = MaterialTheme.typography.titleMedium)
            if (rule.triggerOnOpen) {
                Text(
                    stringResource(R.string.rule_opens, rule.targetAppLabel, actionLabel(rule.openAction)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (rule.triggerOnClose) {
                Text(
                    stringResource(R.string.rule_closes, rule.targetAppLabel, actionLabel(rule.closeAction)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        Switch(
            checked = rule.enabled,
            onCheckedChange = { onToggle(rule, it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MockAccent,
                checkedBorderColor = MockAccent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
        IconButton(onClick = { onDelete(rule) }) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_cd), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun actionLabel(name: String) = when (name) {
    VpnAction.CONNECT.name -> stringResource(R.string.action_connect)
    VpnAction.DISCONNECT.name -> stringResource(R.string.action_disconnect)
    else -> stringResource(R.string.action_toggle)
}

@Composable
fun PermissionsScreen(activity: ComponentActivity) {
    val context = activity
    var usageOk by remember { mutableStateOf(hasUsageAccess(context)) }
    var batteryOk by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.headlineSmall)

        PermissionRow(
            title = stringResource(R.string.perm_usage_title),
            granted = usageOk,
            description = stringResource(R.string.perm_usage_desc),
        ) {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        PermissionRow(
            title = stringResource(R.string.perm_battery_title),
            granted = batteryOk,
            description = stringResource(R.string.perm_battery_desc),
        ) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }

        PermissionRow(
            title = stringResource(R.string.perm_overlay_title),
            granted = overlayOk,
            description = stringResource(R.string.perm_overlay_desc),
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            context.startActivity(intent)
        }
    }

    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                usageOk = hasUsageAccess(context)
                batteryOk = isIgnoringBatteryOptimizations(context)
                overlayOk = Settings.canDrawOverlays(context)
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (granted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (granted) "✓" else "!",
                    color = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!granted) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(MockAccent).padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(stringResource(R.string.perm_grant), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
