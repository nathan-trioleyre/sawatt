package app.sawatt

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The language chosen in Me → Language, or null to follow the phone.
 *
 * From Android 13 the choice is the system's per-app language, so it stays in sync with
 * Settings → Apps → SaWatt → Language and the system recreates the screens. Older versions keep it in
 * preferences and apply it through [wrap].
 */
object AppLanguage {
    /** Languages the app is translated into, as language tags. */
    val supported = listOf("en", "fr")

    private const val PREFS = "language"
    private const val KEY_TAG = "tag"

    fun current(context: Context): String? {
        val language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.takeUnless { it.isEmpty }?.get(0)?.language
        } else {
            prefs(context).getString(KEY_TAG, null)
        }
        return language?.takeIf { it in supported }
    }

    fun set(activity: Activity, tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            prefs(activity).edit().putString(KEY_TAG, tag).apply()
            activity.recreate()
        }
    }

    /** Below Android 13, applies the chosen language to [base]; call it from `attachBaseContext`. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY_TAG, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        // Dates and numbers are formatted with the default locale.
        Locale.setDefault(locale)
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
