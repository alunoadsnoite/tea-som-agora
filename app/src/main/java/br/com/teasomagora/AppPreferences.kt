package br.com.teasomagora

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

internal enum class ThemeChoice(@param:StringRes val label: Int) {
    LIGHT(R.string.light), DARK(R.string.dark), SYSTEM(R.string.automatic),
}

internal enum class AppLanguage(val tag: String, val label: String) {
    PORTUGUESE("pt-BR", "Português (Brasil)"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
}

internal class AppPreferences(context: Context) {
    private val storage = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    var theme: ThemeChoice
        get() = ThemeChoice.entries.firstOrNull { it.name == storage.getString("theme", null) }
            ?: ThemeChoice.SYSTEM
        set(value) { storage.edit().putString("theme", value.name).apply() }

    var language: AppLanguage
        get() = AppLanguage.entries.firstOrNull { it.tag == storage.getString("language", null) }
            ?: AppLanguage.PORTUGUESE
        set(value) { storage.edit().putString("language", value.tag).apply() }

    fun localize(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language.tag))
        return context.createConfigurationContext(config)
    }
}
