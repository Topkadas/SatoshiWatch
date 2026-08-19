package com.satoshiwatch.core.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Přepínání jazyka aplikace: „system“ (výchozí), čeština, angličtina, němčina.
 *
 * Volba je uložena v obyčejných SharedPreferences – kód jazyka není citlivý
 * údaj a musí být čitelný už v attachBaseContext, tedy před inicializací
 * Hiltu i šifrovaných úložišť.
 */
object AppLocale {

    const val SYSTEM = "system"
    val SUPPORTED = listOf(SYSTEM, "cs", "en", "de")

    private const val PREFS_FILE = "satoshiwatch_locale"
    private const val KEY_LANGUAGE = "language"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM

    @Suppress("ApplySharedPref")
    fun set(context: Context, language: String) {
        val value = if (language in SUPPORTED) language else SYSTEM
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, value)
            .commit() // synchronně – hned poté se aktivita recreatuje
    }

    /**
     * Obalí kontext zvoleným jazykem; u „system“ vrací kontext beze změny.
     * Používá se v aktivitě, službě, notifikacích i widgetu, aby všechny
     * texty (včetně těch mimo UI) respektovaly zvolený jazyk.
     */
    fun wrap(base: Context): Context {
        val language = get(base)
        if (language == SYSTEM) return base
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
