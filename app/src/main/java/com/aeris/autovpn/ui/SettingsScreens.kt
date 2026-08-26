package com.aeris.autovpn.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aeris.autovpn.R
import com.aeris.autovpn.data.AppSettings
import kotlinx.coroutines.launch

/**
 * The expand/collapse chip + dropdown list of languages (flag + native name), reused by both
 * the first-run full screen below and inline in Settings. Selecting a language only stages it
 * locally in [selected] — the caller decides when/whether to actually apply it.
 */
@Composable
private fun LanguageDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val language = remember(selected) { languageByCode(selected) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(language.flag, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Text(language.nativeName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
            ) {
                LazyColumn {
                    items(AVAILABLE_LANGUAGES, key = { it.code }) { lang ->
                        LanguageRow(
                            lang = lang,
                            selected = lang.code == selected,
                            onClick = { expanded = false; onSelect(lang.code) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * First-run screen: our logo, "choose your language" prompt, the dropdown above defaulting to
 * Russian, and a confirm button. Applying (and marking first-run done) is the caller's job,
 * since applying immediately would recreate the Activity mid-picker.
 */
@Composable
fun LanguagePickerScreen(initialCode: String, onConfirm: (String) -> Unit) {
    var selected by remember { mutableStateOf(initialCode) }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.choose_language_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        LanguageDropdown(selected = selected, onSelect = { selected = it })

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { onConfirm(selected) },
            colors = ButtonDefaults.buttonColors(containerColor = MockInk, contentColor = androidx.compose.ui.graphics.Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.choose_language_confirm)) }
    }
}

@Composable
private fun LanguageRow(lang: Language, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(lang.flag, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(12.dp))
        Text(
            lang.nativeName,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MockAccent2 else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Settings screen reached from the header's gear icon: language switcher and theme mode. */
@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.back))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(2.dp))
                    InfoTooltip(stringResource(R.string.info_language))
                }
                LanguageDropdown(
                    selected = currentLanguageCode(context),
                    onSelect = { code -> applyLanguage(context, code) },
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(2.dp))
                    InfoTooltip(stringResource(R.string.info_theme))
                }
                val themeMode by settings.themeMode.collectAsState(initial = "system")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeModePill(
                        label = stringResource(R.string.theme_system),
                        selected = themeMode == "system",
                        modifier = Modifier.weight(1f),
                    ) { scope.launch { settings.setThemeMode("system") } }
                    ThemeModePill(
                        label = stringResource(R.string.theme_light),
                        selected = themeMode == "light",
                        modifier = Modifier.weight(1f),
                    ) { scope.launch { settings.setThemeMode("light") } }
                    ThemeModePill(
                        label = stringResource(R.string.theme_dark),
                        selected = themeMode == "dark",
                        modifier = Modifier.weight(1f),
                    ) { scope.launch { settings.setThemeMode("dark") } }
                }
            }

            var showBuyDialog by remember { mutableStateOf(false) }
            if (showBuyDialog) {
                ExternalLinkConfirmDialog(url = "https://t.me/aeris_vpnbot") { showBuyDialog = false }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.settings_buy_vpn_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { showBuyDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MockAccent, contentColor = androidx.compose.ui.graphics.Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_buy_vpn_button)) }
            }
        }
    }
}

@Composable
private fun ThemeModePill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MockAccentSoft else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = if (selected) MockAccent else androidx.compose.ui.graphics.Color.Transparent,
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
