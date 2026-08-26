package com.aeris.autovpn.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.util.Locale

data class Language(val code: String, val nativeName: String, val flag: String)

private const val PREFS_NAME = "locale_prefs"
private const val KEY_LANGUAGE = "language_code"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// Own SharedPreferences-backed locale storage instead of AppCompatDelegate's per-app-locale
// API: that compat shim is documented as reliable mainly for AppCompatActivity, and this app
// uses a plain ComponentActivity — a manual override read synchronously in
// MainActivity.attachBaseContext is more predictable across API 26+ than trusting the shim's
// below-API-33 behavior for a non-AppCompatActivity host.
fun storedLanguageCode(context: Context): String =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LANGUAGE, "") ?: ""

fun currentLanguageCode(context: Context): String {
    val stored = storedLanguageCode(context)
    return if (AVAILABLE_LANGUAGES.any { it.code == stored }) stored else "ru"
}

fun applyLanguage(context: Context, code: String) {
    // commit(), not apply(): must be durably written before recreate() tears down and rebuilds
    // the Activity, since attachBaseContext reads it back synchronously on the way up.
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_LANGUAGE, code).commit()
    Locale.setDefault(Locale(code))
    context.findActivity()?.recreate()
}

// Русский/English/Français/Հայերեն come first, followed by Қазақша/Azərbaycanca/Türkçe/
// ქართული — explicitly requested and promoted above the rest of the list. Abkhaz was removed:
// it has no official Unicode flag (partially-recognized state, no ISO 3166 code), and I don't
// have reliable enough Abkhaz to ship a real translation for it either. Only ru (base), en, fr
// and hy currently ship full translations (see res/values*/strings.xml) — picking anything
// else falls back to the Russian base strings until that language gets translated too.
val AVAILABLE_LANGUAGES = listOf(
    Language("ru", "Русский", "🇷🇺"),
    Language("en", "English", "🇬🇧"),
    Language("fr", "Français", "🇫🇷"),
    Language("hy", "Հայերեն", "🇦🇲"),
    Language("kk", "Қазақша", "🇰🇿"),
    Language("az", "Azərbaycanca", "🇦🇿"),
    Language("tr", "Türkçe", "🇹🇷"),
    Language("ka", "ქართული", "🇬🇪"),
    Language("es", "Español", "🇪🇸"),
    Language("de", "Deutsch", "🇩🇪"),
    Language("it", "Italiano", "🇮🇹"),
    Language("pt", "Português", "🇵🇹"),
    Language("ar", "العربية", "🇸🇦"),
    Language("zh", "中文", "🇨🇳"),
    Language("uk", "Українська", "🇺🇦"),
    Language("pl", "Polski", "🇵🇱"),
    Language("nl", "Nederlands", "🇳🇱"),
    Language("sv", "Svenska", "🇸🇪"),
    Language("fi", "Suomi", "🇫🇮"),
    Language("no", "Norsk", "🇳🇴"),
    Language("da", "Dansk", "🇩🇰"),
    Language("cs", "Čeština", "🇨🇿"),
    Language("ro", "Română", "🇷🇴"),
    Language("hu", "Magyar", "🇭🇺"),
    Language("el", "Ελληνικά", "🇬🇷"),
    Language("he", "עברית", "🇮🇱"),
    Language("hi", "हिन्दी", "🇮🇳"),
    Language("ja", "日本語", "🇯🇵"),
    Language("ko", "한국어", "🇰🇷"),
    Language("vi", "Tiếng Việt", "🇻🇳"),
    Language("th", "ไทย", "🇹🇭"),
    Language("id", "Bahasa Indonesia", "🇮🇩"),
    Language("ms", "Bahasa Melayu", "🇲🇾"),
    Language("fa", "فارسی", "🇮🇷"),
    Language("ur", "اردو", "🇵🇰"),
    Language("bn", "বাংলা", "🇧🇩"),
    Language("uz", "Oʻzbekcha", "🇺🇿"),
    Language("be", "Беларуская", "🇧🇾"),
    Language("bg", "Български", "🇧🇬"),
    Language("sr", "Српски", "🇷🇸"),
    Language("hr", "Hrvatski", "🇭🇷"),
    Language("lt", "Lietuvių", "🇱🇹"),
    Language("lv", "Latviešu", "🇱🇻"),
    Language("et", "Eesti", "🇪🇪"),
    Language("sk", "Slovenčina", "🇸🇰"),
    Language("sl", "Slovenščina", "🇸🇮"),
)

fun languageByCode(code: String): Language =
    AVAILABLE_LANGUAGES.find { it.code == code } ?: AVAILABLE_LANGUAGES.first()
