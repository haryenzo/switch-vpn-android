package com.aeris.autovpn.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

// The actual selected locale is tracked in its own SharedPreferences store (see
// ui/Language.kt's storedLanguageCode/applyLanguage) so it can be read synchronously from
// Activity.attachBaseContext. All we need here is whether the user has ever gotten past the
// first-run language picker, since an empty stored language code is indistinguishable from
// "never asked" vs. "explicitly chose to keep the system default".
private val HAS_CHOSEN_LANGUAGE_KEY = booleanPreferencesKey("has_chosen_language")
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

// "system" (default), "light", or "dark" — see ui/Theme.kt's ThemeMode.
class AppSettings(private val context: Context) {
    val hasChosenLanguage: Flow<Boolean> =
        context.settingsDataStore.data.map { it[HAS_CHOSEN_LANGUAGE_KEY] ?: false }

    suspend fun setHasChosenLanguage(value: Boolean) {
        context.settingsDataStore.edit { it[HAS_CHOSEN_LANGUAGE_KEY] = value }
    }

    val themeMode: Flow<String> =
        context.settingsDataStore.data.map { it[THEME_MODE_KEY] ?: "system" }

    suspend fun setThemeMode(mode: String) {
        context.settingsDataStore.edit { it[THEME_MODE_KEY] = mode }
    }
}
