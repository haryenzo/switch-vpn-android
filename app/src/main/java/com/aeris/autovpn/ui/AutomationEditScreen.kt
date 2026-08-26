package com.aeris.autovpn.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeris.autovpn.R
import com.aeris.autovpn.data.AutomationRule
import com.aeris.autovpn.data.BUILTIN_VPN_TARGETS
import com.aeris.autovpn.data.CustomVpnTarget
import com.aeris.autovpn.data.RuleRepository
import com.aeris.autovpn.data.VpnAction
import com.aeris.autovpn.data.VpnAppTarget
import com.aeris.autovpn.data.toVpnAppTarget
import kotlinx.coroutines.launch

private data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

private fun Drawable.toBitmap(sizePx: Int = 96): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bitmap
}

@Composable
fun AutomationEditScreen(
    initial: AutomationRule?,
    existingRules: List<AutomationRule> = emptyList(),
    repository: RuleRepository,
    onSave: (AutomationRule) -> Unit,
    onCancel: () -> Unit,
) {
    // Custom VPN targets are temporarily off — only the built-ins (Happ/Incy) are selectable
    // for now, per explicit request. The data layer (CustomVpnTarget, AddCustomVpnScreen) is
    // left in place, just not wired into this picker, so it's a quick re-enable later rather
    // than a rewrite.
    val allTargets = BUILTIN_VPN_TARGETS

    var pickingApp by remember { mutableStateOf(false) }
    var watchedPackage by remember { mutableStateOf(initial?.watchedPackage ?: "") }
    var watchedLabel by remember { mutableStateOf(initial?.watchedAppLabel ?: "") }
    var targetAppId by remember { mutableStateOf(initial?.targetAppId ?: BUILTIN_VPN_TARGETS.first().id) }
    var triggerOnOpen by remember { mutableStateOf(initial?.triggerOnOpen ?: true) }
    var triggerOnClose by remember { mutableStateOf(initial?.triggerOnClose ?: false) }
    var openAction by remember { mutableStateOf(initial?.openAction ?: VpnAction.CONNECT.name) }
    var closeAction by remember { mutableStateOf(initial?.closeAction ?: VpnAction.DISCONNECT.name) }

    // Android keeps only one VPN interface active system-wide: two rules for the same
    // watched app pointing at two different VPN clients will fight each other every time
    // the app opens (each disconnects the other's tunnel). Warn instead of letting it happen
    // silently.
    val conflictingTarget = remember(watchedPackage, targetAppId, existingRules, allTargets) {
        existingRules
            .filter { it.id != (initial?.id ?: -1L) && it.watchedPackage == watchedPackage && it.enabled }
            .map { it.targetAppId }
            .firstOrNull { it != targetAppId }
            ?.let { id -> allTargets.find { it.id == id }?.label }
    }

    androidx.activity.compose.BackHandler(enabled = pickingApp) { pickingApp = false }

    if (pickingApp) {
        AppPickerScreen(
            onPicked = { pkg, label ->
                watchedPackage = pkg
                watchedLabel = label
                pickingApp = false
            },
            onCancel = { pickingApp = false },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.back))
            }
        }
        Column(
            Modifier.fillMaxSize().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text(
                stringResource(if (initial == null) R.string.new_rule_title else R.string.edit_rule_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            SectionLabel(stringResource(R.string.section_watched_app), info = stringResource(R.string.info_watched_app))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (watchedLabel.isEmpty()) MaterialTheme.colorScheme.outline else MockAccent, RoundedCornerShape(16.dp))
                    .clickable { pickingApp = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val watchedIcon = rememberAppIcon(watchedPackage)
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (watchedIcon != null) {
                        Image(watchedIcon, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)))
                    } else {
                        Text(watchedLabel.take(1).ifEmpty { "?" }.uppercase(), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    watchedLabel.ifEmpty { stringResource(R.string.pick_app) },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionLabel(stringResource(R.string.section_vpn_app), info = stringResource(R.string.info_vpn_app))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                allTargets.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { target ->
                            VpnTargetCard(
                                label = target.label,
                                icon = rememberAppIcon(target),
                                selected = targetAppId == target.id,
                                modifier = Modifier.weight(1f),
                                onClick = { targetAppId = target.id },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            conflictingTarget?.let { otherLabel ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(16.dp)) {
                    Text(
                        stringResource(R.string.conflict_warning, otherLabel),
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            SectionLabel(stringResource(R.string.section_triggers), info = stringResource(R.string.info_triggers))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TriggerRow(
                    label = stringResource(R.string.trigger_open),
                    checked = triggerOnOpen,
                    onCheckedChange = { triggerOnOpen = it },
                    action = openAction,
                    onActionChange = { openAction = it },
                )
                TriggerRow(
                    label = stringResource(R.string.trigger_close),
                    checked = triggerOnClose,
                    onCheckedChange = { triggerOnClose = it },
                    action = closeAction,
                    onActionChange = { closeAction = it },
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
            Button(
                enabled = watchedPackage.isNotEmpty() && (triggerOnOpen || triggerOnClose),
                colors = ButtonDefaults.buttonColors(containerColor = MockInk, contentColor = Color.White),
                modifier = Modifier.weight(1f),
                onClick = {
                    val target = allTargets.first { it.id == targetAppId }
                    onSave(
                        AutomationRule(
                            id = initial?.id ?: 0,
                            watchedPackage = watchedPackage,
                            watchedAppLabel = watchedLabel,
                            targetAppId = targetAppId,
                            targetAppLabel = target.label,
                            triggerOnOpen = triggerOnOpen,
                            triggerOnClose = triggerOnClose,
                            openAction = openAction,
                            closeAction = closeAction,
                            enabled = initial?.enabled ?: true,
                        )
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String, info: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.6.sp,
        )
        if (info != null) {
            Spacer(Modifier.width(2.dp))
            InfoTooltip(info)
        }
    }
}

@Composable
private fun VpnTargetCard(
    label: String,
    icon: ImageBitmap?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    isCustom: Boolean = false,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) MockAccent else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                Image(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MockAccent else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label.take(1),
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MockAccent2 else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (isCustom && onDelete != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.delete_cd),
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TriggerRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    action: String,
    onActionChange: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MockAccent,
                    checkedBorderColor = MockAccent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        }
        if (checked) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VpnAction.values().forEach { a ->
                    ActionPill(
                        label = when (a) {
                            VpnAction.CONNECT -> stringResource(R.string.action_connect_title)
                            VpnAction.DISCONNECT -> stringResource(R.string.action_disconnect_title)
                            VpnAction.TOGGLE -> stringResource(R.string.action_toggle_title)
                        },
                        selected = action == a.name,
                        modifier = Modifier.weight(1f),
                        onClick = { onActionChange(a.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MockAccentSoft else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = if (selected) MockAccent else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MockAccent2 else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerScreen(onPicked: (String, String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val apps = remember {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it)) }
            .sortedBy { it.label.lowercase() }
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.back))
            }
        }
        Text(
            stringResource(R.string.pick_app_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_apps)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = MockAccent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPicked(app.packageName, app.label) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        bitmap = remember(app.packageName) { app.icon.toBitmap().asImageBitmap() },
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun AddCustomVpnScreen(onSave: (CustomVpnTarget) -> Unit, onCancel: () -> Unit) {
    var pickingApp by remember { mutableStateOf(false) }
    var packageName by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var scheme by remember { mutableStateOf("") }
    var connectPath by remember { mutableStateOf("connect") }
    var disconnectPath by remember { mutableStateOf("disconnect") }
    var togglePath by remember { mutableStateOf("toggle") }
    var showAdvanced by remember { mutableStateOf(false) }
    var broadcastConnect by remember { mutableStateOf("") }
    var broadcastDisconnect by remember { mutableStateOf("") }
    var broadcastToggle by remember { mutableStateOf("") }

    androidx.activity.compose.BackHandler(enabled = pickingApp) { pickingApp = false }

    if (pickingApp) {
        AppPickerScreen(
            onPicked = { pkg, lbl ->
                packageName = pkg
                label = lbl
                pickingApp = false
            },
            onCancel = { pickingApp = false },
        )
        return
    }

    val canSave = packageName.isNotEmpty() && label.isNotEmpty() &&
        (scheme.isNotBlank() || broadcastConnect.isNotBlank() || broadcastDisconnect.isNotBlank() || broadcastToggle.isNotBlank())

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.back))
            }
        }
        Column(
            Modifier.fillMaxSize().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.custom_vpn_title), style = MaterialTheme.typography.headlineSmall)

            SectionLabel(stringResource(R.string.section_app))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (label.isEmpty()) MaterialTheme.colorScheme.outline else MockAccent, RoundedCornerShape(16.dp))
                    .clickable { pickingApp = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = rememberAppIcon(packageName)
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) {
                        Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)))
                    } else {
                        Text(label.take(1).ifEmpty { "?" }.uppercase(), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    label.ifEmpty { stringResource(R.string.pick_app) },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionLabel(stringResource(R.string.section_scheme))
            OutlinedTextField(
                value = scheme,
                onValueChange = { scheme = it },
                placeholder = { Text(stringResource(R.string.scheme_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MockAccent),
                modifier = Modifier.fillMaxWidth(),
            )
            if (scheme.isNotBlank()) {
                Text(
                    stringResource(R.string.scheme_note, scheme),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = connectPath, onValueChange = { connectPath = it },
                        label = { Text("connect") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = disconnectPath, onValueChange = { disconnectPath = it },
                        label = { Text("disconnect") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = togglePath, onValueChange = { togglePath = it },
                        label = { Text("toggle") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
            }

            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(stringResource(if (showAdvanced) R.string.broadcast_toggle_hide else R.string.broadcast_toggle_show))
            }
            if (showAdvanced) {
                SectionLabel(stringResource(R.string.section_broadcast))
                OutlinedTextField(
                    value = broadcastConnect, onValueChange = { broadcastConnect = it },
                    label = { Text(stringResource(R.string.broadcast_connect_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = broadcastDisconnect, onValueChange = { broadcastDisconnect = it },
                    label = { Text(stringResource(R.string.broadcast_disconnect_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = broadcastToggle, onValueChange = { broadcastToggle = it },
                    label = { Text(stringResource(R.string.broadcast_toggle_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.broadcast_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                stringResource(R.string.custom_vpn_footer_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
            Button(
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = MockInk, contentColor = Color.White),
                modifier = Modifier.weight(1f),
                onClick = {
                    onSave(
                        CustomVpnTarget(
                            label = label,
                            packageName = packageName,
                            scheme = scheme.ifBlank { null },
                            connectPath = connectPath.ifBlank { "connect" },
                            disconnectPath = disconnectPath.ifBlank { "disconnect" },
                            togglePath = togglePath.ifBlank { "toggle" },
                            broadcastConnectAction = broadcastConnect.ifBlank { null },
                            broadcastDisconnectAction = broadcastDisconnect.ifBlank { null },
                            broadcastToggleAction = broadcastToggle.ifBlank { null },
                        )
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
